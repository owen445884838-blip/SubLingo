# SubLingo Progress

> Last updated: 2026-07-25
> Purpose: give any new agent a fast snapshot of the current implementation state.
> Status precedence: the 2026-07-25 player/transcript and review-vocabulary fixes, the 2026-07-23 M5 acceptance and product update, the 2026-07-22 acceptance section, and the 2026-07-21 snapshot below are authoritative when older historical sections describe superseded behavior.

## Combined device validation build (2026-07-26)

- Root cause of the missing phone changes: the device had the Debug APK from `fix/review-vocabulary-corrections`, while the completed player/transcript work remained only on `feature/player-transcript-fixes`. Installing the later APK replaced the earlier player build because both use `versionCode 1` / `versionName 0.1.0`.
- Created `feature/device-latest-integration` from the review-fix branch and merged the player/transcript branch. The integrated source now contains both sets of fixes, with no Room schema change. The merged Debug build passed 128 unit tests, `:app:lintDebug`, and `:app:assembleDebug`.
- The integrated APK was installed in place on OPPO PHY110 while the device was connected. Its device SHA-256 matched the local artifact (`7b6be6d6315b7a7db33611d36aa44f39243210f1effa25ff34af24f5a70703aa`), Home and immersive playback rendered, double-tap playback did not reveal controls, and the transcript route rendered bilingual cues.
- During repeated transition testing, returning to the immersive player left the one-shot transcript-navigation flag set, which prevented reopening the transcript from the same player instance. This is now reset together with the transition animation when the live playback handoff is consumed. The rebuilt APK passed the same automated tests.
- Final device install: OPPO PHY110 (`9b43a22c`) received `app/build/outputs/apk/debug/app-debug.apk` in place on 2026-07-26 17:54:30 with app data retained. Device and local SHA-256 both equal `18f92d06a9224d81328e51275a6e2bce7a89879a420d7e600171576ee6d8817f`; cold launch rendered Home and persisted videos. Manual re-entry interaction remains available for extended user acceptance after disconnecting USB.
- Branch: `feature/device-latest-integration`, commit `f89054b`, pushed to `origin`. Do not use the older `app-debug.apk` installed before this section for acceptance.

## Review vocabulary correction and Chinese definitions (2026-07-25)

- Fixed the local fallback lemmatizer so singular words with lexical `s` endings, including `consensus`, `analysis`, `status`, and `news`, are not truncated. Existing malformed lexemes are repaired in place from their persisted occurrence surface forms, preserving lexeme IDs, review cards, favorites, occurrences, and review history; a normalized-lemma collision is skipped rather than merged unsafely.
- Standard dictionary lookup now derives supported adverbs from bundled base forms before remote fallback. For example, `collectively` resolves through `collective` and receives a Chinese adverb definition. Review cards and word-book views no longer display an English definition as a Chinese fallback; they prefer a standard Chinese definition, then the contextual Chinese meaning, then `释义待补全`.
- Affected modules: vocabulary normalization/legacy repair, vocabulary DAO queries, bundled dictionary fallback, standard-sense repair, review UI, and focused unit tests. There is no Room schema or persisted-data format change and no migration is required.
- Automated validation with Android Studio JBR 17: focused vocabulary/dictionary/review tests plus `compileDebugKotlin` passed; the complete `:app:testDebugUnitTest :app:assembleDebug` gate passed with 126 tests, 0 failures, and 0 errors across 32 suites; `:app:lintDebug` passed; `git diff --check` passed.
- Real-device validation: the Debug APK was installed in place on OPPO PHY110 (`9b43a22c`) with app data retained. Opening Review ran the repair successfully: persisted `consensu` became `consensus` with phonetic and bundled Chinese sense, `collectively` received `集体地；聚集地；共同地`, and `PRAGMA foreign_key_check` returned no violations. Home and Review rendered normally, and inspected Logcat contained no app fatal, Room, or SQLite error.
- Known limitation: derived Chinese definitions currently cover regular `-ly` adverbs when a bundled base-form entry exists. Other missing dictionary forms remain `释义待补全` unless contextual Chinese is available or a provider supplies a Chinese sense.
- Branch: `fix/review-vocabulary-corrections`; implementation commit `88ca540` is pushed to `origin`. Pull Request: https://github.com/owen445884838-blip/SubLingo/pull/3.

## Player gesture, progress handoff, and bilingual mapping fixes (2026-07-25)

- Double-tap rewind, play/pause, and fast-forward actions in both the immersive and transcript players no longer make player controls visible. Single taps and direct control interactions retain the existing control visibility and timeout behavior.
- Returning from the transcript by downward gesture, header back, or Android system back now publishes the live Media3 position through a video-scoped, one-shot handoff before the immersive player resumes. Room persistence remains the durable source of truth, while the handoff prevents stale `rememberSaveable` state or an asynchronous database update from restoring the pre-transcript position.
- Transcript display repair now rejects disjoint Chinese regions attached to one English occurrence and retains the most informative contiguous region. Adjacent fragments that form one semantic mapping, such as `did not -> 没 + 有`, remain supported. This repairs existing persisted transcripts at display time without schema changes, retranscription, retranslation, or database deletion.
- Affected modules: shared video gesture helpers, immersive player UI, transcript player UI/ViewModel, and focused unit tests. Persisted-data changes: none; Room remains at schema 13.
- Automated validation with Android Studio JBR 17: `:app:testDebugUnitTest :app:assembleDebug` passed with 125 tests, 0 failures, and 0 errors; `:app:lintDebug` passed; `git diff --check` passed. The Debug APK is `app/build/outputs/apk/debug/app-debug.apk` and remains untracked.
- Device validation was not run because no Android device or emulator was connected. Manual gesture, return-transition, and screenshot-specific transcript verification remain required on a device before merge.
- Branch: `feature/player-transcript-fixes`. Implementation commit: `d6e3d2d`. The branch is pushed to `origin`; the GitHub PR form is prepared at `https://github.com/owen445884838-blip/SubLingo/pull/new/feature/player-transcript-fixes` but has not been submitted.

## GitHub prerelease and project presentation (2026-07-23)

- The OPPO PHY110-validated Release candidate APK is published as the `v0.1.0-alpha.1` GitHub prerelease asset. It remains validation-signed with the Android debug certificate and is explicitly not a production/store signing artifact.
- Four real-device screenshots from OPPO PHY110 are tracked under `docs/screenshots/` and displayed in `README.md`: Home, Review, bilingual playback/transcript, and Settings. Screenshots expose no plaintext API key, Cookie, signing material, or private account identifier.
- Branch: `docs/add-release-and-screenshots`. Validation for the documentation change consists of image inspection, `git diff --check`, APK SHA-256 verification, and GitHub Release/README rendering checks.

## M5 engineering completion and Release acceptance (2026-07-23)

### Status
- M4 is complete. M5 engineering work is complete and has passed the local Release acceptance gate for `0.1.0` (`versionCode 1`). The first Release candidate artifacts have been generated.
- This is an engineering-accepted candidate, not yet a publicly publishable binary: the validation APK uses the local Android debug certificate, while the normal Release APK/AAB remain unsigned when the four `SUBLINGO_RELEASE_*` environment variables are absent. Public distribution still requires the permanent production signing key, final source repository/tag URL, and final legal/store-listing review.

### Release hardening delivered
- Release now enables R8 minification, resource shrinking, BuildConfig generation, and App Bundle ABI/density splits. Material3 is explicitly pinned to `1.3.1`, keeping Debug and Release on the same API surface.
- Production signing is environment-only through `SUBLINGO_RELEASE_STORE_FILE`, `SUBLINGO_RELEASE_STORE_PASSWORD`, `SUBLINGO_RELEASE_KEY_ALIAS`, and `SUBLINGO_RELEASE_KEY_PASSWORD`; no keystore or password is committed.
- Room schema export is enabled, schema 13 is checked in, the complete `2 → 13` migration chain is registered, and destructive migration fallback has been removed. `MigrationChainTest` verifies continuous coverage.
- Release HTTP logging is disabled. Download worker results no longer expose raw yt-dlp output or stack traces. A source scan found no embedded API key/Bearer-token candidates and no BODY/HEADERS HTTP logger enabled for Release.
- `AppStorageCleaner` removes transient cookie files and stale partial files at startup, removes completed audio chunks and their Room rows, and removes per-video media directories after video deletion without converting a successful pipeline into failure.
- GPL/compliance and release materials now include `LICENSE`, `NOTICE`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`, `SOURCE_DISTRIBUTION.md`, `RELEASE_CHECKLIST.md`, `CHANGELOG.md`, and a CycloneDX SBOM containing 150 resolved Release components.

### Automated acceptance
- A clean gate passed with `./gradlew clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease :app:generateReleaseSbom` using Android Studio JBR 17.
- Unit tests: 122 passed, 0 failures, 0 errors across 32 suites. Release lint passed; Release compilation, R8/resource shrinking, APK packaging, and AAB packaging all passed.
- Release warnings are currently non-blocking deprecations/opt-ins plus expected inability to strip several prebuilt youtubedl/FFmpeg native binaries; the packaged libraries were retained and exercised on arm64.

### Device acceptance
- Current-version device: OPPO `PHY110`, Android 16 / API 36, arm64-v8a. The validation-signed Release APK installed as an in-place upgrade over the existing app, launched successfully, and retained the existing three-video Room dataset plus encrypted DeepSeek and Doubao provider configuration.
- On API 36, Home, Settings, Review, and an existing bilingual transcript opened from persisted media. The transcript displayed real English/Chinese cues; the Review card displayed its progress, favorite star, and study actions. The final post-clean `app-release-validation.apk` was then reinstalled and cold-launched successfully. Logcat contained no app fatal, Room migration error, R8 class/method failure, Hilt/Worker instantiation failure, or native-link failure.
- Minimum-version device: a clean Google APIs ARM64 Android 8.0 / API 26 emulator installed and launched the exact same validation-signed Release APK. Home, Settings, and empty-state Review rendered, navigation worked, and the final post-clean validation APK was reinstalled and cold-launched successfully. Logcat contained no fatal, Room, Hilt, WorkManager, or native-link failure. Compose's caught lookup of the optional newer `Layout.TextInclusionStrategy` API is a compatibility probe, not an application crash.
- Existing end-to-end product acceptance from 2026-07-22 remains valid for real YouTube download, media validation, ASR, translation, vocabulary persistence, transcript, review data, and Room foreign-key integrity. The M5 device pass specifically validated that those persisted results remain readable and usable after an R8 Release upgrade.

### Artifacts and size
- Unsigned universal Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`, 226,836,852 bytes, SHA-256 `262dbb9e7bb9ddfe982273f9952f37fd37b5749199718450ee3faf4a77b733f7`.
- Validation-only signed APK: `app/build/outputs/apk/release/app-release-validation.apk`, 226,912,111 bytes, SHA-256 `d4346c07c5c30639d8ab2b5f1a796f703ae372c2ec0f9b71fe030cd4ce4aa1f7`. `apksigner` verifies v2/v3 signing by the local Android debug certificate; do not publish this APK.
- Unsigned Release AAB: `app/build/outputs/bundle/release/app-release.aab`, 231,171,014 bytes, SHA-256 `7b214dee068125260dbacad780589f3079d0c2ecf74730ccfed12a6a04a3a498`.
- Bundletool device delivery for PHY110 totals 69,247,275 bytes: 55,342,016-byte arm64 split, 13,828,870-byte base master split, and 76,389-byte xxxhdpi split. This confirms ABI/density splitting reduces delivery substantially versus the 226.8 MB universal APK.
- R8 mapping is `app/build/outputs/mapping/release/mapping.txt`; the Release lint report is `app/build/reports/lint-results-release.html`; the SBOM is `sbom/sublingo-release.cdx.json`.

## Latest product and UI update (2026-07-23)

### Branding and startup
- The Android launcher/round icon now uses the supplied SubLingo artwork. The shared `SubLingoLogo` renders the updated transparent background asset behind the wordmark and scales that background by 110% so it extends farther beyond the text.
- The Android launch window and Android 12+ splash screen use the icon-matched cream color `#FEF5E7` instead of white.

### Review favorites, rendering, and difficulty control
- `ReviewCardEntity` now persists `isFavorite` through Room schema 13. Each study card has a translucent pale-yellow star action in its top-right progress row; the `completed / total` counter is on the left.
- Review scope and word-book filters include a dedicated `我的收藏` collection. Unfavoriting the current card while reviewing favorites removes it from that active session immediately.
- The review surface pre-composes the next complete study card behind the current one with near-zero alpha. Its text, face layout, and scroll containers are already measured before the current card exits, reducing card-switch stutter.
- The expanded CEFR difficulty slider uses a custom Canvas track with a wider 16dp background and an additional 24dp horizontal drawing span, aligned to the surrounding selector column while retaining the discrete level ticks.

### Home processing cards and local import
- Waiting-download cards and processing video placeholders grow from the top-left toward the bottom-right. The card surface expands first, followed by a separate content fade-in.
- Local imports extract an embedded title, duration, and a representative video frame with `MediaMetadataRetriever`; the generated JPEG is persisted beside the private media copy and used as the video cover.
- The old successful-import message `本地视频已导入，正在生成学习内容` is no longer rendered above the local-import button. The 54dp draggable import button now starts closer to the bottom-navigation capsule while remaining constrained to the Home surface.

### Transcript and player refinements
- The transcript player frame and both directions of the immersive/transcript transition now target 16:9. Media3 remains `RESIZE_MODE_FIT`, so only the container changes shape and video pixels are never stretched.
- Immersive-to-transcript animation geometry is measured from the destination preview's actual player bounds. The preview and destination share the same 16:9 frame, header spacing, and endpoint, preventing the earlier low/offset landing position.
- Playback speed remains above the transcript timeline so it does not intercept scrubber dragging. Shared seek feedback holds one action state, so a left-side 10-second rewind cannot briefly reveal the right-side fast-forward icon.
- Translation prompting uses contextual semantic Chinese segmentation first and maps each Chinese segment to exact English source words/phrases. Deterministic repair still validates exact source/target spans before storage and transcript highlighting.
- The transcript header no longer exposes `复制`, `TXT`, or `MD`. The `英文` / `中文` / `双语` mode group is right-aligned, and the immersive-to-transcript preview uses the same simplified layout so removed controls do not flash during the transition.

### Providers, notifications, and settings cleanup
- Xiaomi MiMo is no longer offered as a built-in LLM or STT preset. Legacy Xiaomi preset IDs fall back to custom-provider handling; DeepSeek, Doubao, and custom LLM plus Doubao/custom STT remain available.
- A successful full video pipeline posts a `视频处理完成` notification only after vocabulary persistence and the Room job reach `SUCCEEDED / 100`. The body includes the video title when available, and tapping opens SubLingo.
- Completion notifications use a dedicated default-importance channel and a stable per-video tag/ID, so duplicate executions replace rather than stack notifications. Android 13+ permission denial or globally disabled notifications are handled without failing the completed WorkManager job. Vocabulary-only background refreshes do not emit a full-pipeline notification.
- Settings → 下载偏好 now contains only Cookie management; the obsolete `首选通道` row and download-channel dialog were removed.
- The `打开技术验证` footer, validation navigation route, and validation Compose page/ViewModel files were removed from the product UI.
- Current verification after these changes: `:app:testDebugUnitTest :app:assembleDebug` passes. The generated debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Navigation layering and DeepSeek full-flow acceptance (2026-07-22)

