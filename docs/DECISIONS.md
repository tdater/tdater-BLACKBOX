# Decisions

| Decision | Why |
|---|---|
| Two modules (`:core` JVM + `:app`) | Domain logic testable without Android; brief asks to keep compare/handoff independent of Compose and AI. |
| Room + explicit entities, JSON only for small ordered lists | Structured, migratable storage; env fields are rows so unknown is queryable. |
| Photos via system camera intent (TakePicture), not CameraX | No CAMERA permission needed, fewer failure modes; CameraX can come later for tabletop framing. |
| Voice via MediaRecorder held in an activity-scoped ViewModel + `configChanges` | Fold/rotate never restarts or duplicates a recording. Limitation: recording stops if the process is killed; no foreground service yet. |
| Soft-withdraw evidence | Keeps claim references explainable (acceptance E). |
| Insert-only capsules and env snapshots | Historical records never silently rewritten (F, snapshot immutability). |
| Archive import = new project, ids remapped | Cannot overwrite existing data. |
| On-device AI only (LiteRT-LM, user-imported model) | No keys, no cost, no INTERNET permission. Models are imported, not downloaded, so the app stays offline. Cloud providers stay out: no safe key handling without a backend. |
| LOCAL_ONLY also blocks the on-device model | One rule users can trust; flipping a project to AI ALLOWED is an explicit, confirmed step. |
| Text caps (200k inline, 50k fields) | A pasted multi-MB log overflows to a text attachment instead of bloating the row or the UI. |
| Dark theme only | Brief asks for a legible dark workbench; one theme to verify. |
| Debug `applicationIdSuffix .debug` | Coexists with a future signed release. No production signing key invented. |
| APK built in GitHub Actions | The Claude cloud sandbox blocks dl.google.com (Google Maven + SDK). `:core` builds anywhere. |
| minSdk 29 | Covers any phone Travis would test on; avoids legacy storage paths. |
