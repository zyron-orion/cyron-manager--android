# Third-party notices

These notices apply to Cyron Manager 1.0.6 (version code 8). Cyron Manager source is Apache-2.0; bundled dependencies retain their own terms. The exact runtime inventory and artifact hashes are in [SBOM.json](SBOM.json), and source provenance is in [docs/THIRD_PARTY_SOURCES.md](docs/THIRD_PARTY_SOURCES.md).

## Where to read the complete texts

On the device, open **Settings → Third-party licenses → Open full license texts**. The APK contains:

- `assets/licenses/ALL_DEPENDENCY_LICENSES.txt` — the complete collected bundle.
- `assets/licenses/INDEX.txt` — component, artifact and entry paths for every collected notice.
- `assets/licenses/` — project license texts (including MIT and BSD-3-Clause for wrappers/native components) and FFmpegKit provenance files.
- `app/build/generated/licenses/` in a local build — individual dependency notices extracted from AAR/JAR/wheel and nested Chaquopy archives.

`:app:collectDependencyLicenses` runs before every build. It reads the release runtime classpath, opens nested `.imy` and `classes.jar` archives, and preserves notices as assets so Release resource shrinking cannot remove them. `:app:verifyReleaseNotices` fails a release build check if required assets or ABI native libraries are missing.

## Runtime components

| Component | Version | License / notice |
|---|---:|---|
| Cyron Manager | 1.0.6 / 8 | Apache-2.0; see `LICENSE`. |
| AndroidX libraries | Versions in `SBOM.json` | Apache-2.0, except DataStore external protobuf which is BSD-3-Clause. Embedded AndroidX notices are collected. |
| Kotlin, coroutines and kotlinx.serialization | Versions in `SBOM.json` | Apache-2.0. |
| OkHttp and Okio | Versions in `SBOM.json` | Apache-2.0. OkHttp's public-suffix notice is collected. |
| Hilt, Dagger and Jakarta Inject | Versions in `SBOM.json` | Apache-2.0. |
| yt-dlp Android wrapper | 2.0.2 | MIT; [source](https://github.com/ffmpegkit-maintained/yt-dlp-android/tree/5423ef0e6ab210921b9d8ad9a65d6951133642fc). |
| Preferred yt-dlp wheel | 2026.10.26 | Unlicense; [source](https://github.com/yt-dlp/yt-dlp/tree/c91f8de0bfb3577894abad716eaaa01659e6bd14). |
| Fallback yt-dlp in the wrapper | 2026.6.9 | Unlicense; [source](https://github.com/yt-dlp/yt-dlp/tree/7f7bdc974dc61d941d1a0d51c4e21a0fdb7b2d06). |
| certifi in the wrapper | 2026.6.17 | MPL-2.0; [source](https://github.com/certifi/python-certifi/tree/2026.06.17). |
| setuptools in the wrapper | 68.2.2 | MIT; [source](https://github.com/pypa/setuptools/tree/v68.2.2). |
| pyelftools in the wrapper | 0.26 | Public Domain according to package metadata; [source](https://github.com/eliben/pyelftools). |
| Python runtime in the wrapper | 3.13.9 | PSF-2.0; [source](https://github.com/python/cpython/tree/v3.13.9). |
| Chaquopy bridge/native runtime | Bundled with wrapper 2.0.2 | MIT; exact build files are in the pinned wrapper source. |
| OpenSSL in Chaquopy | 3.0.18 | Apache-2.0; [source](https://github.com/openssl/openssl/tree/openssl-3.0.18). |
| SQLite in Chaquopy | 3.50.4 | Public Domain; [release notes](https://www.sqlite.org/releaselog/3_50_4.html). |
| FFmpegKit full (non-GPL) | 8.1.9 | LGPL-3.0; [source](https://github.com/ffmpegkit-maintained/ffmpeg/tree/469e560b89a9ba4cab357d2c306c676b0c611745). |
| FFmpeg | n8.1.3 | LGPL-2.1 or later, with portions under LGPLv3; [source](https://github.com/ffmpeg/ffmpeg/tree/n8.1.3). |
| FFmpeg external libraries | Exact build set in `SBOM.json` | Mixed permissive, LGPL, ISC, FTL and other notices. All 33 AAR license resources and the corrected libiconv library text are bundled. |
| libtorrent4j wrapper and Android artifacts | 2.0.6-26 | MIT; [source](https://github.com/aldenml/libtorrent4j/tree/863b09ab2884cfd345c58cdb4ca904197ba126c5). |
| libtorrent | 2.0.6.0 | BSD-3-Clause; [source](https://github.com/arvidn/libtorrent/tree/v2.0.6). |
| OpenSSL in libtorrent4j | 1.1.1m | OpenSSL-1.1; [source](https://github.com/openssl/openssl/tree/OpenSSL_1_1_1m). |
| libdatachannel, libjuice and usrsctp | Exact versions not established from shipped binary strings | Listed in `SBOM.json`; use the native build/source notices at the pinned libtorrent4j commit. |
| Vazirmatn typeface | 33.003 | SIL Open Font License 1.1; exact text in `assets/licenses/Vazirmatn-OFL.txt`. |

`javax.inject:javax.inject:1` has no license declaration or embedded notice in the inspected artifact. The SBOM does not assert a license for it.

## FFmpegKit source and relinking

The shipped artifact is `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9`, not the GPL variant. Its AAR SHA-256 is:

`40b52770a339af3bf8c02d8a9808c89b5f0e0e56eca39c10dc76c01b90f32306`

Provenance:

- Release tag: `v8.1.9-lts-android`
- Commit: `469e560b89a9ba4cab357d2c306c676b0c611745`
- FFmpeg: `n8.1.3`
- NDK: r27c, Android API 24, shared libraries, version-3 configuration
- ABIs: `arm64-v8a` and `x86_64`
- No `--enable-gpl` or `--enable-nonfree`; GPL-only codecs such as x264, x265 and Xvid are not enabled in this artifact

The app includes `assets/licenses/ffmpeg-kit/8.1.9/BUILD-AND-RELINK.txt`, `native-libs.sha256`, the upstream third-party notice, all AAR raw license resources and complete LGPL/GPL texts. Cyron uses the LGPLv3 section 4(d)(0) source/build route: a modified compatible FFmpegKit AAR can be built from the pinned source, substituted into this Apache-2.0 application, signed with the user's own key and installed. The app does not claim an in-place runtime library-picker that is not implemented.

Two upstream notice anomalies are corrected in the bundled build notice. The AAR's `source.txt` is stale Arthenica boilerplate with retired contact details and misleading GPL wording. Its `license_libiconv.txt` contains GPLv3 text for the iconv program, while GNU libiconv 1.17's library is LGPLv2.1; the correct `COPYING.LIB` text is bundled as `libiconv-LGPL-2.1.txt`.

## Redistribution limits

This document and the generated bundle record the inspected artifacts and packaging path; they are not legal advice or a legal clearance. Review the exact source, notices, modifications and distribution method before publishing a modified combined work. Do not use Cyron Manager to bypass DRM, paywalls, logins or site access controls.
