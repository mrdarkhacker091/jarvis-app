package com.jarvis.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * On-screen interaction after the user explicitly enables accessibility. All
 * actions target nodes by label/content description/resource id; coordinate
 * gestures are not used. Secure windows, password fields and OTPs are never
 * read or bypassed.
 */
class JarvisAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }

    fun performNamedAction(action: String): String {
        val mapped = when (action.lowercase().trim()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent apps" -> GLOBAL_ACTION_RECENTS
            "notifications", "shade" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "quick settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "I do not know the action $action."
        }
        val ok = performGlobalAction(mapped)
        return if (ok) "Done: $action." else "The system refused $action."
    }

    fun click(label: String): String {
        val root = rootInActiveWindow ?: return "I cannot see the screen."
        val node = findNode(root, label.lowercase(), 0) ?: return "I could not find $label on screen."
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        return if (clicked) "Tapped $label." else "Found $label but could not tap it."
    }

    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "I cannot see the screen."
        val field = findEditable(root, 0) ?: return "I do not see a text field."
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "Typed it." else "I could not type into that field."
    }

    fun scroll(direction: String): String {
        val root = rootInActiveWindow ?: return "I cannot see the screen."
        val scrollable = findScrollable(root, 0) ?: return "Nothing to scroll here."
        val action = when (direction.lowercase()) {
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return "Scroll which way?"
        }
        return if (scrollable.performAction(action)) "Scrolled $direction." else "Could not scroll."
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "I cannot see the screen."
        val texts = mutableListOf<String>()
        collectText(root, texts, 0)
        return if (texts.isEmpty()) "There is no readable text on screen."
        else texts.distinct().take(40).joinToString(". ")
    }

    private fun findNode(node: AccessibilityNodeInfo?, wanted: String, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 24) return null
        val text = node.text?.toString()?.lowercase().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
        if (text == wanted || desc == wanted || text.contains(wanted) || desc.contains(wanted)) return node
        for (i in 0 until node.childCount) {
            val f = findNode(node.getChild(i), wanted, depth + 1)
            if (f != null) return f
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 24) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) { findEditable(node.getChild(i), depth + 1)?.let { return it } }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 24) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) { findScrollable(node.getChild(i), depth + 1)?.let { return it } }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo?, into: MutableList<String>, depth: Int) {
        if (node == null || depth > 30) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrBlank() && !node.isPassword) into.add(t)
        for (i in 0 until node.childCount) collectText(node.getChild(i), into, depth + 1)
    }

    companion object { @Volatile var instance: JarvisAccessibilityService? = null }
}