# Paper Sudoku · 纸上数独

A Sudoku notebook for Android. Open it and today's page is in front of you; turn back and every
earlier day is there, stamped when it was done. When you are stuck, the Coach writes in the
margin — where to look, then why, and only if you ask, the answer.

一本数独本：打开就是今天这一页，往回翻是过去的每一天，做完的页面盖上印章。卡住时，教练在页边写字：先告诉你看哪里，再告诉你为什么，最后才给答案。

Source: [github.com/hxddh/paper-sudoku](https://github.com/hxddh/paper-sudoku)

## Install

[**下载 / Download the latest APK**](https://github.com/hxddh/paper-sudoku/releases/latest) — Android 8.0+, no `INTERNET` permission.

GitHub builds are *sideload* builds. From 9.0 they can be signed with a fixed sideload key (set up
as repository secrets; the release notes give its certificate fingerprint), so later builds update
in place; moving from an earlier build signed with a CI debug key needs one last uninstall. A Play-signed build comes from a plain `vX.Y.Z` tag once the
signing secrets exist (see [docs/release.md](docs/release.md)).

`applicationId`: `com.titpea.papersudoku`.

## What it is

- **A notebook, not a menu.** Today's 6×6 page on top, the practice card ("Learning: X-Wing ·
  Known: 6 of 12"), and one calendar where every page you wrote sits on its day, 9×9 pages
  included. No settings, no streak.
- **One path that knows you.** One never-ending stack of 9×9 pages that grows harder after three
  pages solved without the Coach's answer (the levels stay behind the scenes) — singles, then pointing and pairs, then hidden pairs
  and X-Wings, then (Deep) triples, Swordfish and XY-Wing. Each page is dealt to need what you are
  about to learn, judged from the digits you wrote with each technique yourself, and every few
  pages it goes back to one you found long ago. No scores: a solved page says what you found.
- **Pages of your own.** Type in a puzzle from a book or newspaper; it is checked for one
  solution, graded and coached.
- **A Coach on the page.** Look → Why → Answer, above keys that keep working, with an ink line to the part of the board it is
  talking about; when a placement needs several eliminations, Why walks through each one. It reads
  your page: a pencil mark that leaves out a cell's answer comes first, steps near your cell come
  before others, and it is brief about what you already know. Its marks stay on the board until
  you write in the cell it means.
- **Pen and paper.** Printed digits, your ink, pencil notes ("Fill every candidate" in pencil
  mode on a 9×9 page). Press and slide to aim at a cell (a loupe shows where you are);
  lift to write. Tap a digit with nothing selected to pick it up and write it across the page.
- **A handbook that teaches.** Every technique with a worked example from a
  real page, drawn the way the Coach draws it, a page each. Each technique shows how well you know
  it (seen, practised, known) from the digits you wrote with it yourself.
- **Read a page back.** Every digit of a solved page in order, with the technique that justified
  it then, the slips, and where the Coach helped.
- **Quiet.** The clock is hidden while you play and pauses itself when you leave. No ads,
  accounts, analytics or network.

Also: print a blank copy of any page or save it as PDF, share a spoiler-free result, a home-screen widget with
today's page, English, 简体中文 and 繁體中文, TalkBack, hardware keyboard, tablets and foldables (notebook
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

The screenshot matrix is compared with the goldens in `app/src/androidTest/screenshots`
(`tools/compare_screenshots.py`, half size, small tolerance). For a pull request CI also pushes the
reduced captures to `captures/pr-<number>`; after an intended visual change, fetch that branch,
review the images and commit them as the new goldens.

## Architecture

```
:core:engine   pure Kotlin  techniques, logical and backtracking solvers, generator, grader, Coach hints
:core:game     pure Kotlin  Catalog (dailies, practice), Practice progression, GameState (serializable, undo)
:app           Android      Room + DataStore, PageViewModel, NotebookViewModel, Compose UI, RemoteViews widget
:benchmark     Android test Macrobenchmark: cold start (with and without the Baseline Profile),
               writing frames; a smoke test of the minified build; a Baseline Profile generator
```

- Home is a Navigation 3 back stack (Notebook, Handbook, Review, Type in, Licences). The notebook is a Material 3 adaptive
  `NavigableListDetailPaneScaffold`: pages list and open page, one pane or two.
- `GameState` is immutable and saved as JSON in Room within 0.4s of a move and on leaving, with the printed and
  current digits beside it for the notebook (schema 4: the JSON last, indexed by kind and day); a solve is saved and recorded in one
  transaction, once per puzzle. Upcoming pages are generated ahead into a disk cache.
- One custom-drawn surface (the board, drawn in one pass with 81 semantics nodes); everything else is Material 3 under the app's own tokens (docs/design.md).

Docs: [design](docs/design.md) · [Android checklist](docs/android-standards.md) ·
[release](docs/release.md) · [store listing](docs/store-listing.md) · [privacy](PRIVACY.md) ·
[changelog](CHANGELOG.md)

## License notes

Fraunces is © Undercase Type, SIL Open Font License 1.1
([third_party/fraunces/OFL.txt](third_party/fraunces/OFL.txt)); the app bundles a Latin subset. Icons are
Material Symbols Rounded (Apache 2.0). Both licences, and AndroidX's, are shown in full in the app
(the foot of the notebook → Licences).
