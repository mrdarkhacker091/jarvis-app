# JARVIS — Android voice assistant

Kotlin + Jetpack Compose. No backend. LLM runs on your configured endpoint.

## Features

- Wake phrase ("jarvis" by default, customizable) with hands-free listening service
- Wake briefing: greeting, notification counts grouped by app/sender, battery,
  connectivity, pending 1-hour reminders, permission problems. Ends with
  "Tell me if you need something else."
- Silence by default: incoming notifications never trigger speech on their own
- Sender-name normalization for speech (decorative emoji stripped, original kept)
- Inline replies via the notification's own reply action, with confirmation first
- One-hour gentle reminders for conversations that look unanswered
- Futuristic dashboard launch screen: animated AI core, real battery/storage/
  network telemetry, permissions checklist, recent activity, bottom nav
  (Home / Activity / Settings)
- Speech in / speech out using Android TTS and SpeechRecognizer
- Tool calling: alarms, timers, torch, volume, brightness, battery, app launching,
  notification reading, web search, quick settings toggle
- Remembers user facts via tool calls
- Settings screen for model endpoint, API key, briefing detail, permissions

## Install

1. Download `app-debug.apk` from the latest release
2. Allow "Install unknown apps" for your browser/downloader
3. Install and open
4. Grant permissions in order: mic → notifications → accessibility → write settings → battery exemption
5. Paste your API key in Settings, pick a model preset, save
6. Optionally enable "Hands-free listening" and pick a briefing detail level

## Model endpoints

Any OpenAI-compatible endpoint works. Presets include:
- OpenRouter: https://openrouter.ai/api/v1
- Gemini AI: https://generativelanguage.googleapis.com/v1beta/openai
- Groq: https://api.groq.com/openai/v1

## Quiet hours & announcements

- Notification announcements on arrival are off by default.
- Use quiet hours + the briefing detail setting to control night behavior.
- Only an intentional wake (or an explicit "what did I miss?") speaks a summary.

## What this does NOT do

- Full WhatsApp/Telegram chat history (Android doesn't expose this)
- Bluetooth/hotspot toggle through public APIs (uses accessibility service to tap tiles instead)
- Play Store distribution (requires different review process)
- Guaranteed always-on wake word: Android has no public always-on third-party
  wake-word API. The current engine listens with SpeechRecognizer behind a
  `WakeEngine` interface (see `voice/WakeEngine.kt`), so it can stop, time out,
  need network, or be killed in the background by Doze/OEM policies. A future
  on-device keyword model (e.g. Porcupine / Vosk) can implement that interface
  without touching callers. Wake reliability limits are honest, not promised.
- Exact unread/reply detection: a disappearing notification is never treated as
  proof of a reply. Reminders say "you may still have an unanswered message" and
  the store tracks only replies JARVIS itself sends plus explicit marks.
- No relationship inference: sender names are never turned into girlfriend/boyfriend/
  family labels unless you store that fact yourself.

## Build locally

```bash
./gradlew assembleDebug
```

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).