- The bottom navigation background is now an independent lowest layer. Route content and review cards render above it, while the navigation capsule remains the highest layer. The background begins at the capsule top instead of extending into page content.
- The review content reserves explicit bottom-navigation space and the study card is slightly shorter, so its bottom edge and shadow are no longer covered by the navigation surface.
- DeepSeek translation uses provider-aware batches capped at 12 cues / an 800-token input budget. Xiaomi MiMo remains isolated to single-cue batches because of its slower and less consistent translation responses.
- DeepSeek's translations were semantically correct, but strict word-map validation previously rejected paraphrased English surfaces, incomplete Chinese function-word coverage, repeated Chinese surfaces, and overlapping mappings. `TranslationWordMapRepair` now keeps only exact source/translation surfaces, resolves their real non-overlapping Chinese ranges, preserves occurrence order, and conservatively maps uncovered Chinese spans to the full source sentence.
- Local word-map repair now runs immediately after every batch response. The app only sends targeted follow-up requests when an index is structurally missing or still invalid after deterministic repair, eliminating the earlier batch → group → single-cue retry amplification.
- Pixel 10 Pro acceptance used `https://www.youtube.com/watch?v=8Hx2yvWSgs0` with DeepSeek `deepseek-chat`. Download selected YouTube format 137 (1080p) and validated the cached 101.52 MiB video plus 9.1 MiB audio; four MiMo ASR chunks completed once each and produced 113 English cues.
- Translation completed 10/10 batches with 113 Chinese cues, mappings for all 113 sequences, and 1,731 stored word alignments. After the repair was moved before retry, each remaining batch completed with one DeepSeek request; observed HTTP response time was generally about 84–1,683 ms with no timeout.
- Vocabulary extraction completed two DeepSeek batches once each, extracted 212 selected learning entries, and persisted 1,075 contextual occurrences across 471 lexemes. The final job state is `VOCABULARY / SUCCEEDED / 100`; Room foreign-key checking is clean, with no app fatal, ANR, HTTP failure, pipeline failure, WorkManager retry, or retry loop in the acceptance log.
- `SubtitleToolsTest` covers paraphrased English removal, conservative Chinese-gap repair, out-of-order pairs, repeated Chinese surfaces, and overlapping mappings. All 114 debug unit tests and `assembleDebug` pass after these changes.

## Transcript complete word-map display repair (2026-07-23)

- Root cause of the apparent missing bilingual mappings was not absent Room data. The tested Samsung transcript contained 1,731 translation alignments across all 113 cues, but 244 uncovered Chinese fragments in 87 cues had been conservatively stored against the complete English sentence.
- `TranscriptWordAligner` intentionally gives longer English surfaces priority to avoid overlapping highlights. Consequently, a single `complete sentence → one Chinese particle` fallback occupied every English token and hid all valid local word pairs in that cue; the reported 00:27 sentence had 24 valid local pairs hidden by one `complete sentence → 一些` row.
- New translation repairs now attach an uncovered Chinese fragment to its nearest exact local English pair. Only a response with no usable local pair at all retains the complete-sentence fallback.
- Existing videos are repaired at display time without retranscription, retranslation, database deletion, or another LLM request. Legacy complete-sentence fallback rows are re-anchored to the nearest persisted local pair by ordinal before transcript highlights are assembled.
- Focused tests cover legacy fallback localization and the new local-anchor behavior. Pixel 10 Pro acceptance confirmed words render as independent click targets again; selecting an English word highlights its localized Chinese counterpart rather than highlighting the entire English sentence for one Chinese fragment.
- Layout Inspector measured the visible `SoftBottomBar` capsule at 72dp high. Its actual top inset also includes the component's 10dp outer bottom padding and the host's 12dp bottom padding, for a shared 94dp boundary. Both the separate navigation background and the `NavHost` bottom spacer now use this value, so route content ends exactly at the capsule top instead of being clipped 16dp early; scrollable screens retain their own bottom content padding for the final item.

## Authoritative current snapshot (2026-07-21)

### Product and architecture
- SubLingo remains a single-module Android app built with Kotlin, Jetpack Compose, Room, Hilt, WorkManager, Media3, OkHttp/Retrofit, and `youtubedl-android`.
- Room is the source of truth for videos, processing jobs, subtitles, vocabulary occurrences, review cards/logs, provider profiles, and LLM batch cache metadata.
- Provider API keys and download cookies are encrypted with the Android Keystore-backed `SecretStore`; Room stores aliases/configuration only and no plaintext secrets.
- The implemented end-to-end path is URL download or local import → validated local media → platform subtitle discovery or STT fallback → Chinese translation → word-first vocabulary extraction → bilingual transcript → Room-backed review loop.

### Home, task, and video-card experience
- The new-task URL control has a fixed height across vendor fonts and empty/non-empty states. Its `OutlinedTextField` border is transparent in focused, unfocused, disabled, and error states, so the pale-purple capsule is the only visible input background.
- A translucent circular `×` appears inside a non-empty URL field and clears the entered address. The former text download action is now a compact 52dp yellow circular `＋` button.
- A Room video placeholder is inserted before download execution and appears immediately in Recent Videos. It keeps the same stable `videoId` and grid position while downloading.
- Active video cards show a rotating top-right progress indicator. Thumbnail and real title updates fill the same placeholder with animated content after media validation; the indicator becomes the ready check only after the complete subtitle/translation/vocabulary pipeline finishes.
- Processing-task content expands/fades into the Home screen rather than appearing abruptly. Existing cancellation, login recovery, retry, queue position, selection, and deletion behavior remains available.

### Download scheduling, cancellation, and recovery
- Download work is persisted before enqueue, constrained on connectivity, promoted to foreground work, and retried with exponential backoff for transient failures.
- `DownloadWorker` no longer swallows `CancellationException` inside per-format `runCatching`. Explicit cancellation, WorkManager stop, or unique-work replacement exits the attempt loop immediately and destroys active yt-dlp processes.
- Progress and foreground-notification updates now stop when the worker/coroutine is no longer active, preventing updates against a non-RUNNING `WorkSpec`.
- Download requests already use yt-dlp `--continue` and stable per-video task directories. Partial files survive cancellation/background retry; full strategy-isolated resumable storage remains a future enhancement because fallback strategy changes can still clear attempt artifacts.
- Unique download enqueue is awaited so scheduling failures are observable. The WorkManager executor currently uses three threads to avoid a new download being starved indefinitely behind two long cloud/subtitle stages; do not rely on older two-thread historical notes.
- YouTube retains direct progressive MP4 first, client-aware fallback, Cookie/login recovery, 16KB-page compatibility, and HLS as the final compatibility route. Bilibili retains split video/audio download and Android MediaMuxer fallback.

### YouTube subtitle parsing and playback alignment
- Subtitle parsing now removes YouTube WebVTT inline timestamps, `c` style/color tags, speaker/language/font/ruby tags, residual markup, common HTML entities, BOM/zero-width characters, and bidirectional control characters.
- Blank/duplicate lines inside a cue are removed while preserving each cue's original start/end timestamps. This fixes unexplained visible symbols and duplicate rolling-caption text without applying an unsafe global timing offset.
- Any remaining constant or local audio/subtitle drift must be diagnosed from a concrete video's media timeline and VTT cues; no universal YouTube offset is currently hard-coded.

### Vocabulary pipeline v12: word-first and lower request count
- `VocabularyPipelineContract.VERSION` is 12 and refresh work uses the `v12-word-first` identity. Current cache phases are `EXTRACTION_V12_WORD_FIRST`, `PHRASE_AUDIT_V12`, and `ALIGNMENT_V12_FIXED_PHRASES`.
- The extraction prompt now prioritizes useful standalone English content words, accurate dictionary lemmas, and complete word coverage. Multi-word output is optional and limited to unmistakable phrasal verbs, fixed collocations, or idioms.
- Routine compositional chunks, arbitrary verb-object spans, discourse fillers, grammatical chunks, and whole clauses are no longer requested as primary learning items. A phrase must not suppress useful constituent words.
- The dedicated phrase-coverage audit is disabled in the active worker path, eliminating its many 24-cue follow-up requests. The first extraction request may still return a small number of genuine fixed expressions.
- Bulk alignment repair no longer sends every locally recovered `WORD` back to the LLM. It repairs only non-word fixed expressions missing a valid Chinese span. This substantially reduces latency and API usage.
- General extraction remains whole-transcript-first within the character budget, recursively splitting only oversized or invalid/truncated batches. Valid phase responses retain the existing Room cache and per-video in-process execution lock.
- Review study sessions explicitly show only single-word lemmas; phrases may remain in persisted transcript/word-book data but do not enter the swipe-card queue.

### Transcript behavior and animation
- Transcript learning highlights require an exact contextual `translationZh` substring that exists in the matching Chinese cue. Invalid or missing Chinese mappings are not rendered as English-only playback highlights, though the vocabulary/review data remains stored.
- For duplicate lexeme occurrences within a cue, the UI prefers a validated Chinese mapping and the most complete contextual span rather than blindly retaining the first row.
- A followed next cue may be positioned before its timestamp, but it remains visually idle while `positionMs < cueStartMs`. The former prelight behavior was removed because it caused `highlight → off → highlight again` flicker.
- Active sentence cards animate accent color, play-button colors, elevation, and subtle scale. English/Chinese token backgrounds animate between selected and transparent states without changing token metrics or causing reflow.
- Existing sentence replay boundaries, stable follow position, bilingual token linking, speed menu, native scrubber, and embedded Media3 playback remain active.

### Review-card layout and interaction
- The study progress strip is integrated into the top of the white card as a compact segmented bar plus `completed / total`; it no longer consumes separate vertical space above the card.
- The bottom card row contains three equal-height 48dp actions: translucent red `×`, translucent purple `点击翻面`/`查看正面`, and translucent green `✓`. The prior undo control and separate front/back flip labels were removed from the card UI.
- Each new card enters with a short fade and 0.97→1.0 scale animation. Swipe translation/rotation/alpha behavior remains intact.
- The elevated card is inset inside a larger unclipped transform stage with additional horizontal/vertical shadow space, reducing shadow clipping during press, drag, rotation, flip, and spring return.
- Card width was increased to align visually with the difficulty/scope rounded panel while retaining shadow-safe margins. The front lemma is capped at a smaller dynamic size for narrow screens.
- The back lemma receives full width and no longer competes with an overlaid pronunciation control. Below it, the phonetic appears in a yellow capsule with the pronunciation button immediately beside it; the former part-of-speech capsule in that location was removed.
- Card faces scroll internally for long definitions/examples. Source-video jumping and bilingual contextual highlights remain available on the back.

### Pronunciation compatibility
- Dictionary audio remains the first pronunciation source; `MediaPlayer` errors fall back to Android TTS.
- Android 11+ TTS service package visibility is declared through an `android.intent.action.TTS_SERVICE` manifest query, improving Google/vendor engine discovery on OPPO and similar ROMs.
- TTS initialization retries after a failed/not-ready first attempt, queues the selected word, prefers `Locale.US`, and falls back to generic English when US voice data is unsupported.
- Devices still require an installed/enabled English TTS engine or working dictionary audio, and pronunciation uses media volume rather than ringtone volume.

### Settings and secret-save feedback
- LLM and STT dialogs retain provider presets, custom HTTPS configuration, encrypted-key reuse boundaries, and official API-key links.
- After `加密保存`, the plaintext field is cleared and a configured provider now displays `API Key 已加密保存` in the API-key input. The secret is never decrypted back into the UI.
- Current built-in presets exclude Xiaomi MiMo. The settings footer no longer links to a technical-validation page, and Download Preferences no longer exposes a selectable preferred channel.

### Home/player transition refinements (latest)
- Download placeholders no longer show the purple center play glyph, and processing cards are disabled for playback until their complete pipeline is ready; selection mode remains usable. Ready cards continue to open the player, whose existing prepare/seek/play path starts immediately.
- The URL action is restored to the yellow `下载` text capsule. The separate local-import FAB is now the smaller 54dp circular yellow `＋`, and its persisted drag offset lets the user reposition it within the Home surface.
- In portrait immersive playback, the video is constrained to a top 16:9 region and subtitles render below it instead of overlaying the picture. Landscape retains overlay subtitles.
- Opening Transcript from the button animates the immersive video upward/scaled toward the transcript video position before navigation; the transcript route then fades in. An upward drag on portrait video interactively drives the same progress and commits after the threshold, otherwise springing back.

### Current build and device status
- Recent compile regressions were corrected: Compose runtime animation imports, `TextUnit` sizing, `tween`, and review-card single-word filtering no longer reference unavailable APIs/fields.
- IDE diagnostics report no errors in the most recently edited Home, settings, transcript, review, download, vocabulary, subtitle-parser, manifest, and provider files.
- Real-device focus includes OPPO PHY110/ColorOS behavior: fixed input height, narrow-screen review layout, TTS discovery/retry, and background-work restrictions. For reliable long processing, allow notifications/background activity and disable aggressive battery optimization during acceptance testing.
- The latest `:app:testDebugUnitTest :app:assembleDebug` run passes after the completion-notification and settings/transcript cleanup changes. The current Debug APK is available at `app/build/outputs/apk/debug/app-debug.apk`.

## Current follow-up work
1. Complete OPPO PHY110 visual/interaction acceptance for Home input, placeholder growth animation, local-import cover/button placement, review favorites/pre-rendering, transcript header alignment, and pronunciation.
2. Capture pipeline-specific Logcat (`DownloadWorker`, subtitle workers, `VocabWorker`, WorkManager) rather than SurfaceFlinger/window lifecycle noise when diagnosing background failures.
3. Measure v12 LLM request counts on short and long videos, verifying phrase audit is absent, fixed-phrase alignment is bounded, cache hits work, and duplicate workers do not repeat the same phase.
4. Validate YouTube platform subtitles against several real videos for residual drift after VTT cleanup; add per-source timestamp correction only with reproducible evidence.
5. If robust cross-strategy download resume is required, isolate each yt-dlp format attempt in a stable subdirectory instead of clearing the whole shared attempt directory.

