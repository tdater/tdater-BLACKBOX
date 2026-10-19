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
- LOCAL_ONLY is enforced in `AiGateway.authorize`, not by hiding UI. No provider ships yet.
- No analytics/telemetry/ads; Android backup disabled (`allowBackup=false`, data extraction rules exclude all).
- Room schema changes require a `Migration`; no destructive migration. Schemas exported to `app/schemas`.

## Layout
- `core/` pure Kotlin/JVM: `model`, `compare`, `resume`, `handoff`, `archive`, `ai`, `sample`, `search`, `regression`, `integrations` (owned-app event log import, docs/EVENT_FORMAT.md).
- `app/` Android: `data/` (Room, AttachmentStore, Repository), `ui/` (Compose screens, adaptive layout).

## Commands
- Core only (works without Android SDK): `./gradlew :core:test`
- Full (needs ANDROID_HOME / local.properties): `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- CI: `.github/workflows/android.yml` builds and uploads the APK artifact on every push/PR.
- The Claude cloud sandbox cannot reach dl.google.com (Google Maven), so `:app` is built in CI there; `settings.gradle.kts` only includes `:app` when an SDK is present.
