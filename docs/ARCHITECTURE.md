# Architecture

Two Gradle modules, no more:

- **`:core`** (Kotlin/JVM, kotlinx-serialization, coroutines). Domain records and every piece of logic that must be
  deterministic and testable without Android: `ComparisonEngine`, `ResumeGenerator`, `HandoffGenerator`,
  `SensitiveScanner`, `ProjectArchive` (+ `BundleValidator`, `IdRemapper`, `ReferenceIntegrity`), `AiGateway`, `SampleProject`.
- **`:app`** (Android, Compose, Room). `data/` persists records and attachments; `ui/` renders them.

## Data flow
```
UI (Compose) ──calls──▶ BlackboxRepository ──Room tx──▶ blackbox.db
      ▲                        │ └──────────────────────▶ AttachmentStore (filesDir/attachments)
      └── Flow<ProjectBundle> ◀┘
UI ──passes ProjectBundle──▶ :core engines (compare / resume / handoff / archive) ──▶ plain results
```
`ProjectBundle` (all records of one project) is the unit the UI observes and the unit of archive export/import.
Every repository write is a Room transaction that also appends a `TimelineEvent` and bumps the owning
investigation's `revision` (which drives resume-capsule staleness).

## Schema (Room v1, exported to `app/schemas`)
`project`, `investigation`, `session`, `build_reference`, `environment_snapshot` + `environment_field`
(one row per key; `known=false` = explicit unknown), `evidence`, `claim`, `test_plan`, `test_run`
(`planSnapshot` frozen JSON), `timeline_event`, `resume_capsule`, `handoff_draft`.
Child tables cascade on project delete. Small ordered lists (steps, labels, measurements, markers, refs) are JSON columns.

## Privacy boundary
- `AiGateway.authorize()` runs before any provider: global switch on, every context item's project `AI_ALLOWED`,
  size limit. Mixed-project requests with any LOCAL_ONLY record are denied. Responses citing ids outside the
  selected context are discarded. The only provider is `OnDeviceProvider` (LiteRT-LM on CPU) using a model file the
  user imports into `filesDir/models`; without one, requests end `Unavailable`. Records go in as fenced `<record>`
  blocks with short handles (R1…) that `AiPrompt.parse` maps back to ids.
- Backups disabled; no network permission is declared at all.
- Share-sheet import only stages content until the user picks a project. No exported service/receiver/provider.

## Archive format v1
Zip: `attachments/<attachmentPath>`, `bundle.json`, `manifest.json` (written last, holds sha256 of bundle and
every file plus record counts). Reader streams entries, rejects traversal/absolute/backslash/duplicate/undeclared
entries, bounds entry count and total bytes, verifies checksums, validates references, then remaps every id.

## Extension points
- `ProjectCategory` (SOFTWARE / PHYSICAL / COMPARISON) and `Measurement` are where BENCHMATE and SIGNAL ROOM attach.
- `EvidenceSource.INSTRUMENTATION` + share/import path is the hook for owned-app event exports.
- `AiProvider` interface for the single P1 provider; `GenerationMode.AI_ASSISTED` already exists on capsules.
- `Session` records exist in schema for THREADLOCK-style resume across devices.