## In-app YouTube login fallback (2026-07-19)
- Abandoned the self-hosted Cobalt direction. The download flow remains local yt-dlp first and only asks for user interaction after all anonymous YouTube clients fail with bot verification / 403.
- YouTube authentication failures now persist as `ProcessingState.WAITING_FOR_USER` with `YOUTUBE_LOGIN_REQUIRED` instead of a generic failed job. WorkManager also returns typed login-required output for the legacy download screen.
- The main Home download flow automatically opens a full-screen in-app YouTube login browser for that waiting job. The task card retains a `登录 YouTube 并继续` recovery action if the user cancels the first prompt.
- Login completion is based on real authenticated YouTube cookie markers (`SAPISID`, secure PAPISID variants, or `LOGIN_INFO`). Visitor-only cookies such as `YSC`, `PREF`, and `VISITOR_INFO1_LIVE` cannot falsely complete the flow.
- After login, cookies from the YouTube WebView session are normalized, AES-GCM encrypted through the existing Android Keystore-backed `SecretStore`, the isolated WebView cookie jar is cleared, and the exact original URL is automatically re-enqueued with `ExistingWorkPolicy.REPLACE`.
- The browser disables file/content access, only permits HTTP(S) navigation, explains local cookie handling to the user, and never writes cookie values to Room or logs.
- Device acceptance on the 16 KB emulator used `https://www.youtube.com/watch?v=c347oYQO57A`: all three anonymous clients failed with the real YouTube bot challenge/403; Room transitioned to the waiting state; Home automatically displayed the full-screen login browser; and the browser successfully loaded `accounts.google.com`. A real account was intentionally not entered in the test environment, so authenticated-cookie extraction and automatic retry are covered by policy unit tests rather than a fabricated device login.
- Automated acceptance: `:app:testDebugUnitTest :app:assembleDebug` passes and the resulting APK installs successfully on `emulator-5554`.

## Task controls and playback polish (2026-07-19)
- Every Home `正在处理` card now has a compact top-right `×` action. Download jobs cancel their unique WorkManager work and clean partial download artifacts; subtitle/vocabulary jobs cancel both unique pipeline chains and persist `CANCELLED` in Room while keeping an already downloaded video.
- YouTube login-required cards now place a refresh action beside `登录 YouTube 并继续`. Refresh re-reads the Keystore-encrypted cookie, accepts both browser-header and Netscape cookie formats, retries the exact source URL when an authenticated YouTube session exists, and shows a local status when it does not.
- The immersive player now treats opening a video as an explicit play action: after preparing and seeking to the saved/deep-linked position it immediately calls play, while retaining the existing three-second control fade.
- The transcript player now has timed fading controls even while paused. Tapping the video restores them; the playback-speed control sits above the timeline on its right and cycles `0.75x → 1x → 1.25x → 1.5x → 2x`.
- Transcript sentence play actions now synchronously update the UI position, explicitly seek media item 0 to the cue's exact `startMs`, prepare if idle, and resume playback. This fixes the previous behavior where some taps restarted from the beginning.
- Emulator acceptance confirmed the Home waiting-login card shows the new `×` and refresh controls, the immersive player begins rendering/advancing immediately, the transcript exposes `1.0x` at the timeline's upper-right, controls fade after three seconds, and selecting the `00:07` cue jumps the rendered video to that point rather than zero.

## Local import FAB and transcript sentence replay (2026-07-19)
- Home now exposes a yellow elevated `+` button immediately above the bottom navigation's right side. It opens Android's video document picker and runs the full local import path directly from Home: private copy, minimum-size/video-track/duration validation, atomic `.part` commit, Room video/job creation, and subtitle pipeline enqueue.
- The import button reserves bottom list padding so it does not cover the last recent-video/task content and shows a compact result status above the button.
- Transcript row play is now a bounded sentence replay action. It pauses the current stream, seeks media item 0 to the selected cue's exact `startMs`, waits until playback actually enters that cue range, and automatically pauses at `endMs` using a 40 ms position check. Repeated taps restart the same sentence instead of resuming arbitrary playback or starting at zero.
- Manual player controls and timeline seeks clear the sentence boundary, so ordinary continuous playback remains available.
- Removed the full-width translucent black background behind the transcript speed/timeline/time labels. The controls now float directly over the video while retaining readable individual text and track colors.
- Unit coverage verifies the sentence end guard cannot pause from a stale pre-seek position and pauses exactly at/after the cue end only after entering the target range. Build, test, APK installation and Home FAB visual acceptance pass on `emulator-5554`.

## Word-book deletion and Bilibili DASH download fix (2026-07-19)
- The word book now exposes a visible red `删除` management action beside search/layout/add. Delete mode supports selecting multiple list or grid cards, highlights selected cards, changes the action to `删除(n)`, and requires confirmation before removal.
- Deleting a lexeme uses the existing Room foreign-key cascade, so its review card, review logs, senses and video occurrences are removed together. The active study session and undo history are also pruned immediately to avoid retaining deleted cards in memory.
- Root cause of `BV1Tf3JesEtJ: Requested format is not available`: on Bilibili, normal quality is commonly exposed as separate DASH video/audio tracks, while the 16 KB compatibility selector required one pre-muxed file. The Android FFmpeg binary cannot be used on the device, so both fallback attempts asked for a format Bilibili did not publish.
- On 16 KB devices, Bilibili now uses a dedicated two-stage attempt: download an Android-compatible MP4/H.264 video track, download an M4A/AAC audio track, and combine them through Android `MediaMuxer`. The existing single-file request remains a secondary fallback.
- Real-device acceptance used the reported URL `https://www.bilibili.com/video/BV1Tf3JesEtJ`: yt-dlp selected video format `30080`, produced `video.mp4` (91.10 MiB) and `audio.m4a` (6.9 MiB), native muxing produced `media.merged.mp4` (98 MiB), WorkManager returned SUCCESS, and the subtitle pipeline continued. `ffprobe` confirmed H.264 video plus AAC audio.
- The `Requested important-while-foreground flag ... is ignored` line is harmless Android scheduler noise and was not involved in the failure.

## Complete recent-video library display (2026-07-19)
- Fixed Home silently truncating `recentVideos` with `take(2)`. All videos with a valid local media path now remain visible, ordered by Room's newest-first query.
- Replaced the single two-card row with an expandable two-column layout built from successive rows, so the third and later videos continue below without shrinking every card.
- The header now shows the total local-video count, and selection/deletion operates on the complete visible library instead of only the two most recent entries.

## Efficient resumable LLM pipeline v10 (2026-07-19)
- Vocabulary contract upgraded to v10. Translation remains the existing token-budget batching with targeted missing-index retry; the optimization targets the significantly more expensive vocabulary path.
- Added an in-process per-video mutex around `VocabWorker`. Main-pipeline and transcript-refresh workers can no longer issue vocabulary requests concurrently; a waiting duplicate exits immediately once it observes v10 completed.
- Transcript opening no longer enqueues vocabulary refresh while the same video's Room job is already pending/running at `VOCABULARY`.
- Phrase audit no longer requires one empty output object for every cue. It accepts only cues containing found phrases, eliminating output truncation caused by hundreds of `{items:[]}` rows.
- Phrase audit uses deterministic batches of 24 cues and a local risk planner. Strong constructions (`Thank you`, `by the way`, `do/did/does + verb`, `a lot`, common phrasal-verb particles) are always audited; already-covered generic cues are skipped unless they still carry a strong signal.
- Alignment repair now targets only multi-word phrases/chunks whose first-pass Chinese span is missing. Local fallback `WORD` items use their standard dictionary meaning instead of creating hundreds of additional LLM mappings.
- Added Room 9 `VocabularyLlmBatchEntity` with a non-destructive 8→9 migration. Validated extraction, phrase-audit and alignment responses are cached by video/version/phase/model/input SHA-256; restored or retried work reuses successful responses after parsing them again.
- Invalid or truncated responses are never marked successful in the cache. Failed records retain attempt/error diagnostics and may be retried; API keys and prompts are not stored as secrets, and no API key enters Room.
- Existing device data migrated successfully to schema 9 with zero foreign-key violations. Unit/build/install acceptance passes.

## Current focus
- M1 implementation is feature-complete in code: URL download/local document import → Room persistence → Media3 playback → playback-position restore → Room-backed bilingual subtitle overlay.
- M2 implementation is feature-complete in code: platform-English-subtitle priority → audio chunking fallback → resumable Doubao ASR chunks → dynamically batched aligned translation.
- M3 implementation is feature-complete and device-verified: unlimited whole-transcript vocabulary extraction → bundled/offline ECDICT lookup → per-cue LLM Chinese-span alignment → Room-backed bilingual transcript highlighting.
- `youtubedl-android` with its matching FFmpeg package is the primary download engine; output is validated before being treated as complete.
- The home and immersive player UIs now follow the supplied Stitch blueprints.
- M3 regression/device acceptance is complete and M4's Room-backed review loop is now implemented and emulator-verified.
- FFmpeg subtitle-burning remains deferred because learning subtitles will be rendered as a Compose overlay.

## Completed so far
- Read and aligned the implementation plan, design spec, and visual system.  
  读取并对齐实施计划、设计规范和视觉系统。
- Switched into implementation mode.  
  已切换到实现模式。
- Repaired the Android project skeleton.  
  已修复 Android 工程骨架。
- Added the Gradle root configuration and app module configuration.  
  已添加 Gradle 根配置和 app 模块配置。
- Added the Android manifest, `MainActivity`, and `SubLingoApplication`.  
  已添加 Android Manifest、`MainActivity` 和 `SubLingoApplication`。
- Added the app theme and core brand colors.  
  已添加应用主题和核心品牌色。
- Added base domain enums for processing stage/state.  
  已添加流程阶段与状态的基础领域枚举。
- Added provider interfaces for translation, STT, dictionary, and vocabulary.  
  已添加翻译、语音识别、词典和生词提取的 Provider 接口。
- Added Room entity drafts for videos, processing jobs, subtitles, and provider profiles.  
  已添加视频、处理任务、字幕和供应商配置的 Room 实体草图。
- Added Room DAO and database skeletons.  
  已添加 Room DAO 和数据库骨架。
- Added Hilt app module for database provisioning.  
  已添加用于数据库注入的 Hilt 模块。
- Added repository interfaces and Room repository implementations.  
  已添加仓库接口和基于 Room 的仓库实现。
- Added WorkManager worker skeletons for the processing pipeline.  
  已添加处理流水线的 WorkManager Worker 骨架。
- Added basic navigation host and a soft-play home screen.  
  已添加基础导航宿主和软胶囊风格首页。
- Added a reusable status card UI component.  
  已添加可复用的状态卡片组件。
- Ran lint checks on the edited app sources; no new lint errors were reported.  
  已对修改过的应用源码运行 lint 检查，未发现新的 lint 错误。
- Added M-1 validation model and provider interfaces for download, subtitle, ASR, and translation alignment probes.  
  已添加 M-1 验证模型以及下载、字幕、ASR、翻译对齐探针接口。
- Added fake validation providers, Hilt bindings, and validation use cases.  
  已添加假验证 Provider、Hilt 绑定和验证用例。
- Added in-memory secret store implementation.  
  已添加内存版密钥存储实现。
- Added minimal placeholder routes for library, download, settings, and validation.  
  已添加库、下载、设置和验证的最小占位路由。
- Added a validation screen and ViewModel to exercise the M-1 probe flow.  
  已添加验证页面和 ViewModel 来驱动 M-1 探针流程。
- Added a pill-style button and refined the home screen composition.  
  已添加胶囊按钮并优化首页组合方式。
- Fixed SecretStore to use a consistent suspend-based API.  
  已修正 SecretStore 为一致的挂起函数 API。
- Updated the app shell so the app launches through a single Compose root.  
  已更新应用壳层，确保通过单一 Compose 根节点启动。
- Added heuristic validation providers and probe sample data for a more realistic M-1 run.  
  已添加启发式验证 Provider 和探针样例数据，使 M-1 运行更接近真实验证。
- Added a clickable home entry into the validation page.  
  已添加从首页进入验证页的可点击入口。
- Added real-client skeleton interfaces for chat completion, Doubao ASR, and video inspection.  
  已添加聊天补全、豆包 ASR 和视频检查的真实客户端骨架接口。
- Added fake remote client implementations and a key setup hint object.  
  已添加远程客户端 Fake 实现和 key 配置提示对象。
- Added provider model, type converters, preset registry, and settings screen with key placement guidance.  
  已添加 Provider 模型、类型转换器、预设注册表和包含 key 配置指引的设置页。
- Added a home shortcut to the settings page and confirmed Gradle sync naming.  
  已添加首页到设置页的入口，并确认 Gradle 同步按钮名称。
- Added a validation summary and streamlined validation flow for manual testing.  
  已添加验证总览和更顺畅的验证流程，便于手动测试。
- Added a failure demo path and richer validation state for final-like checks.  
  已添加失败演示路径和更丰富的验证状态，便于接近最终形态的检查。
- Verified M-1 validation flow through runnable UI, summary cards, logs, and demo scenarios.  
  已通过可运行 UI、摘要卡片、日志和演示场景验证 M-1 流程。
- Added M1 task planning/viewmodel/screen skeleton for download/import flows.  
  已添加 M1 下载/导入流程的任务规划、ViewModel 和页面骨架。
- Added a three-entry home screen and download page route.  
  已添加三入口首页和下载页路由。
- Added pill-shaped input fields for download and settings.  
  已添加下载页和设置页的胶囊输入框。
- Added a media library skeleton backed by fake data and a route to open player placeholders.  
  已添加基于假数据的媒体库骨架以及进入播放器占位页的路由。
- Added player shell and navigation arguments for the M1 playback foundation.  
  已添加播放器外壳和导航参数，构建 M1 播放基础。
- Fixed duplicate model definitions and stabilized compilation.  
  已修复重复模型定义并稳定编译。
- Added download task progress, task result feedback, and refresh action.  
  已添加下载任务进度、任务结果反馈和刷新动作。
- Added soft scaffold and soft card components to match blueprint styling.  
  已添加软壳层与软卡片组件以贴近 blueprint 风格。
- Updated home and player screens to use the softer final-like layout language.  
  已更新首页和播放器页，采用更柔和的最终风格布局语言。
- Added library item cards that can open the player shell.  
  已添加可打开播放器外壳的媒体库卡片。
- Added a blueprint-like home layout with task input and processing sections.  
  已添加 blueprint 风格首页布局，包含任务输入和处理中区域。
- Wired the home task input to task creation state.  
  已将首页任务输入接入任务创建状态。
- Upgraded fake task flow to behave like a progressing download state machine.  
  已升级 fake 任务流程，使其更像可推进的下载状态机。
- Made the home page create task feedback immediately visible on the same screen.  
  已让首页创建任务后的反馈在同一页面立即可见。
- Tuned the download planner to detect Bilibili / YouTube URLs more clearly.  
  已调整下载规划器，使其更清晰识别 Bilibili / YouTube 链接。
- Added a real network layer with OkHttp / Retrofit and Bilibili API service models.  
  已添加真实网络层、OkHttp / Retrofit 和 Bilibili API 服务模型。
- Added a Bilibili download strategy that resolves view/playurl data.  
  已添加可解析 view/playurl 数据的 Bilibili 下载策略。
- Connected the download task flow to resolve metadata and write downloaded videos into Room.  
  已将下载任务流接到元数据解析并写入 Room 视频表。
