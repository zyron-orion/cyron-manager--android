## Summary

Describe the problem and the change.

## Verification

- [ ] `sh ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`
- [ ] Device/emulator checks completed for UI, notifications, SAF, recovery, or service changes
- [ ] Room schema changes include an explicit migration and migration test
- [ ] No signing keys, credentials, local paths, generated APKs, or personal data are included
- [ ] Third-party dependency or bundled-asset changes update `THIRD_PARTY_NOTICES.md`, `SBOM.json`, and in-app notices
- [ ] User-visible strings remain readable in Persian and RTL layouts
