# Contributing

Thanks for considering a contribution to Cyron Manager. Keep the Android app native; do not introduce a WebView, cloud backend, broad storage permission, or an app-imposed download-speed cap.

## Local verification

- Use JDK 17, Android SDK Platform 36 and the checked-in Gradle 9.6 wrapper.
- Initialize Git when working from a source archive, then run `node scripts/check-public.mjs` to reject tracked secrets, generated APKs and missing release notices.
- Run `sh ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` before opening a pull request. Add `--offline` only when every required artifact is already cached.
- Run `sh ./gradlew --no-daemon connectedDebugAndroidTest` on an Android 35 emulator or device.
- Exercise the app on an emulator or device for changes involving notifications, SAF, foreground services, media cancellation, app recovery, or Android-version-specific behavior.

## Change guidelines

- Keep Compose in `presentation`, Android persistence in `data`, HTTP orchestration in `download`, and SAF/private files in `storage`.
- Add deterministic unit tests for domain/protocol behavior. Use MockWebServer for HTTP edge cases; use instrumented tests for Room and system document-provider behavior.
- Do not merge byte ranges unless total size, range layout and validators have been checked. Preserve the user's existing history when changing the Room schema with an explicit migration and migration test.
- Preserve and document the reason for every Android permission. Never commit signing keys, credentials, local SDK paths, generated APKs, crash logs, or personal download data.
- Keep visible errors actionable and avoid claiming integrity, resumability, or provider durability that the remote server or document provider cannot guarantee.
- Keep Persian strings readable in RTL layouts and test both language directions for UI changes.
- Dependency or bundled-asset changes must update `THIRD_PARTY_NOTICES.md`, `SBOM.json`, the identical in-app notice asset, and `docs/THIRD_PARTY_SOURCES.md` when native source/relinking information changes.
- GitHub Actions are pinned to commit SHAs. Update the version comment and pin together, and keep Gradle dependency verification metadata current.

## Security and releases

Report vulnerabilities privately using GitHub Security Advisories; see [SECURITY.md](SECURITY.md). A passing build is necessary but not a substitute for device, SAF-provider, lifecycle and release-signing tests. Follow [docs/RELEASE.md](docs/RELEASE.md) for signing, artifact verification and draft-release creation.
