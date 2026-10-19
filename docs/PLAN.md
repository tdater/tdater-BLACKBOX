# Plan & status

Legend: ✅ implemented + automated test · 🟡 implemented, needs device verification · ⬜ planned

## Phases
| # | Phase | Status |
|---|-------|--------|
| 1 | Inspect env, plan, repo scaffolding | ✅ |
| 2 | Vertical slice: project → investigation → evidence → plan → run → outcome, persisted | ✅ repo tests · 🟡 UI on device |
| 3 | Evidence: text/log/measurement, file import, photo (system camera), voice (MediaRecorder) | 🟡 |
| 4 | Fold: compact/two-pane workspace, tabletop split on run screen, saved state | 🟡 |
| 5 | Compare, resume capsules, handoff, archive round-trip | ✅ core tests · 🟡 UI |
| 6 | Regression checklist, search/filter, polish | 🟨 search + regression checklist built, core-tested; polish pending |
| 7 | Optional AI provider | ⬜ gateway + policy tests only, no provider |
| 8 | Release handoff (signed build) | ⬜ no production key by design |

## Acceptance scenarios (brief §15)
| | Scenario | Status |
|-|----------|--------|
| A | Offline investigation, relaunch intact | ✅ `RepositoryWorkflowTest.offlineInvestigationSurvivesRelaunch` · 🟡 device |
| B | Managed attachment survives loss of original | ✅ `importedAttachmentSurvivesLossOfOriginal` · 🟡 with real SAF provider |
| C | Fold continuity, no duplicate run/capture | ✅ duplicate-run guard tested · 🟡 fold/rotate on device |
| D | Unknown/missing ≠ unchanged; incompatible units | ✅ core tests |
| E | Claims flagged when evidence withdrawn | ✅ core test · 🟡 UI warning |
| F | Capsule goes stale without being rewritten | ✅ core + repo tests |
| G | Excluded evidence absent from handoff | ✅ core test |
| H | Archive round-trip + traversal rejected | ✅ core + repo tests |
| I | Camera/mic denial leaves other capture working | 🟡 (no CAMERA permission used at all) |
| J | LOCAL_ONLY never reaches provider, incl. mixed requests | ✅ core tests |
| K | Timeout / malformed / invented IDs fail honestly | ✅ core tests |
| L | Labeled synthetic OBD sample | ✅ `SampleProject`, flagged `isSample` |

## Next
1. Device pass on the Fold (checklist in TESTING.md), fix what breaks.
2. ~~Search/filter across records; regression checklist view.~~ Done (`core/search`, `core/regression`, Search + Regression screens). Fail-then-pass only *suggests* a regression check; nothing is auto-added.
3. ~~Owned-app event import format (JSON) for KITT/RetroTV instrumentation.~~ Done: `core/integrations`, docs/EVENT_FORMAT.md, Quick capture → Import app event log.
