# SubLingo Release Checklist

## Build and signing

- Run `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease`.
- Release signing is read only from environment variables:
  - `SUBLINGO_RELEASE_STORE_FILE`
  - `SUBLINGO_RELEASE_STORE_PASSWORD`
  - `SUBLINGO_RELEASE_KEY_ALIAS`
  - `SUBLINGO_RELEASE_KEY_PASSWORD`
- If those variables are absent, Gradle creates an unsigned Release artifact suitable for inspection but not public distribution.
- Never commit a keystore, passwords, or a generated signing properties file.

## Required validation

- Verify API 26 and a current Android version using the exact signed Release build.
- Exercise local import, YouTube/Bilibili download, cancellation/retry, process death/relaunch, notification denial, playback, transcript, review, and deletion.
- Verify Room upgrade from every previously distributed schema. A missing migration must fail loudly; destructive fallback is forbidden.
- Inspect Logcat for secrets and inspect the manifest/resources after R8.
- Check the arm64 device-delivered size from the AAB and the universal APK size.

## Distribution and compliance

- Update `versionCode`, `versionName`, changelog, screenshots, and supported-site wording.
- Regenerate `sbom/sublingo-release.cdx.json` and `THIRD_PARTY_NOTICES.md` from the release commit.
- Include `LICENSE`, `NOTICE`, `PRIVACY.md`, source-code URL, and corresponding-source instructions with the release.
- Confirm yt-dlp/youtubedl-android, FFmpeg, aria2c, ECDICT, Dictionary API data/audio, and platform download terms for the exact shipped package.
- Publish initially as a prerelease (`v0.1.0-alpha.1`) until real-device acceptance is complete.
