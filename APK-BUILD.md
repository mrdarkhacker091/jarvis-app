JARVIS AI COMPANION — GETTING A REAL APK
========================================

Honest starting point: a web page cannot compile an APK. An APK is produced by
Android's build tools (JDK + Gradle + the Android SDK), which never run inside a
browser. The Jarvis web console therefore gives you two real routes to an
installed app, and the Vault stores the finished file so it can be installed from
inside the console.

ROUTE 1 — BUILD IN THE CLOUD (no toolchain on your machine)
-----------------------------------------------------------
1. Download the project archive from the Vault and push it to a GitHub
   repository. The archive already contains the file
   .github/workflows/build-apk.yml, so GitHub Actions picks it up straight away.
   If you only have the android/ folder, copy android/build-apk-workflow.yml to
   .github/workflows/build-apk.yml in your repository — GitHub only runs
   workflows from that exact path.
2. Open the repository's Actions tab. "Build Jarvis Android APK" runs on every
   push to main, or press "Run workflow" to start it by hand.
3. When the run finishes, open it and download the artifact
   "jarvis-ai-debug-apk". Inside is app-debug.apk.
4. Upload that file in the Jarvis console: Vault -> Android APK -> UPLOAD THE
   .APK FILE. It is stored privately and offered as a one-hour signed install
   link.

ROUTE 2 — BUILD LOCALLY
-----------------------
Requirements: JDK 17 and either Android Studio or a standalone Gradle install.

  cd android
  gradle assembleDebug          # or: ./gradlew assembleDebug  (with a wrapper)
  # result: app/build/outputs/apk/debug/app-debug.apk

Android Studio route: open the android/ folder, wait for the Gradle sync, then
Build -> Build Bundle(s) / APK(s) -> Build APK(s).

INSTALL ON THE PHONE
--------------------
1. Copy the APK to the phone (or open the signed link from the Vault on the
   phone).
2. Allow "install unknown apps" for the browser or file manager you use.
3. Install, open Jarvis AI, then open SETTINGS and paste an app API key from the
   console (API page). The key is kept in EncryptedSharedPreferences.

OPTIONAL — MAKE IT THE DEFAULT ASSISTANT
----------------------------------------
Settings -> Apps -> Default apps -> Digital assistant -> Jarvis AI. Whether this
is offered depends on the Android version, the manufacturer (Infinix/Transsion
builds vary) and system policy. Installing the APK alone never replaces Google
Assistant.

DEBUG VS RELEASE
----------------
The workflow builds a DEBUG APK: installable on your own phones, no Play Store
submission, not signed for distribution. For a release build you need your own
keystore and signing config — add them locally and never commit the keystore or
its passwords.

WHAT THE COMPANION DOES NOT DO (stated honestly in the app too)
--------------------------------------------------------------
- Always-on "Hey Jarvis" wake word: voice input is push-to-talk, because Android
  background microphone policy makes always-on listening unreliable.
- Antivirus-style scanning: a normal Android app cannot read other apps' private
  data, so the scan reviews permissions and patch level and says so.
- Toggling Wi-Fi, mobile data or GPS: Android forbids it for third-party apps, so
  the app opens the relevant settings screen instead of pretending.