# Cyron Manager

A native Android download manager built with Kotlin, Jetpack Compose, Room, DataStore, OkHttp and coroutines. Downloads use real HTTP(S) requests; this is not a mock or a WebView wrapper. See [CHANGELOG.md](CHANGELOG.md) for the current release notes.

## Highlights

- Native dashboard, download library, search and filters, task details and settings.
- HTTP(S) probing, redirects, verified byte-range transfers, configurable connection count with no app-imposed speed cap, pause/cancel, and checksum verification.
- YouTube and general media-page inspection through the bundled yt-dlp Python module, with format selection, local FFmpeg merging and optional MP3 conversion.
- Magnet and local `.torrent` downloads through a local libtorrent session; completed files are exported into the user-selected SAF folder.
- Resume only when the remote representation can be validated with an ETag or Last-Modified value. Each ranged response is checked before private segments are merged. If those safety checks fail, Cyron declines unsafe resume/parallel merging.
- Foreground `dataSync` service, progress notification, wake lock while actively transferring, and local Room history.
- Interrupted transfers retry with capped exponential backoff and wait for a network instead of failing a whole file on a Wi-Fi/mobile handover. HTTP, media and torrent transfers have no user-configurable speed cap; actual throughput depends on the source, network, device and selected connection count.
- Media titles and format lists come from real yt-dlp metadata, so saved files use the page title and a mapped MIME type. Video-only and audio-only streams are downloaded separately and merged on-device with FFmpegKit because the free yt-dlp build has no ffmpeg executable.
- Destination-folder access through Android's Storage Access Framework (SAF); partial files are kept in app-private storage until verified.
- Persian UI with the Vazirmatn typeface (SIL OFL 1.1) and natural, product-focused copy. Settings are stored locally with DataStore. No accounts, server, analytics, or cloud services are required.
- Minimum Android API 26 (Android 8). Target API 35; compile SDK 36. Native builds currently include `arm64-v8a` and `x86_64` only.

## Build and test

Requirements: JDK 17, Node.js 20 or newer for the public-source check, Android SDK Platform 36 and Android Build Tools, and access to Google Maven and Maven Central. Use the checked-in Gradle wrapper (9.6): `./gradlew`. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` and accept the Android SDK licences first.

```shell
node scripts/check-public.mjs
sh ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Install a local debug build on a connected device:

```shell
./gradlew installDebug
```

To run the Room instrumentation test on a connected emulator or device:

```shell
./gradlew connectedDebugAndroidTest
```

The build splits by ABI: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (phones), `app-x86_64-debug.apk` (emulators) and `app-universal-debug.apk` (both, for convenience). Do not distribute a debug APK as a release. `app/build.gradle.kts` enables R8, resource shrinking and ABI splits. Release signing uses either the git-ignored `keystore.properties` file or the `CYRON_KEYSTORE_*` environment variables described in [the release guide](docs/RELEASE.md). Incomplete signing configuration fails the build; without any signing configuration, release APKs remain unsigned.

## Downloading

1. Launch Cyron Manager and allow notifications if desired.
2. Choose **Settings → Change** to grant the app durable access to a writable folder using Android's system folder picker.
3. Tap **Add**, enter a public HTTP(S) URL, and inspect it. Cyron needs a known positive `Content-Length` and verifies the server's byte-range response before opening independent connections.
4. Optionally enter the file's expected 64-digit SHA-256 digest. Select a filename and connection count, then start the transfer.
5. Pause, resume, cancel, or delete transfers from their actions menu or the foreground notification. Open and share only completed files. Cancelling from the notification asks for a second tap before partial data is removed.

Servers that require a login flow, return unknown-length streams, forbid the required requests, or change the file without stable validators may not be suitable. A multi-connection speed-up depends on the origin server and network; some servers do not support byte ranges. Cancellation deletes HTTP partial data; pausing/recovery keeps private segments which are revalidated against HTTP validators on resume. Media downloads rely on yt-dlp's continuation. The direct-media pause/resume/delete flow has been exercised on an emulator with a local media server; live-site YouTube compatibility still needs broader device testing. Torrent data is kept in app-private storage during transfer and exported only after completion, but process-death recovery/resume has not been validated. An interruption is not the same as a successfully verified final file.

