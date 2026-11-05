## Summary

<!-- What does this PR change and why? Link issues with "Closes #123". -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / cleanup
- [ ] Docs / CI

## How was this tested?

- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] `./gradlew :app:lintDebug` has no new errors
- [ ] Ran on a device or emulator (model / Android version: ______)
- [ ] Relevant items from the field checklist in `docs/TESTING.md`

## Checklist

- [ ] `domain/` stays pure Kotlin (no `android.*` imports)
- [ ] Works offline (airplane mode), except map tile download
- [ ] No new network calls, analytics, or data leaving the device
- [ ] New permissions are justified in `README.md` and `docs/PRIVACY.md`
- [ ] UI strings in `strings.xml`; TalkBack labels on new controls
- [ ] Screenshots attached for UI changes

## Screenshots

<!-- Before / after, if UI changed. -->
