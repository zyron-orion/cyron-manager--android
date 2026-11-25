# Permissions and local data

| Permission/capability | Purpose | Timing |
|---|---|---|
| `INTERNET` | Connect directly to the URL the user enters. | Normal manifest permission; no runtime prompt. |
| `ACCESS_NETWORK_STATE` | Detect a lost network so interrupted transfers wait and retry instead of failing. | Normal manifest permission; no runtime prompt. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Keep an explicitly started download visible to Android while it transfers. | Android system permission; foreground notification is posted before work starts. |
| `POST_NOTIFICATIONS` | Show progress and completion alerts on Android 13 and later. | An explained one-time prompt asks on first launch; if declined, Settings links to Android notification settings. |
| `WAKE_LOCK` | Hold a partial wake lock while active transfer jobs are running. | Acquired by the service only while it has jobs; released when work ends. |
| `RECEIVE_BOOT_COMPLETED` | Keep the periodic queue-recovery job registered across device restarts. | Normal manifest permission; no runtime prompt. The job retries unfinished local records within normal Android JobScheduler limits. |
| SAF tree grant | Read/write only the folder selected by the user. | Requested through the system folder picker; only the returned persistable URI permission is retained. |

The app does **not** ask for broad external-storage access, contacts, location, media-library access, or an account. Cleartext HTTP is enabled so the engine can honor user-entered HTTP and HTTPS links; HTTP is not encrypted or authenticated, so HTTPS is strongly recommended. An expected SHA-256 digest helps detect content changes only when the expected value is trusted. Because Android and the user-selected document provider control the selected destination, availability can change if access is revoked or the external provider is offline. In that event Cyron reports that the folder must be chosen again.

Transfer records and settings are local Room/DataStore data. Partial segments stay in the app's `noBackupFilesDir`, outside the selected output folder, and are not independently encrypted by Cyron. The verified merge is streamed directly into a hidden document in the selected SAF folder, so no second private merged copy is kept. Android's device/app sandbox and device encryption are relied on for confidentiality at rest. A file downloaded over plain HTTP can be modified in transit; use HTTPS and, where available, provide a trusted expected SHA-256 digest.
