# Architecture

## Boundaries

- `domain/model`: persisted transfer state, byte ranges, settings, statistics and deterministic filename policy.
- `domain/repository`: contracts consumed by presentation and application services.
- `data`: Room entities/DAO and repository implementation; Preferences DataStore implementation.
- `download`: synchronous OkHttp I/O isolated on the dedicated `cyron-transfer` pool, remote probing, range planning, transfer control and HTTP response validation.
- `media`: direct `yt_dlp` metadata extraction inside Chaquopy, allow-listed yt-dlp downloads, separate video/audio streams, FFmpegKit merging/MP3 conversion and yt-dlp staging cleanup.
- `storage`: private sandbox segment files, crash-safe manifest writes, exact-length merge, SHA-256 verification and Storage Access Framework publication.
- `background`: Android foreground `dataSync` service, concurrency admission, progress notifications and recovery controls.
- `presentation`: Compose screens/state and a Hilt ViewModel; it never writes transfer bytes or depends on a backend.
- `di`: app-scoped OkHttp, Room, repository, connection-count and network-monitor dependencies. Transfer engines do not apply a user-configurable speed cap.

## Transfer lifecycle

1. The user chooses a destination folder with `ACTION_OPEN_DOCUMENT_TREE`. Cyron takes only the returned persistable read/write grant.
2. A probe follows redirects, checks a positive resource length, and uses a one-byte `Range` request to discover actual byte-range support. The resolved URL, validators and size are saved with the Room task.
3. Multiple requests are used only when the origin accepts ranges and returns a stable ETag or Last-Modified validator. Every segment maps to one inclusive, non-overlapping range. Requests send `Accept-Encoding: identity`, `If-Range` only with a strong ETag or Last-Modified validator, and validate HTTP status, validator, Content-Range, response length and segment boundary before appending. A weak ETag is never used in `If-Range`.
4. Segment files and an `AtomicFile` manifest are stored under `noBackupFilesDir`; the manifest binds the resolved URL, size, validators and exact range layout. A new/incompatible representation invalidates saved segments instead of merging them.
5. Transient network/HTTP failures retry the affected byte range with capped exponential backoff, waiting for connectivity when the device is offline. Pause cancels network work and closes/syncs segment streams. Cancel/delete waits for the worker to stop before removing files. Process-death recovery resumes interrupted rows after a sticky service restart; opening the Activity never pauses a live transfer. A failure of the private storage or of the selected document provider is reported as a storage error, never as a network error.
6. Completed segments are streamed directly into a random hidden document in the selected SAF folder. SHA-256 is computed while writing, the length and optional expected checksum are verified, and only then is the document renamed to a unique sanitized filename. On a publish or I/O failure the temporary document is removed and the verified segments are retained for a retry; the old private merged copy (which temporarily required about twice the file size) no longer exists. The destination is checked for writability before the network transfer starts.

## Media pipeline

The Java runner shipped with yt-dlp-android only maps a small allow-list of CLI flags and discards process stdout. Cyron therefore calls the bundled `yt_dlp` Python module directly for metadata (`extract_info` + `sanitize_info` + `json.dumps`), which avoids the old `-J` fallback that silently downloaded the whole video. A current pure-Python yt-dlp wheel is bundled as an asset and placed at the front of `sys.path`, because YouTube changes its clients faster than the AAR is updated. The runner is only a fallback for non-YouTube sites; it is not used for YouTube, where the JS-free client is selected automatically and supported client fallbacks are attempted without cookies or login.

The free yt-dlp build has no ffmpeg executable, so Cyron never asks yt-dlp to merge streams. A quality choice downloads a video-only stream and an audio-only stream, then muxes them with FFmpegKit. If video or audio extraction fails, the task reports the actionable error; a progressive single-file format is attempted only as the final fallback. Audio extraction uses stream copy when possible instead of re-encoding. Pause cancels both the yt-dlp future and the FFmpeg session; yt-dlp's normal `.part` continuation resumes the next attempt. The shared folder mutex is released before waiting for cooperative Python/native cancellation, so resume, delete and unrelated folder operations remain responsive.

## Data and dependencies

The Room schema is the source of truth for visible history/task state. DataStore is the source of truth for user preferences. Progress snapshots are transient and are written to Room at a bounded cadence; the completion worker writes the final status, SHA-256 and final content URI. Network and filesystem work run off the main thread. The app does not need an account or service endpoint.

Room schema changes must add an explicit migration and a migration test before increasing `CyronDatabase`'s version. Do not use destructive migration on user download history.

## Intentional limits

The engine declines unknown-size streaming responses. Servers that ignore ranges use one connection. Safe parallel/resume behavior requires a stable server validator. A provider that cannot publish a temporary document with a safe rename is rejected instead of exposing an unverified output file. Local sandbox segments rely on Android's app-private filesystem and device protection; Cyron does not implement custom encryption. Local JVM tests and Android 35 emulator tests are part of CI; physical-device, third-party SAF-provider, OS-version, live-site and long-transfer checks are still required before a public release.
