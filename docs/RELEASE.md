# Release guide

## Source repository

Before publishing the repository, confirm that only source, documentation, schemas, tests and required bundled assets are included:

```shell
git init -b main
node scripts/check-public.mjs
git status --short
```

The check rejects tracked signing material, local SDK configuration, environment files, crash logs and generated APKs. Keep `keystore.properties`, `release.keystore` and `local.properties` local and git-ignored. Enable GitHub secret scanning and push protection for the public repository.

## Verification

Run the same checks used by CI:

```shell
sh ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
sh ./gradlew --no-daemon connectedDebugAndroidTest
```

The instrumentation test requires a connected Android 35 emulator or device. UI, notification, SAF-provider, foreground-service, process-recovery and long-transfer checks still require a device or emulator pass.

## Local release signing

Copy `keystore.properties.example` to the ignored `keystore.properties` file and fill in the real values, or provide these environment variables:

- `CYRON_KEYSTORE_PATH`
- `CYRON_KEYSTORE_PASSWORD`
- `CYRON_KEY_ALIAS`
- `CYRON_KEY_PASSWORD`

All four values are required whenever any signing value is provided. The build fails instead of silently producing an unsigned release when signing configuration is incomplete.

```shell
sh ./gradlew --no-daemon assembleRelease
```

Outputs are written to `app/build/outputs/apk/release/`:

- `app-arm64-v8a-release.apk`
- `app-x86_64-release.apk`
- `app-universal-release.apk`

Without signing configuration, Gradle produces unsigned APKs. Do not distribute them. Verify every signed APK before installation or upload:

```shell
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## CI release signing

Create a protected GitHub environment named `release` with required reviewers. Add these repository or environment secrets:

- `CYRON_KEYSTORE_BASE64` — base64 encoding of the release keystore
- `CYRON_KEYSTORE_PASSWORD`
- `CYRON_KEY_ALIAS`
- `CYRON_KEY_PASSWORD`

The workflow decodes the keystore only into the runner's temporary directory, builds the release variant, verifies all three APK signatures with `apksigner`, writes SHA-256 checksums, and uploads the signed artifacts. It never writes signing values to the repository.

To create a release, push a tag such as `v1.0.6` that exactly matches `versionName` in `app/build.gradle.kts`. The release job creates a **draft** GitHub Release. Review the artifacts, notices, checksums and release notes before publishing it.

## Redistribution checklist

Before publishing an APK or GitHub Release:

1. Confirm that `THIRD_PARTY_NOTICES.md`, `SBOM.json` and the identical in-app notice asset describe the exact shipped versions.
2. Confirm that all required license texts are present under `app/src/main/assets/licenses/` and survive Release resource shrinking.
3. Review `docs/THIRD_PARTY_SOURCES.md` for FFmpegKit/libtorrent source, build and replacement/relinking information.
4. Install the signed arm64 APK on a physical device and the x86_64 APK on an emulator; exercise HTTP pause/resume/delete, YouTube pause/resume/delete, SAF publication, notifications and process recovery.
5. Confirm that the APK contains no debug-only tooling and that its certificate fingerprint matches the intended release key.
6. Publish only the signed APKs and checksums; never upload the keystore, local properties, build directories or crash logs.

The repository's Apache-2.0 license covers Cyron Manager source code. Bundled dependencies retain their own licenses and obligations.
