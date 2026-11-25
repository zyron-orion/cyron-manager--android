# Third-party sources and relinking

This document records the source provenance for bundled components. The machine-readable runtime inventory is [SBOM.json](../SBOM.json). Exact notice texts are collected into the APK by `:app:collectDependencyLicenses` and are available in **Settings → Third-party licenses → Open full license texts**.

Regenerate the reports after dependency or bundled-asset changes:

```shell
sh ./gradlew --no-daemon :app:generateSbom :app:collectDependencyLicenses
```

## FFmpegKit and FFmpeg

| Item | Version / revision | Source |
|---|---|---|
| FFmpegKit full (non-GPL) | 8.1.9, tag `v8.1.9-lts-android`, commit `469e560b89a9ba4cab357d2c306c676b0c611745` | [Maintained repository](https://github.com/ffmpegkit-maintained/ffmpeg/tree/469e560b89a9ba4cab357d2c306c676b0c611745) |
| FFmpeg | n8.1.3 | [FFmpeg source tag](https://github.com/ffmpeg/ffmpeg/tree/n8.1.3) |
| Android NDK | r27c, API 24, shared libraries, version-3 configuration | Build scripts in the pinned FFmpegKit commit |
| GNU libiconv library | 1.17, LGPLv2.1 | [Correct library license](https://github.com/arthenica/libiconv/blob/v1.17/COPYING.LIB) |

The AAR SHA-256 is `40b52770a339af3bf8c02d8a9808c89b5f0e0e56eca39c10dc76c01b90f32306`. It matches the official `ffmpeg-kit-full-8.1.9-arm64-v8a-x86_64.aar` release asset. The `full-gpl` artifact is not used.

The app bundles:

- `app/src/main/assets/licenses/ffmpeg-kit/8.1.9/THIRD-PARTY-NOTICES.txt`
- `app/src/main/assets/licenses/ffmpeg-kit/8.1.9/BUILD-AND-RELINK.txt`
- `app/src/main/assets/licenses/ffmpeg-kit/8.1.9/native-libs.sha256`
- `app/src/main/assets/licenses/ffmpeg-kit/8.1.9/libiconv-LGPL-2.1.txt`
- every `res/raw/license*.txt` and `source.txt` entry from the exact AAR under `upstream-res/`
- complete LGPLv2.0, LGPLv2.1, LGPLv3 and GPLv3 texts

Two upstream notice anomalies are corrected in `BUILD-AND-RELINK.txt`: the AAR's `source.txt` is stale Arthenica boilerplate, and `license_libiconv.txt` contains the GPLv3 program text rather than the LGPLv2.1 library text. The maintained non-GPL build excludes GPL-only codecs such as x264, x265 and Xvid.

Cyron uses the LGPLv3 section 4(d)(0) source/build route. A modified compatible FFmpegKit AAR can be built from the pinned source, substituted into this app, signed with the user's own key and installed. The app does not claim an in-place runtime library replacement feature. See the bundled build/relink notice for the required steps and installation information.

## yt-dlp, Python and Chaquopy

| Component | Version / revision | License | Source |
|---|---|---|---|
| yt-dlp Android wrapper | 2.0.2, commit `5423ef0e6ab210921b9d8ad9a65d6951133642fc` | MIT | [Wrapper repository](https://github.com/ffmpegkit-maintained/yt-dlp-android/tree/5423ef0e6ab210921b9d8ad9a65d6951133642fc) |
| Preferred yt-dlp wheel | 2026.10.26, commit `c91f8de0bfb3577894abad716eaaa01659e6bd14` | Unlicense | [yt-dlp commit](https://github.com/yt-dlp/yt-dlp/tree/c91f8de0bfb3577894abad716eaaa01659e6bd14) |
| Fallback yt-dlp | 2026.6.9, commit `7f7bdc974dc61d941d1a0d51c4e21a0fdb7b2d06` | Unlicense | [yt-dlp tag](https://github.com/yt-dlp/yt-dlp/tree/7f7bdc974dc61d941d1a0d51c4e21a0fdb7b2d06) |
| certifi | 2026.6.17 | MPL-2.0 | [certifi tag](https://github.com/certifi/python-certifi/tree/2026.06.17) |
| setuptools | 68.2.2 | MIT | [setuptools tag](https://github.com/pypa/setuptools/tree/v68.2.2) |
| pyelftools | 0.26 | Public Domain | [pyelftools repository](https://github.com/eliben/pyelftools) |
| Python | 3.13.9 | PSF-2.0 | [Python tag](https://github.com/python/cpython/tree/v3.13.9) |
| OpenSSL in Chaquopy | 3.0.18 | Apache-2.0 | [OpenSSL tag](https://github.com/openssl/openssl/tree/openssl-3.0.18) |
| SQLite in Chaquopy | 3.50.4 | Public Domain | [SQLite release notes](https://www.sqlite.org/releaselog/3_50_4.html) |
| Chaquopy bridge/native runtime | Bundled with wrapper 2.0.2 | MIT | Wrapper source and build files at the pinned commit |

The wrapper AAR does not contain a top-level `THIRD-PARTY-NOTICES.txt`. Its Python packages are inside `assets/chaquopy/*.imy`; the license collector opens those nested archives and bundles the embedded yt-dlp, certifi and setuptools notices. The preferred wheel's embedded Unlicense text is collected separately.

## libtorrent4j and native torrent libraries

| Component | Version / revision | License | Source |
|---|---|---|---|
| libtorrent4j wrapper and Android artifacts | 2.0.6-26, commit `863b09ab2884cfd345c58cdb4ca904197ba126c5` | MIT | [libtorrent4j commit](https://github.com/aldenml/libtorrent4j/tree/863b09ab2884cfd345c58cdb4ca904197ba126c5) |
| libtorrent | 2.0.6.0 | BSD-3-Clause | [libtorrent tag](https://github.com/arvidn/libtorrent/tree/v2.0.6) |
| OpenSSL in libtorrent4j | 1.1.1m | OpenSSL-1.1 | [OpenSSL tag](https://github.com/openssl/openssl/tree/OpenSSL_1_1_1m) |
| libdatachannel, libjuice, usrsctp | Exact versions not established from the shipped binary strings | See pinned native build/source notices | libtorrent4j source/build files at the pinned commit |

The three libtorrent4j JARs do not embed an upstream license bundle. The wrapper's MIT license is declared in its POM; the native libraries and their build provenance are listed separately in the SBOM. The app exports completed torrent data through SAF and does not modify the native library.

## Font and JVM dependencies

- Vazirmatn 33.003 is bundled under the SIL Open Font License 1.1. The exact text is in `app/src/main/assets/licenses/Vazirmatn-OFL.txt`; upstream is [rastikerdar/vazirmatn](https://github.com/rastikerdar/vazirmatn).
- AndroidX, Kotlin, coroutines, serialization, OkHttp/Okio, Hilt/Dagger, Jakarta Inject and other runtime JVM dependencies are listed with versions and SHA-256 hashes in [SBOM.json](../SBOM.json). Their embedded Maven notices are extracted into the generated license bundle.
- DataStore's external protobuf artifact is BSD-3-Clause; most AndroidX artifacts are Apache-2.0. `javax.inject:javax.inject:1` has no license declaration or embedded notice in the inspected artifact and is therefore not assigned an asserted license in the SBOM.

## Verification

CI regenerates `SBOM.json` and fails if it differs from the checked-in file. `:app:verifyReleaseNotices` inspects every release APK and fails when required notice assets, FFmpeg provenance files or ABI native libraries are missing. This verifies packaging, not legal clearance; obtain qualified review before distributing a modified combined work.