- Added a file download manager that saves downloaded media under `Movies/SubLingo`.  
  已添加文件下载管理器，将下载的媒体保存到 `Movies/SubLingo`。
- Switched the primary download implementation to `youtubedl-android` per `IMPLEMENTATION_PLAN.md`.  
  已按 `IMPLEMENTATION_PLAN.md` 切换主下载实现为 `youtubedl-android`。
- Added `youtubedl-android`, `ffmpeg`, and `aria2c` dependencies and initialized the library at app startup.  
  已添加 `youtubedl-android`、`ffmpeg`、`aria2c` 依赖并在应用启动时初始化库。
- Replaced the custom Bilibili direct-download flow with a WorkManager-driven `youtubedl-android` task flow.  
  已将自研 Bilibili 直链下载流程替换为由 WorkManager 驱动的 `youtubedl-android` 任务流。
- Verified that `youtubedl-android` can successfully download a Bilibili video in pure-download mode.  
  已验证 `youtubedl-android` 在纯下载模式下可以成功下载 Bilibili 视频。
- Started integrating a dedicated media playback path with `androidx.media3` so downloaded local files can play inside the app.  
  已开始集成独立的媒体播放路径，使用 `androidx.media3` 在 app 内播放本地下载文件。
- Switched the library screen to read from Room-backed video data instead of only fake placeholders.  
  已将媒体库页面切换为读取 Room 视频数据，而不仅是假的占位内容。
- Wired the home screen to surface recent downloaded videos from Room.  
  已将首页接入 Room 中的最近下载视频展示。
- Added and then removed a blocking ffmpeg-kit dependency after confirming it was not available from the current Maven sources.  
  已在确认当前 Maven 源不可用后添加又移除了会阻塞构建的 ffmpeg-kit 依赖。

## Latest M1 implementation (2026-07-17)
- Rebuilt the home screen from Stitch `_4`: URL download pill, conditional real processing jobs, completed-only recent videos, and a single blueprint-style bottom navigation bar.  
  已按 Stitch `_4` 重构首页：下载输入胶囊、仅在真实任务存在时显示处理区、仅展示已完成视频，并统一为一个参考稿风格底栏。
- Connected the home task cards to Room `ProcessingJob` state and fixed duplicate random job rows that left completed downloads stuck at 5%.  
  已将首页任务卡接入 Room 任务状态，并修复随机任务 ID 导致已完成下载仍卡在 5% 的问题。
- Added real yt-dlp progress propagation to WorkManager and Room.  
  已将 yt-dlp 实时进度同步到 WorkManager 与 Room。
- Added the matching youtubedl-android FFmpeg package, per-task output directories, MP4/H.264-oriented format selection, and deterministic artifact lookup.  
  已加入匹配版本的 FFmpeg 包、独立任务输出目录、偏向 MP4/H.264 的格式选择及确定性的产物定位。
- Validate downloaded artifacts by extension, size, duration, and presence of a video track before persisting success.  
  下载完成后会按扩展名、大小、时长和视频轨道进行校验，只有有效媒体才会入库为成功。
- Persist valid local file paths, file sizes, duration, extracted local thumbnails, and yt-dlp `info.json` original titles in Room.  
  已将有效本地路径、文件大小、时长、本地提取封面及 yt-dlp `info.json` 原标题写入 Room。
- Show local thumbnails on recent-video cards and open completed videos directly in the Media3 player.  
  最近视频卡已显示本地缩略图，并可直接打开 Media3 播放本地视频。
- Added recent-video selection mode and deletion of the Room row, media file, and thumbnail.  
  已添加最近视频选择删除模式，可同时删除 Room 记录、媒体文件与缩略图。
- Rebuilt the player from Stitch `_1` as an immersive black experience with custom progress, seek, play/pause, speed, fullscreen, title, transcript entry, and playback error UI.  
  已按 Stitch `_1` 重构沉浸式黑色播放器，包含自定义进度、拖动、播放暂停、倍速、全屏、标题、逐字稿入口及播放错误提示。
- Player chrome fades after three seconds and can be restored by tapping the video. The app bottom bar is hidden on the player route and scaffold padding is removed.  
  播放控件会在 3 秒后渐隐并可点击视频唤醒；播放器路由隐藏应用底栏并移除外层边距。
- Added nonlinear player route transitions while retaining Media3's default `SurfaceView`; a manual `TextureView` experiment caused black video and was reverted.  
  已添加非线性播放器路由转场，同时保留 Media3 默认 `SurfaceView`；手动接管 `TextureView` 曾导致黑屏，现已回退。

## In progress
- Complete the remaining M1/M2 device acceptance matrix across more video sources and API/device versions; current Bilibili download, audio playback, Doubao transcription, DeepSeek translation, vocabulary, and transcript paths have been exercised on the active emulator.
- Continue M3 regression checks for ambiguous/repeated Chinese spans and the small set of LLM items that intentionally remain unaligned when no reliable subtitle substring exists.
- No real credentials are stored in the repository; provider secrets remain encrypted through Android Keystore/AES-GCM.

## Next steps
1. Execute the remaining M1 device matrix: local import, YouTube/Bilibili download, cancellation cleanup, playback restore, subtitle toggle/seek.
2. Execute the remaining M2 matrix: platform subtitle hit, 30-minute chunked ASR fallback, successful-chunk resume, and targeted translation retry.
3. Perform M3 regression on additional long videos and ambiguous bilingual translations.
4. Begin M4 review-loop implementation after the above regression matrix is green.

## M1/M2 completion update (2026-07-18)
- Replaced manual local-path entry with Android's document picker, atomic `.part` copy, video-track/duration validation, and Room persistence.
- Persisted and restored `lastPlayedPositionMs`; player exit/disposal now writes the current position.
- Added Room-backed English/Chinese subtitle observation, Compose bilingual overlay, independent language toggles, and cue seek.
- Added download cancellation cleanup and persisted `CANCELLED` state.
- Added Room constraints/DAOs for audio chunks and translation batches so completed work survives retries.
- Added SRT/VTT parsing, platform English subtitle discovery with yt-dlp, and platform-first short-circuiting.
- Added 15-minute audio remux chunks with `.part` atomic commit, start offsets, a five-hour limit, and flash/standard ASR selection policy.
- Added Doubao request headers/body handling, Base64 size guard, utterance timestamp ingestion, standard-mode polling, and per-chunk success/failure persistence.
- Added OpenAI-compatible chat completion client, dynamic translation batches, strict index validation, split retries, and final per-cue retries.
- Replaced the in-memory secret store with Android Keystore-backed AES-GCM storage; provider rows keep only `secretAlias`.
- Added unit tests for SRT/VTT parsing, 100-cue batch preservation, alignment validation, ASR mode selection, and successful-chunk skip behavior.
- Acceptance caveat: compilation and live provider/device acceptance are not marked passed yet because the managed execution environment blocked Gradle startup, and live cloud tests require the user's own credentials/account.
- Android Studio acceptance update: `assembleDebug` initially exposed a missing closing brace in `PlayerScreen.kt`; that syntax error and the `extractNativeLibs` manifest warning were corrected. Subsequent clean/unit-test/debug builds pass.
- Rebuilt the settings screen from the Stitch API/settings blueprint: grouped pastel cards, circular icons, provider/security/download/appearance rows, modal editors, visible configuration status, and route-aware bottom navigation selection.
- Split LLM, Doubao STT, and Cookie save actions; all secrets remain Keystore/AES-GCM protected. The download worker now reads the encrypted Cookie and passes it to yt-dlp when present.
- Doubao status clarification: the production path is wired end-to-end (profile → encrypted key → chunk worker → HTTP request → utterance timestamps → Room cues) and has successfully transcribed the active emulator sample with the user-configured provider account.
- Fixed the `YoutubeDL instance not initialized` download failure by introducing a synchronized, retryable shared runtime initializer for yt-dlp, FFmpeg, and aria2c. Both Application warm-up and download/subtitle workers use the same guard, and initialization errors are no longer swallowed.
- Moved subtitle-pipeline scheduling until after the downloaded media has passed validation and been persisted, preventing subtitle/audio workers from running after a failed download. Removed startup-time yt-dlp updating to avoid racing a live download.
- Fixed Android packaging for youtubedl-android: AGP's default in-APK JNI mode left `libpython.zip.so` absent from `applicationInfo.nativeLibraryDir`. Enabled `packaging.jniLibs.useLegacyPackaging`, which this library requires, and added an explicit runtime-component diagnostic.
- Fixed subtitle positions independently of player controls: portrait subtitles stay at the former controls-visible position near the bottom of the video, while landscape subtitles stay at the former controls-hidden bottom position.
- Fixed post-download subtitle processing visibility and recovery: processing jobs remain visible after `filePath` is persisted, provider configuration failures become actionable `WAITING_FOR_USER` states, downloaded videos expose a retry action, and Home reconciles any download-success record that missed subtitle scheduling during a process interruption.
- Fixed Bilibili audio extraction compatibility: audio chunking now falls back from Android `MediaExtractor` to the bundled FFmpeg, transcoding the first audio stream to 16 kHz mono AAC segments. Tightened yt-dlp format selection so every fallback requires both video and audio streams.
- Added recovery for previously downloaded video-only Bilibili artifacts: when both MediaExtractor and FFmpeg find no audio, the extraction worker downloads only the source's best audio stream (with encrypted Cookie when configured) and resumes chunking without downloading the full video again.
- Fixed separate-stream Bilibili downloads on 16 KB page-size devices: when yt-dlp leaves `media.f*.mp4` and `media.f*.m4a`, Android MediaMuxer now attempts an FFmpeg-free merge, while subtitle extraction preferentially consumes the companion M4A directly. Pipeline failures are now logged with the exact stage and stack trace.
- Replaced stale/broken WorkManager subtitle dependency chains on every enqueue, eliminating `Prerequisite ... doesn't exist`. Doubao failures now include safe HTTP/API status and service message diagnostics in Room/UI instead of an opaque Worker failure.
- Fixed silent playback for existing Bilibili separate-stream downloads: PlayerViewModel discovers the companion audio artifact and Media3 merges the video-only MP4 plus M4A at playback time with `MergingMediaSource`, independent of FFmpeg and physical mux success.
- Fixed disappearing audio chunks: DAO `@Insert(REPLACE)` updates deleted parent `ProcessingJob`/`SubtitleTrack` rows and cascaded deletion into `AudioChunk`/`SubtitleCue`. Replaced persistence writes with Room `@Upsert`, added post-write chunk verification, and stopped treating empty English tracks as usable subtitles.
- Added transcription recovery from disk: if an earlier cascade already removed `AudioChunk` rows, `TranscribeWorker` reconstructs them from valid `audio/<jobId>/chunk-*.m4a` files before contacting Doubao.
- Fixed DeepSeek translation timeouts: the shared HTTP client now allows 30s connect, 60s write, 120s response read, and 180s total call time. LLM HTTP and malformed-response errors now expose safe server details instead of nullable assertion failures.

## M3 implementation update (2026-07-18)
- Added Room entities and DAOs for `Lexeme`, `LexemeSense`, `WordOccurrence`, one-default-card-per-lexeme `ReviewCard`, and positive/negative `DictionaryCache`.
- Added a non-destructive Room 2→3 migration so upgrading to M3 preserves downloaded videos, subtitles, jobs, and provider profiles.
- Implemented local vocabulary candidate preprocessing, stop-word filtering, frequency ranking, basic normalization, LLM selection/lemmatization validation, and normalized-lemma deduplication.
- Replaced the empty `VocabWorker` with a persisted M3 stage after translation. The initial 30-item cap was subsequently removed; the current v6 flow extracts the full learning-word/phrase set, preserves per-cue contexts, and creates only one default review card per lexeme.
- Added Free Dictionary API lookup with IPA/audio/part-of-speech/English definitions, 30-day success/not-found caching, and 6-hour failure caching. Dictionary failure does not block context/card creation.
- Added batched Chinese definition translation through the configured OpenAI-compatible LLM; definition-translation failure degrades to English definitions without failing vocabulary generation.
- Added a Room-backed bilingual transcript screen with English/Chinese/bilingual modes and cue-to-player seeking. This historical implementation originally included copy/TXT/Markdown export; those header actions were removed on 2026-07-23.
- Added automatic M3 continuation for existing `TRANSLATION/SUCCEEDED` M2 videos and M3 unit tests for vocabulary normalization/deduplication plus transcript alignment.
- M3 acceptance status: implementation and automated acceptance are complete; later approved runs of `testDebugUnitTest assembleDebug` pass, and contextual alignment has been verified on the active emulator.

