# Paper Sudoku

A calm, teaching-first Sudoku app for Android. The phone-first path is a 6×6 daily mini with a
light Coach when you are stuck; 9×9 remains available as Classic.

The source lives at [github.com/hxddh/paper-sudoku](https://github.com/hxddh/paper-sudoku).

`applicationId`: `com.titpea.papersudoku` — permanent once a build is published.

## v1 product scope

- 61 daily 6×6 puzzles, rotating deterministically by local date
- 31 Classic 9×9 puzzles; “New game” advances through the Classic bank
- Coach teaches naked single and hidden single, with technique-specific copy
- Notes, undo, erase, timer, solved overlay with elapsed time, local stats (completed + best)
- Paper light/dark theme; saved theme; edge-to-edge Material 3 layout
- Expanded (≥840dp) two-pane layout: board + dock / keypad / Coach rail
- Hardware keyboard: arrows move, digits enter, Backspace erases, N notes, H coach
- TalkBack cell semantics plus Notes / Erase / Coach custom actions
- `SavedStateHandle` in-progress restore; Room history; DataStore settings
- No accounts, ads, analytics, notifications, billing, or `INTERNET` permission

The platform checklist is in [docs/android-standards.md](docs/android-standards.md). Release and
store procedures are in [docs/release.md](docs/release.md) and
[docs/store-listing.md](docs/store-listing.md). The privacy policy draft is [PRIVACY.md](PRIVACY.md).

## Requirements

- JDK 17
- Android SDK platform 36 + build-tools 36.0.0
- Gradle wrapper included (`8.13`)
- `local.properties` with your SDK path:

```properties
sdk.dir=/path/to/Android/Sdk
```

## Build / verify

```bash
./gradlew --no-daemon \
  :core:engine:test \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Release R8/resource shrinking can be checked without keys:

```bash
./gradlew --no-daemon :app:assembleRelease
# or with the four PAPER_SUDOKU_* signing variables from docs/release.md:
./gradlew --no-daemon :app:bundleRelease
```

An unsigned release artifact is not a Play upload. See [docs/release.md](docs/release.md).

UI tests need a device/emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions runs JVM tests, debug/release builds, lint, and a separate API 35 emulator job for
the Compose/accessibility tests.

## Implementation

- `MainActivity`: edge-to-edge Compose, DataStore theme, system bar appearance, stats collection.
- `GameViewModel`: unidirectional state, daily/Classic selection, notes, undo, erase, Coach,
  timer persistence, one completion record per game.
- `Puzzles`: compact, validated daily/Classic bank. `PuzzleBankTest` proves every entry has one
  solution and is solvable with the two Coach techniques.
- `SudokuEngine`: candidates, naked/hidden singles, conflicts, solved check.
- `SudokuBoard`: Canvas grid + Text digits, collection semantics, custom actions, test tags.
- `GameScreen`: compact `BottomSheetScaffold` and expanded two-pane layouts; timer; stats dialog;
  keyboard handling; solved overlay.
- `SudokuTheme`: Paper light/dark tokens, type, shapes.
- Room: local `game_results` history; DataStore: theme.

Tests currently cover the engine, ViewModel, puzzle bank, and Compose/accessibility UI. The
instrumentation suite is wired into CI; local orb runs cannot execute it because the orb has no
emulator/KVM.

## Not in v1

- Accounts, cloud sync, ads, analytics, notifications, billing/IAP
- Streak systems or social features
- A larger technique curriculum beyond naked/hidden single
- Medium-width (600–839dp) two-pane layout; it keeps the bottom sheet to preserve board size
- Bundled custom display fonts (uses platform serif/sans)
- Play publication itself: the upload key, Play Console, hosted privacy URL, and store assets
  require the app owner
