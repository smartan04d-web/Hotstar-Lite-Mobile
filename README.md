# Hotstar Lite Mobile (unofficial)

Android 10+ experimental JioHotstar mobile WebView wrapper. Separate app from Hotstar Lite TV. Not affiliated with JioHotstar.

Features: mobile horizontally scrollable Home / Back / Reload / Safe Filter ON-OFF / Auto Skip ON-OFF toolbar; full-screen video callback; saved preferences; no background services; GitHub Actions APK build. Both ad controls OFF by default.

Limitations: JioHotstar DRM, login, video playback and ad skipping may not work in Android WebView. The optional filter blocks only selected third-party ad hosts, not guaranteed video ads. Auto Skip only attempts to click a visible enabled Skip Ad button in the main web page; it cannot bypass timers, embedded cross-origin players or protected streams. If video breaks, turn Safe Filter OFF and reload.

Build: create a new GitHub repository `Hotstar-Lite-Mobile`; upload the extracted files including hidden `.github/workflows/build-apk.yml`; commit to main; open Actions and download the successful `Hotstar-Lite-Mobile-APK` artifact, unzip it and install `app-debug.apk` on Android 10+. Local build requires JDK 17, Gradle 8.9 and Android SDK 35: `gradle assembleDebug`.

Source package only; not compiled or device-tested.
