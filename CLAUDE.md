# BLACKBOX — working notes for Claude

Fold-native Android investigation workbench. Source of truth for product scope: the BLACKBOX brief
(project files). This file holds enduring invariants and commands.

## Invariants (do not break)
- Offline-first. No account, network, AI key or server is needed for any P0 workflow.
- "Unknown" is a value (`EnvValue.Unknown`), never collapsed into blank or "unchanged".
- Comparison, resume and handoff are deterministic and live in `:core` (no Android, no AI).
- Claims carry a classification (OBSERVED / INFERRED / SUPPORTED_RESULT / UNKNOWN); review ≠ certainty.
- Environment snapshots and resume capsules are insert-only. Plan edits create a new revision; runs keep a frozen `PlanSnapshot`.
- Evidence deletion is a soft withdraw (record kept, file removed) so claims never silently cite missing proof.
- Attachments are copied into app storage (`filesDir/attachments/<projectId>/<evidenceId>/<name>`); external URIs are never stored.
- Archive import always creates a new project (ids remapped); never overwrite.
- LOCAL_ONLY is enforced in `AiGateway.authorize`, not by hiding UI. It applies to the on-device model too.
- The only AI provider is on-device LiteRT-LM (`app/.../ai/OnDeviceProvider.kt`) with a user-imported model (`ModelStore`). No INTERNET permission, no keys. AI output is saved only as INFERRED claims with author "ai".
- No analytics/telemetry/ads; Android backup disabled (`allowBackup=false`, data extraction rules exclude all).
- Room schema changes require a `Migration`; no destructive migration. Schemas exported to `app/schemas`. `MigrationTest` covers v1 → v2.
- The database is SQLCipher-encrypted (`DbEncryption`, passphrase wrapped by Android Keystore). Plaintext DBs are converted once, verified before swap; on failure the plaintext DB stays usable.
- INTERNET is stripped with `tools:node="remove"` and CI fails if the standard APK requests it. Only the `net` flavor (`src/net/AndroidManifest.xml`, `BuildConfig.NET_TOOLS`, app id suffix `.net`) adds it, for `NetTools`. Libraries (ML Kit, LiteRT) run fully on-device.
- Derived records (text read from a photo, scanned codes) are new evidence with `derivedFromId`; the source is never modified.
- Dictation uses only the on-device SpeechRecognizer (API 31+); no cloud fallback.
- UI vocabulary goes through `LocalLingo` (EVERYDAY default, TECHNICAL optional); colours through `LocalPalette`/`BbColors`.

## Layout
- `core/` pure Kotlin/JVM: `model`, `compare`, `resume`, `handoff`, `archive`, `ai`, `sample`, `search` (incl. `SimilarRecords`), `regression`, `integrations` (owned-app event log import, docs/EVENT_FORMAT.md), `templates`, `readings`, `report`, `procedures`.
- `app/` Android: `data/` (Room, AttachmentStore, Repository, DbEncryption), `system/` (reminders, widget/tile, backup folder, PDF, phone readings, Vision OCR/barcodes, dictation, app lock), `ui/` (Compose screens, adaptive layout).

## Commands
- Core only (works without Android SDK): `./gradlew :core:test`
- Full (needs ANDROID_HOME / local.properties): `./gradlew :core:test :app:testStandardDebugUnitTest :app:assembleDebug` (builds standard and net)
- APKs: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`, `app/build/outputs/apk/net/debug/app-net-debug.apk`
- CI: `.github/workflows/android.yml` builds and uploads the APK artifact on every push/PR.
- The Claude cloud sandbox cannot reach dl.google.com (Google Maven), so `:app` is built in CI there; `settings.gradle.kts` only includes `:app` when an SDK is present.