## Transcript learning UI update (2026-07-19)
- Rebuilt the transcript route from Stitch `_2`: a sticky 4:3 embedded Media3 player now sits above a Room-backed key-vocabulary strip and scrollable rounded bilingual transcript cards.
- Reused the separate-stream playback path on the transcript page, including companion M4A discovery and `MergingMediaSource`, so Bilibili video-only MP4 artifacts retain sound.
- Transcript rows now seek the embedded player directly, follow the active cue with a purple accent, and auto-scroll when playback moves beyond the visible rows. Copy and TXT/Markdown export were available at this historical checkpoint and were removed from the current UI on 2026-07-23.
- Added selectable English and Chinese token groups. The initial deterministic proportional heuristic was subsequently replaced by persisted per-cue LLM exact-substring mappings; selecting either side now highlights its contextual counterpart with the same color.
- Added observable Room vocabulary projection plus alignment tests for punctuation, missing languages, shared selection IDs, and bounded Chinese mappings.
- Acceptance status: source implementation is complete, and subsequent approved `testDebugUnitTest assembleDebug` runs pass.
- Fixed transcript playback highlighting: punctuation can no longer be selected through nullable-ID equality, and the active cue now advances through aligned English/Chinese token groups according to in-cue playback progress with 100 ms UI updates.
- Hid the app bottom navigation bar on the transcript route so the video and bilingual transcript use the full available height; the transcript header back action and Android system back behavior remain available.
- Restricted transcript token highlighting to persisted `WordOccurrence` vocabulary/phrases. Ordinary words and standalone prepositions no longer receive playback highlights; a preposition is highlighted only when it belongs to an extracted multi-word phrase. Chinese linkage now requires the LLM-provided exact contextual substring inside the translated cue; dictionary definitions are reserved for vocabulary cards, and uncertain mappings remain unhighlighted.
- Upgraded Room to v4 with non-destructive migration and an optional per-occurrence `translationZh`. New vocabulary extraction accepts useful fixed phrases, asks the LLM for a Chinese span that must occur verbatim in the cue translation, and persists only validated spans for reliable bilingual linkage.
- Added a v5 migration with alignment-version tracking. Opening an older transcript triggers one versioned vocabulary-only refresh (no media download, ASR, or subtitle retranslation), then backfills exact Chinese cue spans and stops rerunning after the records reach the current alignment version.
- Removed every vocabulary-count cap. Local preprocessing now retains the complete de-duplicated content-word candidate set for the full video; LLM enrichment runs in unlimited 40-candidate request batches, adds fixed phrases and Chinese cue spans, and all batches are merged without a final `take()` truncation. Local candidates still persist if an enrichment batch fails. Room v6 records the extraction version so older capped videos receive one vocabulary-only full refresh.
- Fixed full-video phrase coverage: every subtitle cue is additionally scanned in unlimited 25-cue LLM batches for reusable fixed expressions/phrasal verbs, while the versioned WorkManager name forces one new unlimited refresh even if a previous capped refresh already completed.
- Replaced the apparent per-word LLM request loop: dictionary English senses are now persisted first, all missing Chinese definitions are translated in 40-sense batches, each completed batch is committed immediately, and existing Chinese senses are skipped on resume. Room migrations now check column existence so devices that ran intermediate v4/v5 builds upgrade safely.
- Consolidated vocabulary AI work into one request per 10 bilingual subtitle cues. Each response returns all content words, fixed phrases, lemmas, concise Chinese meanings, and exact Chinese cue spans; the separate candidate-enrichment, phrase-extraction, dictionary-definition-translation, and post-refresh alignment requests are no longer part of the v3 vocabulary flow. Local scanning still persists any content word the model omits without issuing another LLM request.
- Replaced fixed cue batches with whole-transcript-first extraction: bilingual subtitles are sent in one request when within the 90k-character safety budget and otherwise balanced into two requests. Added an APK-bundled UTF-8 English-Chinese TSV dictionary with in-memory lookup; the lookup order is bundled dictionary → Room remote cache → Dictionary API. Multi-word phrases never call Dictionary API, and only single uncommon words/proper names absent from the bundled dictionary can reach the remote service. Vocabulary extraction version is now v4 so older videos receive one optimized refresh.
- Whole-transcript extraction requests up to 8192 output tokens and recursively halves only a batch whose response is truncated, malformed, or otherwise fails. When the same response already supplies `definitionZh`, dictionary lookup is local-only and remote API access is disabled; system TTS remains the pronunciation fallback when the bundled dictionary has no phonetic/audio entry.
- Implemented the optional full offline dictionary pack: Settings can download/delete MIT-licensed ECDICT, WorkManager waits for network and uses a dedicated 30-minute download client, CSV is streamed into an atomically committed application-private SQLite index, installation state/entry count/size are shown in UI, and lookup falls back cleanly after deletion. The pack excludes audio and continues to use Android TTS.
- Added the real bundled basic dictionary generated reproducibly from ECDICT: 45,000 filtered English words with phonetics, part of speech, concise English/Chinese definitions in a `WITHOUT ROWID` SQLite database. The generator now requires both phonetics and Chinese definitions for every entry, and the previous 112-entry TSV fallback has been removed from both assets and the lookup path.

## Dictionary and contextual alignment completion update (2026-07-19)
- Fixed Android asset packaging for the bundled dictionary. AGP automatically expanded the previous `.gz` asset and removed its suffix while runtime code still requested the gzip filename, silently disabling all local lookups. The dictionary is now stored as `basic_en_zh.sqlite.pack`, remains gzip-compressed inside the APK, and installs atomically with explicit error logging.
- Bumped the bundled dictionary format to v3 so devices replace previously extracted selections. Verified the packaged database contains 45,000 entries, includes common words observed in remote-API logs, and passes SQLite `integrity_check`.
- Added a local-dictionary availability guard so an installation/open failure cannot turn a whole transcript into hundreds of sequential Dictionary API calls. Remote lookup remains only for genuine single-word misses.
- Added a deprecated `VocabularyAlignmentWorker` compatibility shim. WorkManager can now complete historical work persisted under that removed class name after an APK upgrade instead of failing with `ClassNotFoundException`.
- Separated transcript alignment from dictionary meanings. Dictionary phonetics and definitions are retained for vocabulary cards only; transcript Chinese highlighting uses only an exact contextual substring copied from the matching Chinese subtitle cue.
- Fixed per-video lemma deduplication that discarded later contextual meanings. Vocabulary alignment now retains one result per subtitle cue plus lemma, and occurrences are written only to the cue for which the LLM returned the mapping.
- Added a dedicated bounded alignment-repair phase after whole-transcript vocabulary extraction. Missing mappings are sent to DeepSeek in batches of 100 with a strict exact-substring contract, tolerant response-field parsing, verbatim cue validation, and per-batch acceptance logs.
- Upgraded the vocabulary/alignment data version to v6. Opening an older transcript schedules one vocabulary-only refresh, removes stale v4/v5 occurrences, and rebuilds exact per-cue Chinese mappings without rerunning download, ASR, or subtitle translation.
- Updated transcript Chinese tokens to support multiple alignment IDs, so overlapping English words/phrases that legitimately map to the same Chinese span no longer overwrite each other.
- Device acceptance on `video-d1b0fb80`: 660 vocabulary occurrences persisted, 641 exact Chinese subtitle mappings accepted (`97.1%`), 19 uncertain/omitted expressions intentionally left without fabricated Chinese alignment. `VocabWorker` completed successfully at version 6.
- Latest automated acceptance: `:app:testDebugUnitTest :app:assembleDebug` completed with `BUILD SUCCESSFUL`; the debug APK was built and installed on the active emulator.

## M4 review-loop completion update (2026-07-19)
- Added `ReviewLogEntity` plus a non-destructive Room 6→7 migration. Every rating atomically records the previous/next repetitions, interval, ease factor, and due date while updating the default card.
- Implemented a tested binary SM-2 scheduler: `AGAIN` resets to a one-day interval and lowers ease; `GOOD` advances through 1 day, 6 days, then rounded `interval × ease` intervals.
- Replaced the bottom-nav validation placeholder with the real review route. The learning UI follows Stitch `_3`: cream background, segmented progress, large rounded front/back card, flip button, drag/swipe gestures, explicit forgot/undo/known controls, and the selected yellow review destination.
- Card fronts show only the English lemma, phonetic, and English video sentence. Card backs show Chinese definition/context, part of speech, pronunciation, and a source-video jump at the exact cue start time.
- Added dictionary-audio pronunciation with Android TTS fallback, including queued fallback while TTS initializes.
- Added a Room-backed word book with source-video grouping, search, due/new/mastered filters, list/grid layouts, manual add, edit, and delete.
- Added today's reviews, due count, mastered count, current streak, total learning days, a horizontally scrollable Monday-to-Sunday 12-month heatmap, five fixed intensity levels, accessible day descriptions, and per-day review/good/again/new-word/accuracy details.
- Historical M3 refreshes had left 598 orphan review cards with no current occurrence. Review queries now exclude those stale cards while preserving cards with a real video occurrence or a manual `USER` sense; the active emulator shows 332 valid due cards from 660 persisted occurrences.
- Automated acceptance: `:app:testDebugUnitTest :app:assembleDebug` passes. Unit coverage now includes binary scheduling, heatmap thresholds, leap-day inclusion, complete-week layout, local-time aggregation, and cross-year streaks.
- Device acceptance on the active emulator completed with preserved M3 data. The first valid card displays its real bilingual cue context; a `GOOD` action persisted `0 repetitions / 0 days → 1 repetition / 1 day`, raised ease `2.5 → 2.6`, scheduled exactly `+86,400,000 ms`, and appeared in today's stats/detail as 1 review, 1 remembered, 1 new word, 100% accuracy.
- Final M3 regression snapshot: 660 occurrences, 641 validated exact Chinese spans, zero duplicate default cards, and matching 98/98 English/Chinese cue sequence sets. Final automated suite: 24 tests, zero failures/errors.

## Vocabulary-definition correctness fix (2026-07-19)
- Root cause: historical M3 runs persisted the first remote Dictionary API sense and never replaced it after the bundled ECDICT dictionary became available. This produced contextually implausible card meanings such as `get → 后代`, `new → 新的事物`, and `york → 投掷约克球`.
- Added versioned `standard-en-zh-v2` sense repair. Opening Review performs an offline-only repair for existing single-word lexemes, replacing stale non-user senses with the current bundled/offline standard dictionary while preserving manually edited `USER` senses.
- New VocabWorker runs now always refresh standard senses instead of accepting the first previously persisted sense, and vocabulary version 7 schedules one vocabulary-only refresh for older videos.
- Review queries prioritize `USER`, then current standard dictionary senses. Standard ECDICT part-of-speech abbreviations are normalized into readable values such as `verb / noun` and `adjective`.
- Standard dictionary meaning and exact subtitle meaning are now distinct UI fields. The card/list presents the broad dictionary definition as the main meaning and optionally shows `本句：…` only for an unambiguous mapping from the same selected occurrence; Chinese spans shared by several English words are suppressed rather than presented as a misleading single-word meaning.
- Device regression corrected confirmed examples: `get → 得到/获得/变成…`, `new → 新的/最近的…`, `know → 知道/了解/认识…`, and `york → 约克郡/约克王朝`. The active database repaired 297 standard senses without a network request. Latest suite: 26 tests, zero failures/errors; debug APK builds and installs successfully.

## Review/transcript experience polish (2026-07-19)
- Review-card phonetics are normalized to slash notation (`dei`, `/dei/`, and `[dei]` all render as `/dei/`).
- Card backs now show the selected video's English sentence and Chinese subtitle together in a dedicated bilingual context panel. The back face scrolls internally so long definitions and long bilingual examples remain fully readable.
- The back-face lemma uses symmetric layout space around the title: the pronunciation button sits immediately to the right, while an equal invisible slot on the left keeps the lemma centered on the physical card.
- Transcript playback following now repositions every newly active cue to the same reading line just below the vocabulary strip instead of waiting until the cue leaves the viewport. Manual vocabulary jumps use the same stable offset.
- Added regression tests for phonetic delimiter normalization and the fixed transcript follow offset.
- Emulator visual acceptance confirmed slash-form phonetics, fully visible bilingual back-face examples, centered lemma geometry with the pronunciation control on the right, and the transcript's fixed top reading position. Latest automated suite: 29 tests, zero failures/errors; debug build and install pass.

## Word-book grouping and phrase-first transcript update (2026-07-19)
- The word book now groups cards by source video with independent expand/collapse controls in both list and grid layouts. Each group shows `mastered/total`, and the word-book tab header shows the global mastered/total value (for example `1/200`).
- Transcript follow timing now promotes the next cue as soon as the previous cue reaches `endMs`, including any silence before the next `startMs`. The fixed-position move uses immediate `scrollToItem`, avoiding an animation that starts after speech has already begun.
- Transcript highlighting is phrase-first. Explicit persisted phrases render as one continuous English highlight and one continuous Chinese highlight. Existing adjacent word mappings are conservatively grouped across common connectors such as `a`, `at`, `to`, and `of` when their Chinese spans are also adjacent, enabling structures such as `show a video` without fabricating translations.
- Vocabulary-strip selections now resolve through the final phrase grouping, so selecting either constituent word selects the shared phrase bar.
- Added tests for phrase continuity, adjacent-word phrase grouping, and exact sentence-end follow timing. Latest suite: 32 tests, zero failures/errors; debug build/install and emulator word-book collapse acceptance pass.

## Transcript layout-stability polish (2026-07-19)
- Highlightable words/phrases now reserve identical horizontal/vertical padding, minimum height, line height, and font weight whether selected or not. Playback only changes the background color, so highlighting no longer causes reflow or container-height jumps.
- Ordinary word tokens use the same stable word padding, further reducing line-break changes when phrase segments are present.
- English/Chinese FlowRow gaps and section spacing were tightened and fixed for a denser, more predictable transcript layout.
- The active-cue accent rail and its left inset are now permanently reserved; inactive rows render the rail transparent instead of changing content width when they become active.
- Latest acceptance: 33 tests, zero failures/errors; debug APK builds, installs, opens the transcript route, and renders the stabilized layout successfully.

## Persistent phrase highlight and card readability polish (2026-07-19)
- Phrase playback highlighting is now step-based: once a phrase starts, it remains highlighted through the rest of its timing window and only switches when the next highlighted phrase begins. The previous highlight no longer disappears immediately after its estimated word fraction ends.
- During inter-cue silence, the already pre-positioned next sentence immediately prelights its first learning phrase, so the transition contains no blank highlight gap.
- Removed the entire `KEY VOCABULARY` strip below the embedded video. The active transcript card now starts directly beneath the player and uses the list's top position as its fixed reading line.
- Review-card motion now uses an unclipped `graphicsLayer` plus a larger 24dp/20dp shadow-safe margin. Rotation/translation occur inside that reserved canvas instead of pushing the Surface against a clipping boundary.
- Card definitions are split on both Chinese and English semicolons and rendered one sense per line, left-aligned with a slightly tighter type scale for scanning.
- Latest suite: 36 tests, zero failures/errors; debug build passes and emulator inspection confirms multiline definitions and expanded shadow space.

## Fixed-collocation transcript correction (2026-07-19)
- Investigated the reported `lot → 经常` mismatch on device data. The persisted row contained only the anchor word `lot`, while the real source was `You a lot of time see...`; `time` had no Chinese span, so ordinary adjacent-span grouping could not form the phrase.
- Added a narrow grammar-based legacy repair for `lot`: `lot of + noun` expands to the complete collocation (for example `lot of time`), while standalone adverbial `a lot` expands left to include its article. The existing exact Chinese contextual span is retained.
- Candidate allocation now sorts by final expanded English range, ensuring a complete collocation wins over a constituent single-word row such as `time`.
- Updated the vocabulary extraction prompt to prefer complete conventional collocations such as `lot of time`, `a lot of people`, and `after school`, reducing future fragment rows.
- Added regressions for `lot of time ↔ 经常` and `a lot ↔ 经常`. Latest suite: 38 tests, zero failures/errors; debug build/install and transcript-route launch pass.

## Typed LLM phrase pipeline completion (2026-07-19)
- Replaced the prompt-only/display-heuristic approach with a persisted typed vocabulary contract: every LLM item must declare `WORD`, `COLLOCATION`, `PHRASAL_VERB`, or `IDIOM` alongside `surfaceForm`, `lemma`, `sourceCueId`, and exact `translationZh`.
- Production validation now rejects missing/unknown types, wrong cue IDs, non-verbatim English surfaces, Chinese spans absent from the matching translated cue, and type/shape mismatches (for example multi-word `WORD`).
- Phrase-over-word conflict resolution runs before Room persistence. Within each cue, typed multi-word items sort before single words and suppress every overlapping constituent occurrence; non-overlapping occurrences remain valid.
- Added `WordOccurrenceEntity.itemType`, Room 7→8 non-destructive migration, DAO projection, and transcript-domain propagation. The transcript renderer now consumes only persisted exact surfaces/types; all `lot` hard-coding and adjacent-word phrase inference were removed from the display layer.
- Vocabulary extraction version is now v8 with unique work `transcript-vocabulary-v8-typed-phrases-*`. Opening an older transcript schedules a vocabulary-only re-extraction; it does not re-download media or rerun ASR/subtitle translation.
- Device migration acceptance: database upgraded to schema 8, the new column exists, all 660 historical occurrences were preserved, and the existing video is correctly marked pending v8 refresh. Latest suite: 40 tests, zero failures/errors; debug build/install pass.

