# Testing

## Automated
| Command | Where it ran | Result |
|---|---|---|
| `./gradlew :core:test` | Claude cloud sandbox (JDK 21) | 27/27 pass |
| `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` | GitHub Actions (`android.yml`, job `build`) | see latest run |
| `./gradlew :app:connectedDebugAndroidTest` (`SmokeFlowTest`) | GitHub Actions emulators: API 34 phone (pixel_6) and a pixel_6 resized to the unfolded Fold inner display (2208x1840 @ 420dpi) | first green 2026-10-19 |

`:core` tests (`CoreAcceptanceTest`) cover acceptance D, E, F, G, H, J, K. `:app` Robolectric tests
(`RepositoryWorkflowTest`) cover A, B, duplicate-run prevention, F, H and event-log import through the real Room database.
`SmokeFlowTest` drives the real UI: guide, sample project, workbench, activity recreation (selection survives),
search and the regression checklist. Screenshots per profile are uploaded as `emulator-phone` / `emulator-fold-unfolded`
artifacts. It does not cover real hinge postures (tabletop) or the outer display; those need the device.

## Device verification (not yet done on hardware; emulator smoke covers only the flows listed above)
Run on the Fold with a fresh install:
- [ ] A: airplane mode, full first workflow from README, force-stop, reopen, data intact.
- [ ] B: import a screenshot from Files, delete the original, evidence still opens.
- [ ] C: type a capture draft and a run result, fold/unfold and rotate; text and selection remain; no duplicate run.
- [ ] Tabletop: half-fold on the run screen; steps above the hinge, controls below.
- [ ] Unfolded: project list beside the workbench; compare shows A/B side by side.
- [ ] I: deny microphone; text/photo/import still work and the message explains how to re-enable.
- [ ] Voice note: record, fold during recording, stop; exactly one audio evidence item, playable.
- [ ] Photo: take photo with system camera; cancel path saves nothing.
- [ ] Share a screenshot from another app to "Save to BLACKBOX", pick a project.
- [ ] Archive: export, import, compare counts; open an imported attachment.
- [ ] Font scale 1.3×+ remains usable.
