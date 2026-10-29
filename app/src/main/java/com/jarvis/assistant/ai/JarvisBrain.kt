package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.JarvisBus
import com.jarvis.assistant.data.Memory
import com.jarvis.assistant.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JarvisBrain {

    private const val MAX_ROUNDS = 4
    private const val PENDING_SP = "jarvis_pending_reply"
    private const val PENDING_KEY = "pending_reply_json"

    fun respond(ctx: Context, userText: String): String {
        val prefs = Prefs.get(ctx)
        if (prefs.apiKey.isBlank() || prefs.baseUrl.isBlank()) {
            return "I do not have a model key yet. Open settings and paste one in."
        }

        // Pending reply confirmation: the user was asked "Send X to Y?"
        // and this turn answers it. Handle locally, no model call needed.
        val pending = loadPending(ctx)
        if (pending != null) {
            val verdict = confirmationVerdict(userText)
            if (verdict == null) {
                return "Should I send it to ${pending.sender}? Say yes to send or no to cancel."
            }
            clearPending(ctx)
            if (verdict) {
                val result = ToolRegistry.execute(
                    ctx,
                    "reply_to_message",
                    JSONObject().put("sender", pending.sender).put("text", pending.text)
                )
                return if (result.startsWith("Reply sent")) {
                    "Sent to ${pending.sender}."
                } else {
                    result
                }
            } else {
                return "Okay, I did not send anything."
            }
        }

        // Wake commands are answered locally with the real briefing,
        // never invented by the model.
        if (isWakeCommand(userText)) {
            val briefing = try {
                Briefing.buildWakeBriefing(ctx)
            } catch (e: Exception) {
                "Hello. I could not read your notifications just now. Tell me if you need something else."
            }
            JarvisBus.markBriefingGiven()
            return briefing
        }

        val client = LlmClient(prefs.baseUrl, prefs.apiKey, prefs.model)

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt(ctx)))

        JarvisBus.turns.value.takeLast(12).forEach { t ->
            val role = if (t.from == "user") "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", t.text))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        val tools = ToolRegistry.schema()

        repeat(MAX_ROUNDS) {
            val reply = try {
                client.complete(messages, tools)
            } catch (e: Exception) {
                return "I could not reach my brain just now. " +
                    (e.message?.take(120) ?: "Connection problem.")
            }

            if (reply.toolCalls.isEmpty()) {
                if (JarvisBus.briefingGivenRecently()) {
                    return reply.text?.trim().orEmpty().ifBlank { "Done." }
                }
                return reply.text?.trim().orEmpty().ifBlank { "I did not get a usable answer." }
            }

            val assistant = JSONObject()
                .put("role", "assistant")
                .put("content", reply.text ?: "")

            val calls = JSONArray()
            for (call in reply.toolCalls) {
                calls.put(
                    JSONObject()
                        .put("id", call.id)
                        .put("type", "function")
                        .put("function", JSONObject()
                            .put("name", call.name)
                            .put("arguments", call.args.toString()))
                )
            }
            assistant.put("tool_calls", calls)
            messages.put(assistant)

            var intercepted: String? = null
            for (call in reply.toolCalls) {
                if (call.name == "reply_to_message" && prefs.replyConfirmDefault) {
                    val sender = call.args.optString("sender", "").trim()
                    val text = call.args.optString("text", "").trim()
                    if (text.isBlank()) {
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", call.id)
                                .put("content", "I need the reply text before I can ask for confirmation.")
                        )
                    } else {
                        val who = sender.ifBlank { "them" }
                        savePending(ctx, PendingReply(sender = who, text = text))
                        intercepted = "Send \"$text\" to $who?"
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", call.id)
                                .put("content", "Awaiting user confirmation; ask: $intercepted")
                        )
                    }
                } else {
                    val result = ToolRegistry.execute(ctx, call.name, call.args)
                    messages.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.id)
                            .put("content", result)
                    )
                }
            }
            if (intercepted != null) {
                return intercepted
            }
        }

        return "I went a few rounds without settling on an answer. Try asking more directly."
    }

    private data class PendingReply(val sender: String, val text: String)

    private fun loadPending(ctx: Context): PendingReply? {
        return try {
            val raw = ctx.applicationContext
                .getSharedPreferences(PENDING_SP, Context.MODE_PRIVATE)
                .getString(PENDING_KEY, null) ?: return null
            val o = JSONObject(raw)
            PendingReply(o.optString("sender", ""), o.optString("text", ""))
        } catch (e: Exception) {
            null
        }
    }

    private fun savePending(ctx: Context, pending: PendingReply) {
        try {
            ctx.applicationContext
                .getSharedPreferences(PENDING_SP, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    PENDING_KEY,
                    JSONObject().put("sender", pending.sender).put("text", pending.text).toString()
                )
                .apply()
        } catch (e: Exception) {
            // Best effort: confirmation simply will not persist.
        }
    }

    private fun clearPending(ctx: Context) {
        try {
            ctx.applicationContext
                .getSharedPreferences(PENDING_SP, Context.MODE_PRIVATE)
                .edit().remove(PENDING_KEY).apply()
        } catch (e: Exception) {
            // Best effort.
        }
    }

    private fun confirmationVerdict(text: String): Boolean? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) {
            return null
        }
        val yes = listOf("yes", "yeah", "yep", "sure", "send", "send it", "ok send", "do it", "confirm", "go ahead")
        val no = listOf("no", "nope", "cancel", "don't", "dont", "stop", "never mind", "nevermind", "abort")
        if (yes.any { w -> t == w || t.startsWith("$w ") || t.startsWith("$w,") || t.endsWith(" $w") }) {
            return true
        }
        if (no.any { w -> t == w || t.startsWith("$w ") || t.startsWith("$w,") || t.startsWith("$w,") || t.endsWith(" $w") || t.contains(w) }) {
            // "contains" for the no-list catches "don't send", "do not send", "never mind".
            return false
        }
        return null
    }

    private fun isWakeCommand(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isEmpty()) {
            return false
        }
        if (t == "hey jarvis" || t == "hey jarvis." || t == "jarvis" || t == "wake up" || t == "wake up jarvis") {
            return true
        }
        val misses = listOf(
            "what did i miss",
            "what have i missed",
            "what's happening",
            "whats happening",
            "what is happening",
            "what did i miss?",
            "catch me up",
            "any messages",
            "read my notifications",
            "what are my notifications"
        )
        if (misses.any { m -> t.contains(m) }) {
            return true
        }
        val whatsappAsk = t.contains("whatsapp") && (t.contains("message") || t.contains("notif") || t.contains("read") || t.contains("miss"))
        val telegramAsk = t.contains("telegram") && (t.contains("message") || t.contains("notif") || t.contains("read") || t.contains("miss"))
        val smsAsk = (t.contains("sms") || t.contains("text message")) && (t.contains("message") || t.contains("notif") || t.contains("read") || t.contains("miss"))
        if (whatsappAsk || telegramAsk || smsAsk) {
            return true
        }
        if ((t.contains("how many") && t.contains("message")) ||
            (t.contains("waiting") && (t.contains("message") || t.contains("for me")))) {
            return true
        }
        return false
    }

    private fun systemPrompt(ctx: Context): String {
        val prefs = Prefs.get(ctx)
        val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        return """
You are JARVIS, a voice assistant running on the user's Android phone.

Current time: $now
Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}
Notification announcements: ${if (prefs.announceNotifications) "on" else "off"}
Quiet hours: ${prefs.quietStart}:00 to ${prefs.quietEnd}:00
Briefing detail: ${prefs.briefingDetail}

Facts the user has approved you remembering:
${Memory.toPromptString(ctx)}

How to behave:
- Speak, do not write. Answers are read aloud, so keep them to one or two sentences unless asked to elaborate.
- No markdown, no bullet lists, no asterisks, no emoji. Plain spoken English.
- If the user asks you to do something and a tool can do it, call the tool. Do not claim an action you did not call a tool for.
- If a tool fails or a permission is missing, say exactly that in one sentence and tell the user what to grant.
- You can only read notification previews, not full chat history. Never pretend otherwise.
- Never invent device state, message counts, sender names, or battery level. Read them with a tool or say you do not know.
- Briefing rules: a wake greeting is answered with the local briefing only. Never repeat the full briefing on ordinary commands; give one short acknowledgement instead.
- Sender names: notification titles may carry emoji or decoration. Speak the normalized name (letters and digits, no emoji narration). Never read emoji aloud one by one.
- A sender name is not a relationship. Do not call anyone a girlfriend, boyfriend, wife, husband, or family unless the user explicitly configured that relationship as a stored fact.
- Reply confirmation: sending a message always needs explicit confirmation first. Propose the exact text and recipient with "Send X to Y?" and only send after the user says yes. Never send to a guessed recipient.
- If a request is ambiguous and acting would be destructive, ask one short clarifying question instead of guessing.
- Never invent device state. Read it with a tool or say you do not know.
""".trimIndent()
    }
}
