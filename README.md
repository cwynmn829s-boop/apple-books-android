# 图书 · Android

A native Jetpack Compose reader inspired by the Apple Books reading experience. Import EPUB and TXT files using Android's Storage Access Framework; books are copied into app-private storage and metadata, parsed chapters, and reading progress are stored in Room.

## Build

Requires JDK 17. Run `./gradlew assembleDebug`; install `app/build/outputs/apk/debug/app-debug.apk` on an Android device. The GitHub Actions workflow builds and publishes the debug APK as release `v0.1.0-build1`.

Supports EPUB OPF spine/XHTML text extraction and TXT UTF-8/GBK decoding with Chinese/English chapter heading recognition. This is an independent Android reader inspired by Apple Books, not Apple's application.
