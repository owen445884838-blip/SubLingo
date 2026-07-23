# Source distribution for SubLingo

SubLingo is licensed under GPL-3.0-or-later. Every public binary release must
link to the complete corresponding source for that exact version, including:

- the SubLingo source and Gradle build scripts;
- the commit/tag used to produce the APK/AAB;
- local modifications, if any;
- release configuration needed to rebuild the binary, excluding private
  signing keys and passwords;
- license and notice files for packaged third-party components.

The release page should link to the public repository and immutable tag. If a
binary is distributed somewhere that cannot provide the source alongside it,
include a durable written source offer that satisfies GPL section 6 and is
valid for the required period.

The generated SBOM is an inventory aid and does not replace the obligation to
provide corresponding source or retain upstream notices.
