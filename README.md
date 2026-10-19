# BLACKBOX

**Your projects remember what happened, what changed, and what to do next.**

A fold-native Android workbench for troubleshooting your own apps and devices:
capture evidence → form a question → plan a test → record the result → compare → resume or hand off.
Everything is local. No account, server, subscription or AI key is needed.

Status: first vertical slice (v0.1.0, debug). See [docs/PLAN.md](docs/PLAN.md) for what is done and verified.

## Install
1. Open the latest successful **Android CI** run under the repo's *Actions* tab.
2. Download the `blackbox-debug-apk` artifact, unzip it, and install `app-debug.apk`
   (enable "Install unknown apps" for your browser/file manager, or `adb install -r app-debug.apk`).
3. The debug build installs as `com.tdater.blackbox.debug` beside any future release build.

## First workflow (about 3 minutes)
1. **New project** → title it (e.g. "KITT launcher"). It is LOCAL ONLY by default.
2. **Start investigation** → "OBD values freeze after screen-off".
3. ⋮ → **Builds & environments** → add a build; *Fill from this phone* → mark anything you didn't check as **Unknown** → *Save snapshot*.
4. Back in the investigation → **Capture** a note, photo, imported screenshot/log, or voice note.
5. **New test plan** → steps + expected result → **Start run** → mark events, record the actual result → pick an outcome → confirm.
6. Do a second run after a change, then **Compare runs**, **Resume**, and **Fix request** (copy/save/share Markdown).
7. ⋮ → **Export project archive** to back it up; import it from the Projects screen.

Want to poke around first? On an empty install, tap **Load labeled sample (synthetic data)**.

## Help inside the app
Tap the ⓘ icon on Projects (or Settings → Guide) for what BLACKBOX is for, step-by-step instructions, a worked example and an FAQ. It's offline like everything else; the text lives in `ui/screens/HelpScreen.kt` (`HelpContent`).

## Build
```
./gradlew :core:test                      # pure-Kotlin domain tests, no Android SDK needed
./gradlew :app:testDebugUnitTest :app:assembleDebug   # needs an Android SDK (API 35)
```
JDK 17+, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1. minSdk 29, target 35.

## What it is not
Not a chat app, not a recorder that runs in the background, not a remote control for other apps,
and not an AI that "knows" root causes. No AI provider ships in this build.

Docs: [ARCHITECTURE](docs/ARCHITECTURE.md) · [DECISIONS](docs/DECISIONS.md) · [TESTING](docs/TESTING.md) · [PLAN](docs/PLAN.md)
