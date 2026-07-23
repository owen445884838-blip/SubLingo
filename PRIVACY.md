# SubLingo Privacy Notice

Last updated: 2026-07-23

SubLingo is a local-first, open-source Android application for studying English with videos. It does not include an advertising SDK, analytics SDK, or an application-operated account service.

## Data stored on the device

- Imported and downloaded video/audio files, generated thumbnails, subtitles, vocabulary, review history, and settings are stored in the app's private storage and Room database.
- LLM/STT API keys and download cookies are encrypted with an Android Keystore-backed AES-GCM key. They are not stored as plaintext in Room.
- Android backup is disabled for the application.

## Data sent to third parties

When the user starts processing, relevant content is sent directly from the device to the providers the user configured:

- STT providers receive audio chunks to create English transcripts.
- LLM providers receive subtitle text and nearby context for translation, alignment, and vocabulary extraction.
- Dictionary providers may receive a normalized English word when it is unavailable in the bundled/offline dictionary.
- Supported video platforms receive normal download requests made by yt-dlp. User-provided cookies may be attached when needed for an authenticated request.

SubLingo does not control these providers. Their own privacy policies, retention rules, pricing, and terms apply. Users should avoid processing sensitive or confidential media with a provider they do not trust.

## Logging and temporary files

- Release builds disable the OkHttp request logger and never return raw yt-dlp output through WorkManager results.
- Logs are intended to contain task identifiers, stages, counts, and sanitized error summaries—not API keys, cookies, authorization headers, audio Base64, or complete request bodies.
- Temporary cookie files are deleted after each request and again on app startup. Completed pipeline audio chunks are removed after the final vocabulary stage. Old `.part` files are removed after 24 hours.

## Deletion

Deleting a video from SubLingo removes its Room-backed subtitles/vocabulary through foreign-key cascades and deletes its private media directory. Uninstalling the application removes its private application data according to Android behavior.

## Content responsibility

SubLingo is intended for personal learning with content the user is permitted to access and process. It does not bypass DRM or paid-content protection. Users are responsible for complying with copyright, platform terms, and provider terms.

## Contact and source

Privacy issues can be reported through the public source repository's issue tracker once the release repository URL is published.
