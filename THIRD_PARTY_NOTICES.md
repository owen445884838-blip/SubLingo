# Third-party software inventory

Generated/reviewed for SubLingo `0.1.0` on 2026-07-23.

This is the human-readable companion to `sbom/sublingo-release.cdx.json`.
Versions are taken from the resolved Release runtime classpath; transitive
dependencies remain authoritative in the SBOM.

| Component family | Version used | Typical upstream license | Notes |
|---|---:|---|---|
| youtubedl-android library / FFmpeg / aria2c packages | 0.18.1 | GPL-3.0 and bundled component licenses | Includes native/Python runtime payloads; corresponding-source obligations must be honored |
| yt-dlp | nightly 2026.08.16.020253 | Unlicense / upstream notices | Official zipapp overrides youtubedl-android's stale embedded extractor; includes the upstream EJS solver distribution |
| AndroidX / Jetpack Compose / Material3 / Media3 / Room / WorkManager | resolved in SBOM | Apache-2.0 | Google/Android open-source libraries |
| Hilt / Dagger | 2.52 | Apache-2.0 | Dependency injection and generated code |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | resolved in SBOM | Apache-2.0 | Kotlin runtime/tooling libraries |
| OkHttp / Retrofit | 4.12.0 / 2.11.0 | Apache-2.0 | Network clients |
| Coil | 2.7.0 | Apache-2.0 | Image loading |
| Material Components for Android | 1.12.0 | Apache-2.0 | Android UI support |
| ECDICT-derived basic dictionary | bundled format v3 | MIT | Local dictionary data; retain upstream attribution/license |

Before publishing a binary, regenerate the SBOM from the same commit and
confirm every upstream license/notice against the actual AAB/APK contents.
