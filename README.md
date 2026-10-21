# Paper Sudoku · 纸上数独

A calm Sudoku notebook for Android that teaches. One 6×6 page a day, Classic 9×9 when you have
time, and a Coach that shows you *where to look* and *why* before it ever gives an answer.

一本会教你的数独练习本：每天一页 6×6，有空再做 9×9；教练先告诉你“看哪里”“为什么”，最后才给答案。

Source: [github.com/hxddh/paper-sudoku](https://github.com/hxddh/paper-sudoku)

## What makes it different

- **A Coach that teaches, in three steps.** *Look* tints the row, column or box to study. *Why*
  draws the pattern and crosses out the candidates it removes. *Answer* writes the digit only when
  you ask. If one of your digits is wrong, the Coach says so before anything else.
- **Every puzzle can be explained.** Puzzles are generated on the device and graded by the same
  technique ladder the Coach teaches: hidden/naked singles, pointing, claiming, naked and hidden
  pairs, X-Wing. Nothing needs guessing.
- **Paper, not a feed.** Printed givens (Fraunces), your digits in ink-blue italic, pencil notes,
  a faint paper grain, and an ink stamp when a page is done. The journal stamps solved days —
  filled for a clean page, a ring when the Coach gave an answer. There is no streak.
- **Literally paper.** Print any puzzle (or save it as PDF) through the system print service.
- **Private by construction.** No `INTERNET` permission, accounts, ads or analytics. The same date
  gives everyone the same Daily, offline.

## Features

- Daily Mini 6×6 (Gentle on weekdays, Steady on weekends); any past day is playable from the journal
- Classic 9×9 in Gentle / Steady / Tricky, with an unlimited, numbered sequence per difficulty
- Autosave of every game; resume after closing, rebooting or updating
- Notes with tidy-up, undo, erase, same-digit highlight, remaining-digit counts
- Digit lock: long-press a key, then tap cells to write it
- Honest timer: counts only while the board is on screen; tap to pause (the board is hidden)
- Optional mistake marks (off by default), conflicts always underlined (not color alone)
- Journal: stamped calendar, pages and solved counts, best times (Coach answers excluded), technique handbook
- Paper and Night themes, optional Material You colors, haptics
- Adaptive layout: tools beside the board on wide windows (landscape, tablets, desktop)
- TalkBack semantics with notes and custom actions; hardware keyboard with a shortcuts helper
- English and 简体中文

## Install

The 2.0 APK is attached to the GitHub release when the `v2.0.0` tag is built (see
[docs/release.md](docs/release.md)). Android 8.0+.

The 1.0 sideload APK was signed with a local debug key, so 2.0 builds signed with the release key
install alongside it only after uninstalling 1.0 (Android refuses an update with a different
signature). Play installs are unaffected.

`applicationId`: `com.titpea.papersudoku` — permanent once a build is published.

## Requirements

- JDK 17
- Android SDK platform 37.0 + build-tools 37.0.0
- Gradle wrapper included (`9.6`)
- `compileSdk 37` (required by Material 3 `1.5.0-beta01`), `targetSdk 36`, `minSdk 26`
- `local.properties` with `sdk.dir=/path/to/Android/Sdk`

## Build and verify

```bash
./gradlew --no-daemon \
  :core:engine:test :core:game:test \
  :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug

# Compose UI, accessibility and Room migration tests (device or emulator):
./gradlew :app:connectedDebugAndroidTest
```

The two `:core` modules are plain Kotlin/JVM and need no Android SDK.

## Architecture

```
:core:engine   pure Kotlin  geometry, techniques, logical + backtracking solvers, generator, grader, Coach hints
:core:game     pure Kotlin  PuzzleSpec + Catalog (deterministic dailies/classics), GameState (serializable, undo)
:app           Android      Room + DataStore, GameViewModel, Compose UI (play, journal, settings), print/share
```

- `GameState` is immutable; every move is a pure function, saved as JSON in Room (`saved_games`).
- `GameViewModel` owns the active puzzle, the clock (monotonic, foreground-only), the Coach session
  and one-off haptic effects. Results are recorded once per puzzle key.
- Room schema 2 (exported under `app/schemas`) migrates v1 results in place.
- UI: Navigation 3 back stack (Play → Journal / Settings), Material 3 Expressive theme, one
  custom-drawn surface (the board).

Docs: [design](docs/design.md) · [Android checklist](docs/android-standards.md) ·
[release](docs/release.md) · [store listing](docs/store-listing.md) · [privacy](PRIVACY.md) ·
[changelog](CHANGELOG.md)

## Not in 2.0

- Accounts, sync, ads, analytics, notifications, billing, leaderboards, streaks
- Techniques beyond X-Wing (chains, swordfish); puzzles needing them are never generated
- A home-screen widget and stylus handwriting (candidates for 2.1)

## License notes

Fraunces is © Undercase Type, licensed under the SIL Open Font License 1.1
([third_party/fraunces/OFL.txt](third_party/fraunces/OFL.txt)); the app bundles a Latin subset.