## Media and torrents

Use the **YouTube** mode for a video, Shorts or `youtu.be` link, or the **Media** mode for another public page supported by the bundled yt-dlp release. Inspect the available formats first, then choose a quality or audio option. High qualities are downloaded as separate video and audio streams and merged locally with FFmpegKit; if that merge fails, Cyron falls back to a progressive single-file format. **Audio · MP3** invokes the bundled FFmpeg build for a local conversion. Do not use this app to bypass DRM, paywalls, login requirements, or a site's access controls; respect the rights and terms for each source. The current integration does not add browser cookies or authentication tokens. Media-site compatibility can change as sites change, and this feature has not yet been exercised on a physical device.

Use the **Torrent** mode for a magnet URI or a `.torrent` file selected from Android's document picker. BitTorrent availability depends on peers, network policy and the source; the app currently has no torrent-specific resume-data persistence or completed-seed controls. The selected output directory is written only after the torrent reports completion. Use only torrents you are authorized to download and share.

Third-party native dependencies add significant APK size and retain their own licenses. Review [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [SBOM.json](SBOM.json) and [third-party source and relinking information](docs/THIRD_PARTY_SOURCES.md) before redistributing an APK. FFmpegKit is distributed under LGPLv3; the required notices, license texts and source/replacement path are part of the release checklist. ABI support is currently limited to arm64-v8a and x86_64.

## Architecture

See [the architecture guide](docs/ARCHITECTURE.md) for module boundaries, transfer lifecycle, recovery and intentional limits. See [permissions and local data](docs/PERMISSIONS.md) before changing manifest permissions.

The static `docs/design-mockup/` page is a design prototype only; it simulates downloads in a browser and is not connected to the Android app.

`domain/` contains task/status/value models and repository contracts. `data/` implements Room persistence and DataStore preferences. `download/` contains the OkHttp engine, probing, range validation and transfer orchestration. `storage/` owns private segment files and atomic SAF publication. `background/` hosts the Android foreground service. `presentation/` contains Compose screens and the ViewModel. Hilt wires application-scoped dependencies.

## Security and permissions

The app requests Internet, network-state, foreground-service/data-sync, notification (Android 13+), wake-lock, and boot-completion permissions. Boot completion is used only to keep the periodic queue-recovery job registered; it does not start an always-running service. It does not request broad storage access. HTTP is enabled to honor entered links but is not encrypted or authenticated; prefer HTTPS. Users choose the destination through SAF. Partial segments stay in the app's private no-backup directory and are never published as the requested filename before the full file and optional digest are verified. Android application sandbox encryption, when available, is provided by the device/OS; Cyron does not independently encrypt those temporary files. Only use URLs you trust; HTTP cannot authenticate or encrypt the remote transfer.

## Tests

Run the expected checks before contributing; see [CONTRIBUTING.md](CONTRIBUTING.md). Android device and SAF-provider lifecycle checks are still required before a release.

`app/src/test` contains local JVM unit tests for filename safety and byte-range invariants plus MockWebServer-based HTTP protocol checks. `.github/workflows/android.yml` checks public-source safety, reviews dependency changes on pull requests, runs unit tests and Android Lint, runs the Room instrumentation test on an Android 35 emulator, and uploads a debug APK. Version tags use protected signing secrets to build verified APKs and create a draft GitHub Release. Additional SAF-provider, notification, crash-recovery, and physical-device lifecycle checks are still required before publishing a release.

## Security

Report vulnerabilities privately through GitHub Security Advisories as described in [SECURITY.md](SECURITY.md). Do not include credentials, private URLs, signing keys or personal files in public issues.

## License

Cyron Manager source code is Apache-2.0; see [LICENSE](LICENSE). Bundled dependencies retain their own terms. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [SBOM.json](SBOM.json) and [docs/RELEASE.md](docs/RELEASE.md) before redistributing a build.
