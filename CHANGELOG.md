# Changelog

## Unreleased

- Preserve singular vocabulary lemmas such as `consensus` instead of truncating their final `s`, and repair previously affected review data in place.
- Derive Chinese definitions for supported adverbs from bundled base-form entries and prevent English-only definitions from appearing in the Chinese review-card field.

## 0.1.0-alpha.1 — 2026-07-23

- First end-to-end SubLingo prerelease candidate.
- Local import and yt-dlp video download with persisted background work.
- Configurable Doubao/custom STT and DeepSeek/Doubao/custom LLM providers.
- Context-aware bilingual subtitles, 16:9 immersive/transcript playback, and word alignment.
- Vocabulary extraction, bundled/offline dictionary support, favorites, CEFR filtering, review cards, simplified SM-2 scheduling, statistics, and 12-month heatmap.
- Android Keystore-backed encrypted API keys/cookies and video-processing completion notifications.
- Release hardening: R8/resource shrinking, no destructive Room fallback, Release network logging disabled, transient-file cleanup, ABI-split AAB configuration, license/privacy/SBOM artifacts, and environment-only signing.
