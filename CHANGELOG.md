# Changelog

## Unreleased

- Preserve an existing lexeme's primary key when regenerating vocabulary after an in-place lemma repair, preventing foreign-key failures in occurrence and review-card persistence.
- Restore silent-e and other inflected vocabulary forms against the bundled dictionary, repair affected existing review cards such as `eliminat`, and make review-card transitions advance before rating persistence completes.
- Preserve singular vocabulary lemmas such as `consensus` instead of truncating their final `s`, and repair previously affected review data in place.
- Derive Chinese definitions for supported adverbs from bundled base-form entries and prevent English-only definitions from appearing in the Chinese review-card field.
- Player double-tap rewind, play/pause, and fast-forward gestures no longer reveal playback controls.
- Immersive and transcript players now hand off the live playback position in both directions.
- Bilingual transcript highlighting no longer maps one English phrase to unrelated, disjoint Chinese regions.
- Synthetic Chinese gap coverage is no longer shown as a semantic English/Chinese word mapping; existing transcripts are repaired without retranslation.
- Returning from the transcript resets the immersive player's transition state, so the transcript can be opened again from the same player instance.

## 0.1.0-alpha.1 — 2026-07-23

- First end-to-end SubLingo prerelease candidate.
- Local import and yt-dlp video download with persisted background work.
- Configurable Doubao/custom STT and DeepSeek/Doubao/custom LLM providers.
- Context-aware bilingual subtitles, 16:9 immersive/transcript playback, and word alignment.
- Vocabulary extraction, bundled/offline dictionary support, favorites, CEFR filtering, review cards, simplified SM-2 scheduling, statistics, and 12-month heatmap.
- Android Keystore-backed encrypted API keys/cookies and video-processing completion notifications.
- Release hardening: R8/resource shrinking, no destructive Room fallback, Release network logging disabled, transient-file cleanup, ABI-split AAB configuration, license/privacy/SBOM artifacts, and environment-only signing.
