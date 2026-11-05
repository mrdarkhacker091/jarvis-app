# JARVIS Android — build instructions

This is the native `com.jarvis.assistant` project. A browser cannot compile an
APK; build it with Gradle on a machine that has Android SDK + JDK 17.

```
cd android
gradle wrapper --gradle-version 8.7      # first time only
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Or trigger the GitHub Action `.github/workflows/build-apk.yml` (built from
`android/build-apk-workflow.yml`).

## First-run setup on the phone
1. Open JARVIS, go to Settings → Permissions, and grant each item (the buttons
   open the right Android screen).
2. Enable Notification access for JARVIS (for the wake briefing to read messages).
3. Enable Accessibility for JARVIS (for on-screen click/type/scroll actions).
4. In Settings → AI Providers, enter your endpoint, model and key for each
   provider you want. Agnes is the default but needs a valid endpoint/key.
5. Set your wake phrase and turn on Wake listening if you want hands-free.
6. Tap the J to talk; say the wake phrase to wake hands-free.

## Honest limitations
- Android does not allow a third-party app to be a true low-power always-on
  hotword engine. Wake listening uses a foreground service with the microphone
  service type and a visible notification; it is as reliable as the on-device
  SpeechRecognizer allows, which can stop, time out or be restricted in the
  background. Keep the screen/foreground fallback in mind.
- A normal app cannot read other apps' private chat databases, control
  Bluetooth/hotspot/mobile-data/airplane-mode directly, or act as a real
  antivirus. The app uses official APIs and opens the relevant settings screen
  when a toggle is not freely writable.