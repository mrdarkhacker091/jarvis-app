# Jarvis AI — Android companion

Native Kotlin/Android companion for the Jarvis Wandering Nexus Core web app
(`https://jarvis-wandering-nexus-core.base44.app`). It is a real Android app,
not a WebView wrapper: chat talks to the same secure backend functions the web
app uses, speech goes through the Android speech APIs, and every device action
runs through an official Android API with its real result reported back.

## What it does

- **Chat** with the Jarvis backend (`jarvisChat`) — the same model, memory and
  research pipeline as the web app.
- **Voice in / voice out** through `SpeechRecognizer` and `TextToSpeech`
  (push to talk: the microphone opens only while you hold the button).
- **Device information** — manufacturer, model, Android version, SDK, security
  patch level, battery, charging state, storage, network type, permission state.
  Anything the platform does not expose is printed as `not reported`.
- **Supported actions**, each reporting the true outcome:
  open an installed app by name, open system settings, Wi-Fi settings, camera,
  flashlight on/off, screen brightness (needs "Modify system settings"),
  a clock timer, a location fix, an action log, and a scoped security review.
- **Digital assistant** — the app declares `ACTION_ASSIST` and a
  `VoiceInteractionService`, so Android can offer Jarvis as the default
  assistant. You must select it yourself; see below.

## Not implemented (deliberately honest)

- **Background wake word ("Hey Jarvis")**. Always-on microphone capture is
  restricted by Android background-execution and microphone rules and drains
  battery; a reliable implementation needs a foreground service with a visible
  notification plus an on-device keyword engine. Not shipped here, so Jarvis
  never claims to be listening in the background.
- **Antivirus-style scanning.** A normal Android app cannot read other apps'
  private data. The scan reviews package permissions, the security patch level
  and risky permission combinations, and states its own limitations in the report.
- **Toggling Wi-Fi / mobile data / GPS.** Android does not permit these for
  third-party apps, so the app opens the settings screen instead of faking it.

## Build the APK

You need Android Studio (or the Android SDK + JDK 17).

```bash
cd jarvis-ai-android
./gradlew assembleDebug          # build/app/outputs/flutter-apk… see below
./gradlew assembleRelease        # unsigned release build
```

Gradle wrapper: if `./gradlew` is missing, run `gradle wrapper` once, or open
the folder in Android Studio and let it generate the wrapper.

Debug APK path: `app/build/outputs/apk/debug/app-debug.apk`
Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
or copy it to the phone and open it (allow "install unknown apps" for your
file manager).

### Sign a release build

```bash
keytool -genkey -v -keystore jarvis.jks -keyalg RSA -keysize 2048 -validity 10000 -alias jarvis
```

Add to `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../jarvis.jks")
            storePassword = System.getenv("JARVIS_STORE_PASSWORD")
            keyAlias = "jarvis"
            keyPassword = System.getenv("JARVIS_KEY_PASSWORD")
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
}
```

Never commit the keystore or its passwords.

### Build in CI

`build-apk-workflow.yml` is ready to use: copy it to
`.github/workflows/build-apk.yml` in your repository and push — GitHub Actions
builds a debug APK and uploads it as a downloadable artifact.

## Connect it to the backend

1. Open the Jarvis web app dashboard → **API** and create an API key for the app.
2. In the Android app open the **SETTINGS** tab, paste the key and press
   **SAVE KEY**. It is stored with `EncryptedSharedPreferences`.
3. Send a message. A 401 means the key is missing, expired or lacks access —
   the app says so plainly instead of inventing an answer.

## Default assistant (optional)

After installing, open
**Settings → Apps → Default apps → Digital assistant** and choose **Jarvis AI**,
then allow it to use your microphone. This replaces Google Assistant only when
the user selects it — installing the APK alone never does. Availability varies
by Android version, manufacturer (including Infinix/Transsion builds) and
system policy, and some devices do not expose the option at all.

## Permissions and why

| Permission | Why |
|---|---|
| `INTERNET` | Talk to the Jarvis backend |
| `RECORD_AUDIO` | Push-to-talk voice input |
| `ACCESS_FINE_LOCATION` | Only when you ask for your location |
| `CAMERA` / `FLASHLIGHT` | Torch control and launching the camera |
| `QUERY_ALL_PACKAGES` | Resolve an app name you asked to open |
| `POST_NOTIFICATIONS` | Status notifications |

Location is foreground-only. Nothing is uploaded anywhere unless you ask for it,
and device details are sent to the assistant only when you press
**SEND TO JARVIS**.

## Project layout

```
android/
  app/src/main/java/ai/jarvis/nexus/
    MainActivity.kt            Compose UI: chat, device, actions, settings
    JarvisApi.kt               Backend calls (jarvisChat, jarvisSpeak)
    DeviceTools.kt             Official Android API actions + scoped security review
    VoiceIo.kt                 SpeechRecognizer + TextToSpeech
    JarvisAssistantService.kt  VoiceInteractionService / session for the assist role
    AndroidManifest.xml        Permissions, assist intent, assistant services
``