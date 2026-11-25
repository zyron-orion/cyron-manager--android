# Security policy

## Supported versions

Security fixes are provided for the latest published `1.0.x` release. Older versions should be upgraded before reporting a problem.

## Reporting a vulnerability

Use **GitHub → Security → Advisories → Report a vulnerability** to contact the maintainers privately. Do not open a public issue for an undisclosed vulnerability, and do not include signing keys, passwords, access tokens, personal download URLs, or private files in a report.

Include the app version, Android version and device model, a short reproduction description, the affected component, and the security impact. For crashes, remove personal data and URLs from logs before attaching them.

Maintainers aim to acknowledge a report within three business days, assess its impact, and coordinate a fix and disclosure timeline with the reporter.

## Scope

Cyron Manager runs on the user's device and does not operate a backend. Reports about unsafe URL validation, TLS downgrade behavior, path traversal, unsafe SAF publication, credential exposure, notification/service lifecycle flaws, or verification of downloaded bytes are in scope.

Reports about a third-party website's availability, a remote server's content, or BitTorrent peers are generally out of scope unless the app introduces the vulnerability. Do not test third-party services, accounts, or networks without explicit authorization.

## Security properties and limits

- HTTPS is strongly recommended. Cleartext HTTP is supported only for links the user deliberately enters and is not encrypted or authenticated.
- A SHA-256 digest detects a content mismatch only when the expected digest comes from a trusted source.
- Partial files remain in Android's app-private storage; the app does not provide a separate custom encryption layer.
- The app does not bypass DRM, paywalls, logins, or site access controls and does not collect browser cookies.