## Semantic phrase coverage completion (2026-07-19)
- Upgraded the persisted LLM vocabulary contract from v8 to v9. Phrase classification now also includes `FORMULAIC_EXPRESSION`, `DISCOURSE_MARKER`, and `GRAMMATICAL_CHUNK`, covering complete politeness formulas, conversational connectors, and emphatic auxiliary constructions.
- Added a dedicated phrase-coverage audit after general vocabulary extraction. It explicitly checks every bilingual cue for full reusable multi-word units and requires one per-cue response (including empty `items`), so truncated or omitted cue ranges are detected and retried with smaller batches instead of being mistaken for “no phrase”.
- Strengthened longest-unit conflict resolution: extraction and audit results are de-duplicated by exact surface rather than lemma before overlap handling, allowing a complete phrase to suppress contained words or shorter phrases even when the LLM gives them the same canonical lemma.
- Added tolerant but still validated LLM JSON parsing for arrays, wrapped objects, fenced JSON, and JSONL/object sequences. Exact cue IDs, verbatim English surfaces, exact Chinese subtitle substrings, type/shape rules, and phrase-over-word conflict resolution remain mandatory before Room persistence.
- Versioned refresh work is now `transcript-vocabulary-v9-semantic-phrases-*`; opening an older transcript reruns only vocabulary/phrase extraction and preserves download, ASR, subtitle translation, review history, and all other user data.
- Emulator acceptance completed on `video-d1b0fb80` with the configured DeepSeek provider. The v9 worker persisted 499 items, 486 with exact Chinese spans, and marked the video vocabulary version 9. Confirmed rows: `Thank you for coming → FORMULAIC_EXPRESSION → 感谢你来`, `by the way → DISCOURSE_MARKER → 顺便说一句`, `do appreciate → GRAMMATICAL_CHUNK → 真的很感激`, plus a second `thank you for coming → 谢谢你们来` occurrence. Overlapping fragment words were absent for those spans.
- Latest automated acceptance: 45 unit tests, zero failures/errors; `:app:testDebugUnitTest :app:assembleDebug` passes, the debug APK installs successfully, and the online v9 vocabulary-only refresh completes with WorkManager `SUCCESS`.

## Review-card shadow clipping fix (2026-07-19)
- Fixed the remaining click/flip shadow clipping at the actual layer boundary. The drag/rotation/alpha `graphicsLayer` now belongs to a full-size shadow stage rather than the elevated `Surface` itself.
- The card is inset inside that stage by 30dp horizontally and 26dp vertically, so its 9dp elevation is rendered into real parent space on all four sides before the whole stage is translated or rotated. This also keeps pointer gestures attached to the larger transform layer instead of recreating an equal-size offscreen card layer during `AnimatedContent` face changes.
- Emulator acceptance covered the front face, click-to-back transition, full swipe to the next card, and a below-threshold drag followed by spring return. Screenshots confirm continuous top, bottom, left, and right shadows with no hard crop line.
- Automated acceptance remains green: 45 unit tests, zero failures/errors; `:app:testDebugUnitTest :app:assembleDebug` passes and the updated APK installs successfully.

## 16KB-page YouTube download compatibility fix (2026-07-19)
- Root cause confirmed from device logs: youtubedl-android 0.18.1's packaged FFmpeg dependencies use 4KB ELF alignment and cannot execute on a 16KB-page Android image. The previous download selector also preferred separated video/audio streams and unconditionally sent Bilibili `Referer`/`Origin` headers to every site, making YouTube merge warnings and CDN 403 responses more likely.
- Added runtime capability detection using the actual OS page size. On 16KB devices the app initializes yt-dlp only, removes stale extracted FFmpeg/aria2c packages, never initializes those incompatible runtimes, and exposes the capability to download/audio stages. 4KB devices retain the existing high-quality merged-stream path.
- Added site-scoped request policy. YouTube no longer receives Bilibili headers; Bilibili and Xiaohongshu keep their own anti-hotlink headers. A 16KB device requests only pre-muxed formats containing both video and audio, while YouTube automatically retries `android_vr` and `web_safari` compatible clients after the default stream fails.
- Forced yt-dlp's native HLS downloader for video, subtitle discovery, and companion-audio repair so yt-dlp cannot silently spawn the packaged `libffmpeg.so`. Audio segmentation now checks runtime capability before any FFmpeg fallback and gives a readable compatible-media message on 16KB devices.
- Download Cookie handling now creates an application-private, temporary Netscape cookie jar for yt-dlp and deletes it in `finally`. Both raw browser header syntax (`name=value; ...`) and full Netscape cookies.txt input are supported. YouTube bot/403 failures are classified into an actionable `设置 → Cookie 管理` message; the last format error no longer hides an earlier authentication challenge.
- Real-device acceptance on the 16KB emulator (`getconf PAGESIZE = 16384`) used `https://www.youtube.com/watch?v=c347oYQO57A`. One run automatically recovered from the first 403 through the `android_vr` client and downloaded format 18 as a 39,875,323-byte MP4. Host ffprobe verified H.264 video plus AAC audio and 796.723-second duration; WorkManager returned download `SUCCESS`. When YouTube later required sign-in for the same IP, the UI correctly displayed the Cookie-management recovery message. No 4KB-alignment or “ffmpeg is not installed for merge” error remains in the final download path.
- Latest automated acceptance: all unit tests pass with zero failures/errors; `:app:testDebugUnitTest :app:assembleDebug` passes and the final APK installs successfully.

## LLM provider configuration experience (2026-07-20)
- Replaced the DeepSeek-only settings state and dialog with a generic OpenAI-compatible LLM provider configuration flow. The top of the dialog now exposes horizontally scrollable DeepSeek, Xiaomi MiMo, Doubao, and Custom tabs; selecting a preset fills its current official Base URL and a usable default model while still allowing model edits.
- Added one-tap links to each preset's official API-key console: DeepSeek Platform, Xiaomi MiMo Open Platform, and Volcengine Ark. Link failures are surfaced in the dialog instead of crashing the settings screen.
- Custom configuration supports a provider name, HTTPS Base URL, model/Endpoint ID, and encrypted API key. Validation rejects blank names/models and invalid or non-HTTPS endpoints, normalizes trailing slashes, and continues to persist only the `secretAlias` in Room.
- Provider switching is key-safe: a blank API-key field reuses the encrypted key only when editing the same persisted provider and unchanged Base URL. Switching presets, changing a custom provider identity, or redirecting the endpoint requires the matching provider key rather than silently sending an old key to a new host.
- The active provider name now appears throughout Settings and generic worker recovery messages no longer instruct users to configure DeepSeek specifically. MiMo requests use its documented `max_completion_tokens` field while retaining the shared OpenAI-compatible client.
- Added unit coverage for preset order/defaults/official links, storage-ID round trips, URL normalization, and custom-provider validation.
- Automated acceptance: 66 unit tests, zero failures/errors; `:app:testDebugUnitTest :app:assembleDebug` passes. The APK installs on the active emulator, and device inspection confirmed preset scrolling, MiMo autofill, the custom-provider form, encrypted-key placeholder behavior, and fully visible dialog/save controls.

## Multi-provider STT configuration and MiMo transcription (2026-07-20)
- Expanded the STT settings dialog from a Doubao-only form to provider tabs for Doubao ASR, Xiaomi MiMo, and Custom. Both official presets autofill their endpoint/model or resource ID and offer a direct link to the official API-key console; Settings summaries and security status now display the active STT provider name.
- Official documentation review confirmed Xiaomi MiMo exposes `mimo-v2.5-asr` through `https://api.xiaomimimo.com/v1/chat/completions`, accepts only Base64 data-URL MP3/WAV audio, and limits the encoded input to 10 MB. DeepSeek's current official model list and API reference expose text Chat/Completions/Models APIs but no STT/audio-transcription model or endpoint, so no misleading DeepSeek STT preset was added.
- Implemented the MiMo production path rather than UI-only configuration: provider protocol is persisted in `optionsJson`, `TranscribeWorker` dispatches through a generic speech-to-text client, audio is re-segmented into three-minute chunks when an OpenAI audio protocol is selected, Android MediaCodec decodes M4A/AAC locally, and a tested converter produces 16kHz mono PCM WAV before Base64 upload.
- MiMo responses currently contain recognized text without sentence timestamps. The app conservatively splits punctuation-delimited sentences and distributes the chunk timeline by sentence length so transcript playback remains usable; the configuration dialog explicitly explains that these times are estimates. Doubao retains its native utterance timestamps and existing long-audio flash/standard behavior.
- Custom STT now supports three executable protocols: standard OpenAI-compatible multipart `/audio/transcriptions` (including verbose JSON segment timestamps when provided), MiMo-compatible `/chat/completions` audio messages, and Doubao BigModel headers/body. HTTPS, provider name, protocol-specific model/resource fields, and encrypted-key reuse boundaries are validated before save.
- Added regression coverage for preset availability, the deliberate absence of unsupported DeepSeek STT, protocol persistence/validation, WAV channel/rate conversion, and estimated sentence timing. Automated suite and emulator acceptance details are recorded with the final build below.
- Final acceptance: 71 unit tests, zero failures/errors; `:app:testDebugUnitTest :app:assembleDebug` passes and the latest APK installs successfully. Emulator inspection confirmed the Doubao/MiMo/Custom tabs, MiMo official Base URL/model autofill, API-key button, protocol-specific fields, explanatory timing notice, and fully visible save controls.

## Background processing and persistent multi-video queue (2026-07-20)
- Audited the previous implementation: downloads and subtitle stages already used WorkManager and separate unique work per video, so basic app-background execution and multiple independent tasks existed. However, long download/STT/LLM workers were not promoted to foreground work, queued items were absent from Room until a worker started, and there were no network constraints, background progress notifications, or transient-network retry policy.
- Download creation now persists the video placeholder and `PENDING` processing job before WorkManager enqueue. Multiple URLs therefore immediately appear in the Home processing section, survive app/process restarts through Room + WorkManager, retain per-video cancellation, and display deterministic queue positions such as `下载队列第 1 位` while waiting.
- Added connected-network constraints to downloads and every cloud-dependent subtitle stage, plus exponential backoff and bounded automatic retries for connection resets, DNS failures, timeouts, and HTTP 408/429/5xx failures. Authentication/403 failures are intentionally not looped and continue into the existing YouTube login recovery flow.
- Promoted downloads and all long subtitle/translation/vocabulary stages to WorkManager foreground jobs using a dedicated low-importance `后台下载与视频处理` notification channel. Notifications show stage/progress, reopen the app, and provide a system-level cancel action. Android 13+ requests notification permission, and the existing `dataSync` foreground-service declaration remains active.
- yt-dlp now uses `--continue`; task directories and partial media are preserved on cancellation/retry instead of being deleted automatically. WorkManager cancellation is bridged to `destroyProcessById`, so the underlying yt-dlp process stops promptly while reusable partial data remains available. Explicit video deletion remains the path that removes library data.
- WorkManager uses a bounded three-thread executor, allowing a small number of videos to download/process concurrently while the remaining tasks stay persistently queued rather than launching an unbounded number of media/LLM jobs.
- Device acceptance: with network disabled, two different videos were enqueued consecutively and immediately rendered as two Room-backed processing cards with queue positions 1 and 2. After returning to the launcher, JobScheduler showed both persistent jobs waiting specifically on the unsatisfied connectivity constraint. Restoring network produced an ongoing foreground-service download notification with progress and a cancel action. Test tasks were cancelled and device connectivity restored afterward.
- Final automated acceptance: 73 unit tests, zero failures/errors; `:app:testDebugUnitTest :app:assembleDebug` passes and the latest APK installs successfully.

## YouTube download speed strategy (2026-07-20)
- Reordered YouTube format selection around the measured bottleneck. Downloads now try progressive HTTP MP4 (`18`, then `22`, then another pre-muxed HTTP MP4 up to 720p) before any fragmented HLS stream, avoiding the previous slow native-HLS path whenever a direct audio+video file is available.
- Added client-aware direct fallbacks. The default YouTube client is tried first, followed by `web_safari`; `android_vr` is retained only for anonymous downloads because yt-dlp rejects that client when a Cookie jar is supplied. On compatible 4KB devices, the high-quality split-stream/FFmpeg strategy remains available after direct attempts.
- Restricted HLS behavior to the last compatibility fallback. `--hls-prefer-native` is no longer global, and the fallback now uses `--concurrent-fragments 4` to fetch fragmented media in parallel.
- Reduced shared bandwidth/CPU contention by limiting the WorkManager executor to two workers. Additional downloads and cloud stages remain persisted in the existing Room/WorkManager queue.
- Throttled yt-dlp progress propagation to a three-percentage-point change or two-second interval. Foreground notifications now show the selected channel, percent, parsed transfer speed, and ETA while avoiding the previous per-percent foreground-service transaction pressure.
- Added policy regressions covering direct-MP4-first order, four-fragment HLS fallback, the 4KB high-quality fallback, and Cookie-aware exclusion of `android_vr`.
- Clean automated acceptance: 74 unit tests, zero failures/errors; `clean :app:testDebugUnitTest :app:assembleDebug` completed successfully and resolved the stale incremental Hilt generated-file collision. The resulting debug APK installed successfully on the active 16KB-page emulator.
- Real-device acceptance used an active YouTube video. The running yt-dlp command selected `-f 18/22/best[protocol^=http]...` without HLS flags; with a configured Cookie, no `android_vr` attempt was present. The notification displayed `YouTube 直连 MP4 · 15% · 93.01KiB/s · 剩余 1:44`. Logcat showed throttled foreground updates and no `Too many transaction errors`. The acceptance download was explicitly cancelled afterward and its yt-dlp process stopped promptly.

## CEFR vocabulary difficulty slider (2026-07-20)
- Added a persistent five-position English-level slider to the Review page: A1 `入门`, A2 `初级`, B1 `中级`, B2 `中高级`, and C1–C2 `高级`. Each position uses a distinct green/teal/blue/purple/orange active track, matching label, visible ticks, and an elevated white thumb based on the supplied reference.
- The selected level means the learner's current level/minimum vocabulary threshold. Review sessions, word-book counts/groups, current due/mastered metrics, and transcript learning highlights update locally to include only that CEFR level and above. Historical review logs, heatmap, streaks, and total learning days remain unchanged.
- Added `显示全部难度` to the word book. Manually created cards and genuinely unclassified rows stay visible at every level, so filtering never hides user-owned data or silently discards uncertain vocabulary.
- Upgraded Room from schema 9 to 10 with a non-destructive migration adding per-occurrence `difficultyLevel`, `difficultySource`, and `difficultyConfidence`. Existing lexemes, cards, review logs, subtitles, and occurrences are preserved.
- Added an offline difficulty backfill for existing occurrences. Common-word anchors, phrase-type heuristics, and fixed-expression overrides classify old data without an LLM/network request. Device backfill preserved all data; default B1 filtered 824 basic cards and still produced a 25-card due session, while C1 filtered 2251 and produced six due cards.
- Upgraded new vocabulary extraction to contract v11. General extraction and phrase audit request a strict `A1/A2/B1/B2/C1/C2` level for the complete contextual expression. Local anchors prevent a basic standalone word such as `school` from being overrated because of a technical topic; known complete phrases such as `take it for granted` retain phrase-level difficulty.
- Existing v10 complete phrase data is deliberately not sent back to the LLM merely to obtain CEFR metadata. Opening an older transcript runs only the local backfill, while genuinely pre-v10 vocabulary data can still use the established versioned refresh path.
- Emulator acceptance confirmed Room 9→10 upgrade, preserved data, persisted C1 selection across process restart, immediate session change from 25 B1 cards to six C1 cards, the word-book all-difficulties control, and no vocabulary/LLM network request during migration or slider use.
- Final automated acceptance: 79 unit tests, zero failures/errors; a clean build plus `:app:testDebugUnitTest :app:assembleDebug` passes. Coverage includes level ordering, unknown/manual visibility, local basic-word correction, complete-phrase difficulty, invalid LLM fallback, and review filtering.

