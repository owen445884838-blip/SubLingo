# SubLingo Agent Instructions

This file applies to the entire repository. More specific `AGENTS.md` files, if added later, override it only within their directories.

## Start here

Before editing code:

1. Read `README.md`, the newest authoritative section of `PROGRESS.md`, `IMPLEMENTATION_PLAN.md`, and the relevant parts of `DESIGN.md`.
2. Run `git status --short --branch`, `git log --oneline -10`, and `git remote -v`.
3. Treat the newest dated section of `PROGRESS.md` as authoritative when older notes conflict with it.
4. Inspect the existing implementation and tests before proposing a replacement design.
5. Preserve all unrelated or uncommitted user changes. Never discard, reset, or overwrite them.

## Project snapshot

- Android application written in Kotlin and Jetpack Compose.
- Core libraries: Room, Hilt, WorkManager, Media3, OkHttp/Retrofit, kotlinx.serialization, and youtubedl-android.
- Minimum supported version: Android 8.0 / API 26.
- Room is the source of truth for videos, jobs, subtitles, vocabulary, review state, and provider profiles.
- API keys and cookies must remain in the Android Keystore-backed encrypted secret store, never in Room or source control.
- The project is GPL-3.0 because the shipped dependency set includes GPL components.

## Git workflow

- Keep `main` releasable. Do not develop directly on `main` unless the user explicitly requests it.
- Start each feature or fix from an up-to-date `main`:

  ```bash
  git switch main
  git pull --ff-only origin main
  git switch -c feature/short-name
  ```

- Use one focused branch per feature, fix, refactor, or documentation change.
- Prefer Conventional Commit prefixes: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, or `chore:`.
- Before handing off, push the branch and report its name, latest commit, validation results, and Pull Request URL when one exists.
- Do not force-push shared branches. If rebasing a personal feature branch is necessary, use only `git push --force-with-lease`.

## Implementation rules

- Follow existing architecture, naming, Compose patterns, and dependency-injection boundaries.
- Keep UI state in ViewModels/repositories where the surrounding code already does so; avoid introducing duplicate sources of truth.
- Keep video pixels aspect-correct. Container changes must not stretch or squash media.
- Preserve cancellation semantics in coroutines and WorkManager. Never swallow `CancellationException`.
- Do not turn cleanup, notification, or optional metadata failures into successful-pipeline failures.
- Avoid unnecessary new dependencies, especially large UI, charting, media, or networking libraries.
- Do not reintroduce removed product features or provider presets solely because older planning notes mention them.

## Room and persisted data

- Never use `fallbackToDestructiveMigration()` or otherwise delete user data to solve a schema problem.
- Every schema change requires:
  - a version increment;
  - an explicit migration from the previous schema;
  - an updated exported schema under `app/schemas/`;
  - focused migration tests;
  - verification that existing videos, jobs, subtitles, vocabulary, favorites, and review history remain readable.
- WorkManager worker class names may be persisted. Keep/rename them carefully and update R8 rules when required.

## Security and privacy

- Never commit or log API keys, cookies, authorization headers, provider request bodies, signed URLs, private media, keystores, or signing passwords.
- Release builds must not enable HTTP `BODY` or `HEADERS` logging.
- User-facing errors and WorkManager output must not contain raw provider responses, yt-dlp output, or stack traces.
- Custom provider endpoints should remain HTTPS by default.
- Before committing, inspect staged changes for secrets and confirm ignored files are still ignored.

## Files that must remain untracked

- `local.properties`
- `.idea/`, `.gradle/`, `.gradle-user/`, and all `build/` directories
- `*.jks`, `*.keystore`, signing property files, and passwords
- `*.apk`, `*.aab`, `*.apks`, and `*.idsig`
- local API keys, cookies, provider exports, private videos, logs, and temporary media files

Do not weaken `.gitignore` to commit these files. Release binaries should be attached to a GitHub Release rather than stored in Git history.

## Validation

Run focused tests while developing. For a substantial merge candidate, run the full local gate with Android Studio JBR 17:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew clean \
  :app:testDebugUnitTest \
  :app:lintRelease \
  :app:assembleRelease \
  :app:bundleRelease \
  :app:generateReleaseSbom
```

Also apply these risk-based checks:

- UI/player changes: exercise the affected route on a real device or emulator and inspect Logcat.
- Compatibility-sensitive changes: verify API 26 and a current Android version.
- R8, Hilt, WorkManager, media, or native-runtime changes: install and launch the actual Release build.
- Pipeline changes: test cancellation, retry, process relaunch, Room persistence, and duplicate-work behavior as applicable.
- Release changes: verify signing state, APK/AAB contents, device-delivered size, privacy/compliance files, and SBOM.

Do not claim a check passed unless it was actually run. Clearly separate automated, emulator, real-device, and manual validation.

## Documentation and handoff

After material work, update the top of `PROGRESS.md` with:

- date and feature/fix name;
- implementation summary;
- affected modules and persisted-data changes;
- exact tests/builds/devices used;
- known issues, limitations, or remaining work;
- branch, commit, and Pull Request when available.

Update `CHANGELOG.md` for user-visible release changes. Update `README.md`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, `SOURCE_DISTRIBUTION.md`, or `RELEASE_CHECKLIST.md` when their claims change.

## Release boundary

- Validation APKs signed with a debug or temporary key are never production releases.
- Public release requires the permanent production signing key supplied through the documented `SUBLINGO_RELEASE_*` environment variables.
- Preserve the R8 mapping for every published build.
- Tag public releases only after the final signed artifacts, source tag, compliance materials, and release notes agree on the same version.
