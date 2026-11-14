# Sniply

A server-free Android video downloader targeting public links from YouTube, Instagram, Facebook, X, Pinterest, Reddit and TikTok. Built with native Java and an embedded Python/yt-dlp extraction engine. Android 10+, ARM64 phones and x86-64 emulators.

## Get an APK

Open **Actions → Build Sniply APK → latest successful run → Artifacts → Sniply-debug-…**. Download and unzip the artifact, then install the APK on your Android phone. Android requires approval to install apps from your browser or file manager. These are development builds, not signed production releases.

Pushes to main, manual runs, and a weekly schedule build with the latest available yt-dlp version. Each build runs Android lint. This delivers engine fixes once installed; it cannot guarantee every platform or URL works. Scheduled builds produce artifacts, not silently installed updates.

## Use

Open Sniply and paste a link, or share text from another app to Sniply. When the app has focus and its input is empty, it checks the clipboard for an HTTPS link. Android may show a clipboard notice; no continuous background clipboard monitoring is used. Choose a quality cap and tap Save video. A foreground service handles one download at a time and saves it under **Movies/Sniply**, visible in the gallery. Download progress appears in the app; a notification keeps the service visible.

## Current limitations

- Platform support comes from yt-dlp and is not yet verified against live URLs on all seven platforms. Some require login, anti-bot challenges, or other unsupported access.
- Only combined audio-and-video formats are selected. Separate tracks need an FFmpeg integration, so some YouTube videos, high resolutions and Reddit videos are unavailable. The app reports errors rather than saving silent video.
- Posts containing several videos download only the first item. Playlists, private content, DRM, account login and cookies are outside this version.
- 1 GiB download limit where the platform reports size; quality choices are caps, not guarantees.
- No cancellation/resume interface yet. Android can still stop long-running work.
- Debug APKs built on different clean GitHub runners may have different signing keys. Uninstall an older debug build before installing a newer one if Android reports a signature conflict. Uninstalling clears app settings; gallery videos remain.

## Updates and production signing

The app checks the official GitHub Releases API on launch and on demand, and opens the release page when a newer build is available. It never downloads or executes replacement engine code. GitHub receives the update request; video URLs are sent to their source platforms during extraction, not to a Sniply server.

Production releases need a persistent **private Android signing key** so updates can install over the existing app. That key must be stored in GitHub Actions secrets, never committed. The **Publish signed update** workflow runs weekly or manually. It skips publishing until signing secrets are configured; development APKs are available through Actions. Set `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` in repository Actions secrets. Keep a secure offline backup of this key. Once configured, the workflow builds, lints and publishes signed updates using the same key. Release codes start at 100001; debug builds use a separate lower sequence. Production tags follow `v0.1.BUILD_NUMBER`, with monotonically increasing Android version codes. Android asks the user to approve APK installation.

## Build locally

Install JDK 17, Python 3.11, Android SDK 35 and Gradle 8.9, then run:

```sh
gradle assembleDebug lintDebug
```

The repository intentionally uses the pinned Gradle version in CI rather than a downloaded wrapper binary. Chaquopy 16.1 embeds Python 3.11; yt-dlp is resolved at build time for current extractor updates. Production builds should record and pin tested dependency versions.

Download only content you own or have permission to save and respect platform terms. This app is not affiliated with the supported platforms.