## Difficulty slider compact interaction polish (2026-07-20)
- Changed the difficulty control from an always-expanded panel into a compact summary card. Its normal state shows only the selected label/CEFR level, a small level-colored indicator, `英语难度`, and a disclosure arrow, returning substantial vertical space to the study card and word-book content.
- Tapping the summary expands the description, five-stop slider, and labels through coordinated vertical-size and fade animations. During dragging, the preview label and active-track color update immediately; releasing the thumb saves the selected level, refreshes filtered content, and automatically collapses the panel.
- Replaced the Material `Surface` slider thumb with a clipped circular `Box` using a circle-only shadow, fill, and border. This removes the rectangular white compositing/placeholder block that was visible beneath the circular thumb.
- Emulator acceptance verified compact and expanded layouts in both Study and Word Book, automatic A2→B2 collapse after selection, immediate filtered-count/content refresh, and a clean circular thumb with no white rectangular artifact. Automated acceptance remains 79 tests with zero failures/errors; debug APK build/install succeeds.

## Review page space, slider track, and card-back readability polish (2026-07-20)
- Removed the Review page `SubLingo` logo and tightened the section-tab top/bottom padding, moving the difficulty summary, progress strip, and study card upward while preserving the three-section navigation.
- Reduced the collapsed difficulty card to a single CEFR summary row plus its colored dot. Removed both the `英语难度` subtitle and disclosure arrow; the whole summary remains tappable and retains the animated expanded state.
- Replaced Material's segmented slider track with a custom rounded Canvas track. The active color is now drawn continuously to the thumb center beneath the circular thumb, so no gray/blank break appears under it; ticks and inactive track remain visible.
- Rebuilt the card-back heading as a physically centered single-line title layer. Font size scales down for longer lemmas, the pronunciation control is reduced from 44dp to 34dp and floats at the right edge without consuming title width, preventing `understand`-length words from wrapping.
- Review projections now include the selected occurrence's original `surfaceForm`. The back-face English example highlights that exact inflected word or complete phrase with a yellow background; the Chinese example highlights the validated contextual meaning when available. Matching is case-insensitive and preserves complete phrase boundaries.
- Emulator acceptance confirmed the compact logo-free Study page, continuous purple B2 track beneath the thumb, single-line centered `understand`, smaller pronunciation button, and synchronized `understand / 听懂` highlights. Final automated suite: 81 tests, zero failures/errors; APK build and install succeed without runtime crashes.

