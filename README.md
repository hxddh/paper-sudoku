# Paper Sudoku · 纸上数独

A Sudoku notebook for Android. Open it and today's page is in front of you; turn back and every
earlier day is there, stamped when it was done. When you are stuck, the Coach writes in the
margin — where to look, then why, and only if you ask, the answer.

一本数独本：打开就是今天这一页，往回翻是过去的每一天，做完的页面盖上印章。卡住时，教练在页边写字：先告诉你看哪里，再告诉你为什么，最后才给答案。

Source: [github.com/hxddh/paper-sudoku](https://github.com/hxddh/paper-sudoku)

## Install

[**下载 / Download the latest APK**](https://github.com/hxddh/paper-sudoku/releases/latest) — Android 8.0+, no `INTERNET` permission.

GitHub builds are *sideload* builds, signed with a CI debug key: uninstall an earlier sideload
build before installing a new one. A Play-signed build comes from a plain `vX.Y.Z` tag once the
signing secrets exist (see [docs/release.md](docs/release.md)).

`applicationId`: `com.titpea.papersudoku`.

## What it is

- **A notebook, not a menu.** Today's 6×6 page on top (gentle on weekdays, steady at the
  weekend), the practice stack, then every earlier day. Stamped pages show their time; there is no
  streak.
- **Practice that follows you.** One never-ending stack of 9×9 pages whose difficulty rises after
  three pages solved without the Coach's answer — singles, then pointing and pairs, then hidden
  pairs and X-Wings.
- **A Coach in the margin.** Look → Why → Answer, with an ink line to the part of the board it is
  talking about. It fills exactly the space of the tools, so the page never moves.
- **Pen and paper.** Printed digits, your ink, pencil notes. Press and slide to aim at a cell
  (a loupe shows where you are); lift to write. Tap a digit with nothing selected to pick it up and
  write it across the page.
- **Quiet.** The clock is hidden while you play and pauses itself when you leave. No ads,
  accounts, analytics or network.

Also: print any page or save it as PDF, share a spoiler-free result, a home-screen widget with
today's page, English and 简体中文, TalkBack, hardware keyboard, tablets and foldables (notebook
and page side by side).

## Build and verify

Requirements: JDK 17, Android SDK platform 37.0 + build-tools 37.0.0, `local.properties` with
`sdk.dir`. Gradle wrapper 9.6. `compileSdk 37`, `targetSdk 36`, `minSdk 26`.

```bash
./gradlew --no-daemon \
  :core:engine:test :core:game:test :app:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest :benchmark:assembleBenchmark \
  :app:lintDebug

# On a device or emulator: UI, accessibility, migration and screenshot-matrix tests,
# then the startup and frame Macrobenchmarks.
./gradlew :app:connectedDebugAndroidTest
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

## Architecture

```
:core:engine   pure Kotlin  techniques, logical and backtracking solvers, generator, grader, Coach hints
:core:game     pure Kotlin  Catalog (dailies, practice), Practice progression, GameState (serializable, undo)
:app           Android      Room + DataStore, PageViewModel, NotebookViewModel, Compose UI, Glance widget
:benchmark     Android test Macrobenchmark: cold start to today's page, writing frames
```

- Home is a Navigation 3 back stack (Notebook, Settings). The notebook is a Material 3 adaptive
  `NavigableListDetailPaneScaffold`: pages list and open page, one pane or two.
- `GameState` is immutable and saved as JSON in Room after every move; results are recorded once
  per puzzle. Room schema 2 is unchanged from 2.0.
- One custom-drawn surface (the board); everything else is Material 3 / Expressive.

Docs: [design](docs/design.md) · [Android checklist](docs/android-standards.md) ·
[release](docs/release.md) · [store listing](docs/store-listing.md) · [privacy](PRIVACY.md) ·
[changelog](CHANGELOG.md)

## License notes

Fraunces is © Undercase Type, SIL Open Font License 1.1
([third_party/fraunces/OFL.txt](third_party/fraunces/OFL.txt)); the app bundles a Latin subset.