## Home capsule and unified playback-speed controls (2026-07-20)
- Replaced the Home and legacy Download-page URL containers' fixed 32/34dp corners with full `999dp` capsule clipping on both the purple outer shell and pale inner input shell. The complete control now keeps true semicircular ends at its actual measured height.
- Added a shared playback-speed component used by both the immersive player and embedded transcript player. The available options are now `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, and `2.0x`; selecting the speed applies it immediately to the active Media3 player.
- Replaced tap-to-cycle speed behavior with a centered, edge-safe speed selection dialog. It shows all six speeds, highlights/checks the active option, dismisses after selection, and prevents the playback controls from auto-hiding while the menu is open.
- Compressed the immersive player's bottom control panel by reducing outer/inner padding, vertical gaps, and corner radius. Its timeline remains on the first row; the second row now places `EN` and `中` at bottom-left, play/pause in the center, and speed immediately to the left of fullscreen.
- Emulator acceptance confirmed the full Home download capsule, the compact immersive control geometry, language/play controls on one row, speed/fullscreen adjacency, and the complete six-option dialog including `0.5x`. The transcript player consumes the same shared menu/options implementation. Automated suite passes with zero failures/errors and the final APK installs successfully.

## Player seeking, rotation continuity, and anchored speed menu fix (2026-07-20)
- Fixed the immersive player's scrubber race. A dedicated seeking state now prevents the playback-position polling loop from overwriting the user's drag preview; releasing the thumb seeks to the chosen position and resumes ordinary polling. Database media duration is used until Media3 exposes its runtime duration, so the control no longer starts with an unusable zero range.
- Replaced the embedded transcript player's clickable `LinearProgressIndicator` with a real Material slider. It supports continuous dragging, pauses its 40ms position synchronization while the user scrubs, commits the exact target on release, and clears sentence-repeat boundaries before seeking.
- Prevented orientation changes from restarting playback. `MainActivity` now handles orientation/screen-size configuration changes in place, both screens retain their Media3 player by video identity with application context, and the immersive fullscreen action snapshots current position/play state before requesting orientation. The same player instance therefore continues across portrait/landscape transitions instead of rebuilding from zero.
- Replaced the full-screen speed Dialog with an anchored Material dropdown attached to the speed button. A negative vertical offset makes it open upward above bottom controls, keeping the video visible while exposing all six speeds and the active checkmark. The same anchored component is shared by immersive and transcript players.
- Final automated acceptance remains 81 tests with zero failures/errors; debug APK builds successfully. Emulator inspection confirms the anchored compact menu and draggable slider semantics, with no runtime crash after repeated screen/control interactions.

## Native player scrubber and near-button speed menu correction (2026-07-20)
- Replaced both Compose scrubbers with an Android native `SeekBar` embedded through `AndroidView`. The native view owns its complete DOWN/MOVE/UP sequence above the clickable player surface, reports continuous preview positions, commits the exact position on release, and ignores polling updates while pressed. This removes the remaining gesture-arbitration failure that made the immersive progress control appear non-draggable.
- Both immersive and transcript players use the same native scrubber component and the same bounded 0–10,000 progress-to-duration conversion. Unit coverage verifies start, midpoint, end, overflow, and zero-duration mapping.
- Removed the large fixed upward offset from the playback-speed dropdown. The Material menu now anchors directly to the speed button and lets the platform choose upward/downward placement based on available space, substantially reducing the visual gap while keeping the full six-option list visible.
- Automated acceptance: 82 tests, zero failures/errors; final APK builds and installs successfully. Emulator visual inspection confirms the menu begins immediately above the bottom control area rather than floating far away.

## Transcript control hit-testing and shadowless speed menu (2026-07-20)
- Removed the parent-level clickable modifier from the embedded transcript-player container. A dedicated video-only tap overlay now sits between the rendered video and the playback controls, so the native SeekBar and speed button remain above it in hit testing and receive their complete touch sequences.
- Repositioned the transcript speed control into the same bottom overlay box as the timeline, aligned to the timeline's top-right with only a small 17dp track inset. It no longer occupies a separate row far above the progress control.
- Set the shared Material speed dropdown's `shadowElevation` to zero. Immersive and transcript speed menus now render without a menu shadow while retaining the same anchored placement and six selectable speeds.
- Automated acceptance remains 82 tests with zero failures/errors; APK builds and installs successfully with no runtime crash.

## Scrubber stale-duration restart fix (2026-07-20)
- Root cause of both players restarting after a completed scrub was a stale closure in the native SeekBar listener. The listener was installed when the Android view was first created, often while `durationMs` was still zero; releasing the thumb therefore converted any progress to target `0` and called `seekTo(0)`.
- The SeekBar listener now reads the latest duration and latest seek/preview/seeking callbacks through Compose `rememberUpdatedState`. It no longer depends on creation-time values, so a completed drag maps against the current real duration and does not issue an implicit play command or reset playback state.
- Automated suite remains 82 tests with zero failures/errors; final APK builds and installs successfully.

## Bounded and cancellable Xiaomi MiMo vocabulary pipeline (2026-07-22)
- Root-cause evidence from the affected `Time to say goodbye` task showed translation completing in five batches without retries, while vocabulary extraction accumulated 21 failed records and repeated exact 120-second response-header timeouts. The apparent translation hang was the downstream vocabulary stage recursively splitting and retrying Xiaomi requests.
- Xiaomi MiMo now defaults to the supported, lower-cost `mimo-v2.5`. Persisted Xiaomi profiles using either the former `mimo-v2.5-pro` default or the invalid `MiMo-V2.5-Pro-UltraSpeed` display name are migrated automatically; manually selected model IDs remain untouched.
- Non-retryable LLM HTTP 4xx responses now stop vocabulary subdivision immediately. An invalid model or authentication configuration therefore produces one rejected request instead of consuming the full 16-request budget.
- OpenAI-compatible LLM calls now use cancellable OkHttp callbacks. WorkManager cancellation cancels the actual network call, and cancellation exceptions are rethrown instead of being interpreted as extraction failures that should split again.
- Xiaomi vocabulary extraction now starts with deterministic 24-cue batches rather than a whole-transcript request. Each Worker execution permits at most 16 API calls, at most two split levels, and at most two starts for the same input hash; interrupted `RUNNING` records therefore cannot restart indefinitely.
- Successful cached batches still resume without consuming the request budget. Exhausted or failed batches fall back to local content-word extraction so the task can finish with useful results instead of remaining at 90%. Batch completion advances foreground/Room progress from 90% through 95%.
- Translation and vocabulary requests now have explicit 8,192-token output budgets, and Xiaomi calls have a 90-second total timeout. Focused tests cover the Xiaomi batch plan, request budget, legacy-model migration, and cancellation of a genuinely in-flight HTTP request. `:app:assembleDebug` and all new focused tests pass; the pre-existing unrelated `TranscriptFollowPolicyTest.prePositionedNextCueStaysUnhighlightedUntilPlaybackStarts` remains the only full-suite failure.
- `mimo-v2.5` later returned HTTP 200 after roughly 50 seconds but with an empty `message.content` for both normal and split batches, consistent with the 4,096 completion budget being consumed before a final answer. Translation and vocabulary budgets are now 8,192, non-streaming mode is explicit, and empty responses expose only safe termination diagnostics (`finish_reason`, reasoning character count, completion token count). An empty final answer disables the remaining LLM calls for that Worker run and uses local vocabulary fallback instead of repeating equally slow subdivisions.
- The vocabulary cache key now includes the output-token budget. Existing failed 4,096-token records therefore cannot suppress the corrected 8,192-token request, while retries with unchanged request parameters remain capped and successful responses remain resumable.
- Live acceptance with `mimo-v2.5` showed that the exhaustive word-enumeration prompt remained unsuitable even at 8,192 tokens: 24-cue requests repeatedly hit the 90-second call timeout, while only some smaller descendants completed in 42–60 seconds. Because the pipeline already guarantees broad word coverage through deterministic local preprocessing, offline dictionary lookup, and local CEFR classification, Xiaomi MiMo vocabulary extraction now uses that local path directly and makes zero vocabulary-stage API calls. Xiaomi remains active for subtitle translation; other LLM providers retain remote vocabulary extraction.
- Local Xiaomi vocabulary occurrences now recover a conservative contextual Chinese highlight from the offline dictionary: an existing aligned subtitle span always wins; otherwise only a two-or-more-character dictionary term that appears verbatim in the same Chinese cue is accepted. New occurrences persist that exact span, while the review-card UI applies the same resolver to existing local cards. Definitions absent from the subtitle remain deliberately unhighlighted rather than using positional guesses.

## Resumable Xiaomi translation timeout recovery (2026-07-22)
- Diagnosed `video-6007752b`: the original 43-cue translation request was retried unchanged and cancelled after the Xiaomi-specific 90-second call timeout. The failure was an OkHttp total timeout (`InterruptedIOException: timeout` with nested `IOException: Canceled`), not a user cancellation or WorkManager background interruption.
- Xiaomi translation now starts with one subtitle cue per request; other OpenAI-compatible providers retain the existing token-budget batching. MiMo's per-call timeout is 60 seconds so an abnormally slow cue reaches recovery sooner instead of holding the foreground Worker for 90 seconds.
- Timeout handling is cancellation-safe and adaptive. A multi-cue request is recursively bisected; a long single cue is split at punctuation/word boundaries and translated as smaller fragments, with English occurrence indexes converted back to the original cue before recombination and validation.
- Successful descendants are persisted immediately, including their Chinese cue and complete word alignment. Worker retries therefore skip already completed sequences rather than losing useful HTTP 200 responses because a later sibling timed out. Translation-batch resume also verifies sequence ranges so records created by older batching policies are not incorrectly treated as current successes.
- Pixel 10 Pro live acceptance reused the affected task and preserved its data. The old request body was about 7,134 bytes; the corrected path issued requests around 895–1,166 bytes, logged timeout subdivision instead of an immediate whole-Worker retry, and persisted sequences 0–2 with 3, 16, and 15 word pairs respectively while later requests were still running. Kotlin compilation, focused subtitle/client tests, and Debug APK assembly pass.

## Notes for future agents
- Follow the implementation plan in `IMPLEMENTATION_PLAN.md`.  
  请遵循 `IMPLEMENTATION_PLAN.md` 中的实施计划。
- Follow the visual direction in `sublingo.md` and `DESIGN.md`.  
  请遵循 `sublingo.md` 和 `DESIGN.md` 中的视觉规范。
- The app should remain single-module for now.  
  当前应用先保持单模块结构。
- Room is intended to be the source of truth for task state.  
  Room 将作为任务状态的事实来源。
- Secrets must never be stored in plaintext.  
  密钥绝不能以明文形式存储。
- API keys should be stored only through the secure SecretStore; Room keeps only `secretAlias`.  
  API key 只能通过安全的 SecretStore 存储，Room 只保存 `secretAlias`。
## Translation malformed-JSON recovery (2026-07-22)
- Diagnosed `video-5e08247e` translation failure: Xiaomi MiMo returned HTTP 200 after 69 seconds, but one output object near subtitle index 128 omitted the `textZh` key/colon. Whole-array JSON parsing threw before the existing missing-index retry could run.
- Replaced whole-array translation parsing with a fenced-response-aware, balanced-object parser. Valid subtitle objects surrounding a malformed object are retained, while malformed, blank, duplicate, missing, and out-of-range items continue through strict alignment validation.
- The existing targeted retry now retries only damaged/missing indexes instead of discarding and retranslating every valid subtitle in the batch.
- Added regression coverage using the exact malformed response shape from Logcat plus duplicate/blank validation. `:app:testDebugUnitTest --tests com.sublingo.app.data.media.SubtitleToolsTest :app:assembleDebug` passes.
## Per-provider encrypted credentials and DeepSeek compatibility (2026-07-22)
- Confirmed the tolerant translation-response parser is provider-neutral and compatible with DeepSeek's OpenAI-compatible `/chat/completions` response. DeepSeek continues to use `max_tokens`; MiMo-only token naming and timeout behavior remain isolated in the HTTP client.
- Replaced the single `llm-default` / `stt-default` profile and shared secret aliases with one Room profile and one encrypted SecretStore alias per preset. Switching the active provider disables only its active flag; other providers' configuration and encrypted API keys remain stored.
- Added migration-on-load for the previously active legacy LLM/STT profile and encrypted key, preserving the user's current credentials without exposing plaintext.
- Saved API keys now render as `API Key 已加密保存` with an explicit `更改` button. The password input is shown only for an unconfigured provider or after the user chooses to replace its key. Editing model, URL, resource ID, or protocol does not discard or require re-entry of the provider's existing key.
- Added storage-identity regression coverage and verified provider preset, translation parser, and Debug build tasks successfully.
## Rolling platform-caption overlap cleanup (2026-07-22)
- Diagnosed the reported transcript overlap on `Life Lessons From My NYC Delivery Driver`: its platform track contains 304 YouTube rolling-caption snapshots, including 10 ms transition cues and progressively accumulated text (`Bye.` → `Bye. >> Okay...` → `>> Okay...`, and repeated sentence prefixes at identical displayed timestamps).
- Added a conservative rolling-caption detector requiring both multiple sub-100 ms snapshots and repeated adjacent suffix/prefix boundaries. Ordinary SRT/VTT tracks are left unchanged.
- New platform subtitles are normalized before persistence/translation: redundant transition snapshots are removed, repeated prefixes are trimmed, and retained cues are resequenced for exact bilingual translation alignment.
- Existing videos are repaired at transcript assembly time without redownloading or calling an LLM. English sequences drive bilingual rows, preventing translated orphan snapshots, while Chinese text is independently normalized with an English removal-fraction fallback when translation wording breaks exact overlap matching.
- Added regressions for the exact screenshot pattern, already-persisted bilingual tracks, Chinese-only orphan prevention, and ordinary subtitle preservation. Targeted tests and `:app:assembleDebug` pass.

## PHY110 bilingual highlight coverage and transcript-card layout (2026-07-22)
- Inspected the Room database from the real OPPO PHY110 without modifying device data. The current video contains 1,634 vocabulary occurrences; 1,198 have no persisted `translationZh`, and the transcript previously inherited the B1 review threshold, excluding most A1/A2 occurrences before alignment.
- Transcript learning highlights now consider every persisted difficulty level. Missing per-occurrence Chinese spans are recovered conservatively from another validated occurrence of the same lexeme, an exact source term retained in the current Chinese cue (for example `Siri`, `iOS`, `Android`, `AI`, or `iPhone`), or an exact non-generic dictionary substring already present in that cue. No positional or fuzzy Chinese guessing was introduced.
- Review cards now evaluate every occurrence for a lexeme and choose the earliest example that can produce a reliable bilingual highlight. The selected occurrence, source timestamp, English surface, and exact Chinese meaning move together, preventing an early unaligned example from hiding a later valid one.
- Removed the transcript card's purple side rail and its reserved inset. Cards now use the same full available width as the embedded video and no longer shrink inactive cards.
- Added resolver regressions for retained Latin proper nouns and same-lexeme translation reuse. Targeted vocabulary/review/transcript tests and `:app:assembleDebug` pass; the APK was installed and visually checked on PHY110. The displayed video and transcript cards both span x=48–1032 px, the rail is absent, and `test ↔ 进行测试` renders as a bilingual selectable pair. No LLM/API work was started during validation.

## Bidirectional player gestures, 1080p preference, and review spacing (2026-07-22)
- Increased the review card's usable vertical stage and shifted the card slightly downward, reducing the excessive gap above the bottom navigation while keeping its shadow canvas intact. The bottom navigation backing now extends upward through a transparent-to-cream gradient instead of ending at a hard horizontal color boundary.
- Video downloads now prefer a highest-quality stream capped at 1080p. On ordinary 4KB-page devices, yt-dlp selects and FFmpeg merges video/audio streams up to 1080p. On 16KB-page devices such as the OPPO PHY110, the app downloads an H.264 MP4 video stream up to 1080p plus M4A audio and merges them with Android `MediaMuxer`, then falls back to progressive/HLS single-file streams only when the preferred formats are unavailable.
- Added a reverse interactive transition from the transcript player to the immersive player. A downward drag moves and reshapes the transcript video under the finger toward the centered 16:9 immersive position while the transcript chrome fades out; crossing the threshold pops back to the existing immersive route with the latest playback position saved.
- Added shared three-zone double-tap behavior to both players: left rewinds 10 seconds, center toggles play/pause, and right advances 10 seconds. Targets are clamped to the media duration and the video-only gesture surface remains behind the native scrubber and speed controls.
- Added unit coverage for the three-zone double-tap mapping and updated download-policy tests for 1080p/native Android mux selection. Focused new tests pass and `:app:assembleDebug` succeeds. The known unrelated `TranscriptFollowPolicyTest.prePositionedNextCueStaysUnhighlightedUntilPlaybackStarts` remains the only selected-suite failure. PHY110 acceptance confirmed the review layout/gradient, immersive-to-transcript route, transcript-to-immersive downward transition, preserved playback position, and no fatal Logcat entry.

## Seek feedback and aspect-safe transcript return transition (2026-07-22)
- Added shared, transient seek feedback to both video surfaces. Double-tapping the left or right third displays a dark translucent pill with `↶ 10` or `10 ↷`; it scales/fades in quickly, remains readable during the seek, and fades out automatically after roughly 620 ms. Center double-tap continues to toggle playback without showing a seek icon.
- Replaced the transcript return transition's non-uniform `scaleX`/`scaleY` transform. The outer player container now interpolates its actual width and height from the transcript's 4:3 frame to the immersive 16:9 frame, while Media3 `PlayerView` remains `RESIZE_MODE_FIT` at every drag frame. Video pixels therefore keep their original aspect ratio; only the container and expected letterboxing change.
- The transcript page background now interpolates continuously from cream to pure black during the downward gesture, including the outer page padding and system status/navigation bars. Header/cards still fade out with the gesture, producing a clean black handoff to the immersive route.
- Focused gesture tests and `:app:assembleDebug` pass. PHY110 visual acceptance captured the right-side seek feedback and the beginning/middle/end frames of the downward transition: the subject's proportions remain stable, the container smoothly changes shape, the page darkens, playback position is preserved, and Logcat contains no fatal application crash.

## Pixel 10 Pro immersive-to-transcript transition polish (2026-07-22)
- Unified seek feedback lifetime around a single retained action. The arrow and `10` now remain in the same composable group and exit together: 240 ms readable time followed by a 160 ms fade/scale-out, for approximately 400 ms total on both players.
- The portrait immersive player now keeps a transcript preview composed underneath a full-screen black layer. Upward dragging moves the video toward the embedded-player position while the black layer becomes transparent, revealing the header, controls, video frame, and current transcript cards before navigation completes.
- Added an in-memory, video-scoped transition handoff containing the already-loaded file paths, playback position, duration, and subtitle cues. The destination uses this snapshot only until its Room-backed state is ready, eliminating the previous first-frame `视频不可用` / `字幕尚未生成` flash without changing Room as the source of truth or starting cloud work.
- Immersive title, subtitle overlay, and bottom controls now fade out within the first 32% of the upward gesture, preventing old-player chrome from overlapping the transcript preview during a slow drag.
- Transcript playback explicitly prepares, seeks to the saved live position, and starts immediately. Its central 58 dp play/pause control uses `CircleShape` and remains a true circle.
- Pixel 10 Pro AVD acceptance used only existing processed videos. Immersive and transcript seek feedback showed the complete icon-plus-number at roughly 150 ms and neither at roughly 500 ms. A slow upward swipe showed the transcript already rendered beneath the fading black layer; arrival contained real video/cards with no empty state, and playback advanced from 00:21 to 00:24 after navigation. Logcat contained no app fatal exception, ANR, out-of-memory, or Media3 playback-error match.
- Automated verification: `:app:compileDebugKotlin`, focused `VideoGesturesTest`, and `:app:assembleDebug` all pass. The resulting APK installs successfully on the Pixel 10 Pro AVD.

## ASR-only background pipeline and translation word mapping (2026-07-22)
- Removed the bottom-navigation cream gradient. Its backing is now a solid page-color rectangle whose top begins exactly at the top of the navigation-bar area; the capsule retains its existing spacing and shadow without a translucent strip above it.
- Removed platform-caption discovery from every newly scheduled video pipeline. Download completion now proceeds directly through audio extraction, configured in-app STT/ASR, Chinese translation, and vocabulary generation. The old `SubtitleDiscoveryWorker` remains only as a no-network compatibility shim so persisted WorkManager chains from older APKs can advance safely into ASR.
- ASR is now the canonical English track across extraction, transcription, translation, player subtitles, transcript assembly, and vocabulary generation. Existing platform tracks remain readable only as a legacy display fallback until the video is explicitly reprocessed; retrying processing creates the ASR track, invalidates vocabulary tied to obsolete cue IDs, and rebuilds the downstream data.
- Diagnosed the apparent background interruption as a scheduling race in addition to ordinary process pressure. The Home state reconciler saw translation marked `SUCCEEDED` before vocabulary and re-enqueued the same unique chain with `REPLACE`, cancelling the active downstream work. Subtitle work now uses `ExistingWorkPolicy.KEEP`, translation remains `RUNNING` at 88% until vocabulary owns completion, and duplicate download/Home enqueue calls can no longer replace an active chain.
- ASR, translation, and remote vocabulary LLM calls now refresh their foreground notification and Room heartbeat every 20 seconds while a long network request is in flight. Merged-manifest verification confirms `FOREGROUND_SERVICE_DATA_SYNC`, WorkManager's `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, and `SystemForegroundService` are present, allowing the persisted chain to continue while the app is backgrounded or the screen is off, subject to Android/vendor force-stop and network rules.
- Rebuilt transcript highlighting around a translation-owned word map rather than the learned-vocabulary subset. The translation contract now returns each Chinese subtitle plus `wordPairs` that map every lexical Chinese segment back to an exact English word/phrase and its zero-based repeated occurrence. Results are validated for exact source/target spans and complete Chinese letter/digit coverage before being persisted.
- Added Room schema 12 `SubtitleWordAlignmentEntity` storage with non-destructive 10→11 and 11→12 migrations. The transcript groups multiple Chinese segments linked to one English token, distinguishes repeated English surfaces by occurrence, and uses the stored mapping for click and playback highlighting. Vocabulary-derived mappings remain only as a compatibility supplement for old videos.
- Bumped the vocabulary contract to v13 so videos reprocessed with ASR/word mapping rebuild occurrences rather than retaining platform-caption cue references.
- Focused translation-parser, word-alignment, layout-stability tests and `:app:assembleDebug` pass. The full 105-test run has 104 passes; the previously documented unrelated `TranscriptFollowPolicyTest.prePositionedNextCueStaysUnhighlightedUntilPlaybackStarts` remains the sole failure.
- Pixel 10 Pro verification: schema migrated from 10 through 12 without data loss, 14 video rows remained, the new alignment table and `englishOccurrence` column exist, the solid navigation backing renders as intended, and startup Logcat contains no app fatal exception, ANR, or out-of-memory event. No download, STT, LLM, or translation request was triggered during acceptance.

## Review card navigation clearance and swipe clipping fix (2026-07-23)
- Diagnosed the review card's bottom crowding as asymmetric spacing inside its animated stage: the card had 12dp at the top, only 6dp outside at the bottom, and an additional 4dp downward offset. The effective bottom clearance was therefore only about 2dp, placing the card and its 9dp elevation shadow against the route boundary shared with the bottom-navigation capsule.
- Removed the redundant outer 6dp bottom padding and the card's 4dp downward offset. The animated stage now reserves 12dp above and 16dp below the Surface; the additional 4dp at the bottom compensates for the navigation capsule's upward-cast shadow, so the visible top and bottom gaps appear equal.
- The larger bottom safety area keeps the rotated card corners and elevation shadow inside the NavHost route canvas during horizontal swipes. The navigation backing remains at the previously verified 94dp boundary and its z-order was not changed.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass. The APK was installed on the Pixel 10 Pro AVD; static and slow-drag screenshots confirm the card bottom is raised, the visual gaps are balanced, and the cream navigation backing no longer cuts across the card while dragging. Logcat contains no fatal exception, ANR, or out-of-memory event from the acceptance run.

## Prototype-matched bottom navigation icons (2026-07-23)
- Extracted the three Material Symbols specified by the bottom capsule navigation in `_4/code.html`, `_3/code.html`, and `api/code.html`: `smart_display` for Videos, `school` for Review, and `settings` for Settings.
- Replaced the temporary `▶`, `◇`, and `⚙` text glyphs with six bundled VectorDrawable assets using the exact Material Symbols paths. Each destination now has both an outlined inactive asset and a filled active asset, so rendering does not depend on Google Fonts or network access.
- Matched the prototypes' icon sizing: inactive icons render at 22dp and active icons at 28dp. Existing label typography, capsule colors, navigation height, and the verified 94dp route boundary remain unchanged.
- The full 116-test unit suite and `:app:assembleDebug` pass. Pixel 10 Pro AVD acceptance checked Videos, Review, and Settings individually: the selected destination shows the filled icon in the yellow pill while both unselected destinations show their outlined icons. Logcat contains no fatal exception, ANR, or out-of-memory event.

## Prototype-matched settings icons and simplified video cards (2026-07-23)
- Extracted the settings-list Material Symbols from `stitch_android_implementation_blueprint/api/code.html`: `key`, `psychology`, `graphic_eq`, `menu_book`, `cloud_download`, `cookie`, `palette`, and `text_fields`. The API security row also uses the prototype's `lock` icon and each row now uses the `chevron_right` vector instead of the text glyph `›`.
- Replaced all temporary settings characters (`⌘`, `◉`, `▥`, `▤`, `⇩`, `●`, `◌`, and `Tᵀ`) with bundled local VectorDrawable assets using the exact Material Symbols paths. The settings UI no longer depends on an online icon font.
- Removed the green ready check from ordinary completed video cards on the Home screen. Processing cards still show their spinner, and selection mode still shows the selected/unselected marker so batch selection remains understandable.
- `:app:testDebugUnitTest`, `:app:compileDebugKotlin`, and `:app:assembleDebug` pass. Pixel 10 Pro AVD screenshots verified all settings sections and the simplified Home video cards; Logcat contains no fatal exception, ANR, or out-of-memory event.
