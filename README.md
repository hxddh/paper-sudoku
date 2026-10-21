# Sudoku Android Skeleton

A small, buildable Compose skeleton for the Sudoku product direction:

- 6×6 daily mini (default) and 9×9 classic mode
- Edge-to-edge Material 3 screen with `BottomSheetScaffold` keypad / Coach sheet
- Expanded (≥840dp) two-pane layout: board + 360dp dock / keypad / Coach rail
- Custom board with `CollectionInfo` / `CollectionItemInfo` semantics
- Pure Kotlin engine: candidates, naked single, conflicts, solved
- Paper light/dark theme with accessible accent/muted tokens
- Notes, undo, erase, Coach, Place, solved overlay
- `SavedStateHandle` in-progress restore; Room completion stats; DataStore theme
- No ads, no analytics, no `INTERNET` permission

The detailed platform checklist is in [docs/android-standards.md](docs/android-standards.md).

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
./gradlew :core:engine:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

The 3 Compose UI tests need a connected device/emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## What is implemented

- `MainActivity`: `enableEdgeToEdge()` + Compose; DataStore theme; Room completion count.
- `GameViewModel`: unidirectional state, 6×6/9×9 toggle, notes, undo, erase, coach;
  `SavedStateHandle` restores values/notes/selection/mode/timer/hint counter after process death.
- `StatsRepository`: Room `game_results` table; one row per completed game, recorded once.
- `SudokuBoard`: Canvas grid lines + Text digits + per-cell semantics and test tags.
- `GameScreen`: `TopAppBar`, compact `BottomSheetScaffold`, 3×2/3×3 keypad; Expanded two-pane rail.
- `SudokuTheme`: Paper light/dark color roles, typography, shapes.
- English + Simplified Chinese strings; `localeConfig`.
- Manifest: target 36, no orientation lock, `enableOnBackInvokedCallback`, no network.
- Tests: 5 engine + 7 ViewModel JVM tests pass; 3 Compose UI tests (AccessibilityChecks) compile.

## Intentionally not implemented yet

- Device-level rotation/process-death verification and TalkBack testing (no emulator in this orb)
- Undo-history persistence; Room migrations/exported schema; stats screen and best times
- Medium-width (600–839dp) two-pane layout; it keeps the bottom sheet to preserve board size
- Bundled Fraunces/Figtree fonts (skeleton uses platform serif/sans)
- Full technique curriculum / difficulty grader / puzzle-bank validator
- Keyboard/TalkBack custom actions beyond the basic grid semantics
- Billing, notifications, share sheet, in-app review
- Screenshot tests, macrobenchmark, CI

## Dependency note

The project is pinned to a known-good stable set (`Compose BOM 2025.10.00`, Material 3 1.4.0,
AGP 8.13.2, Kotlin 2.2.21) so it builds with `compileSdk 36`.

Current 2026 AndroidX artifacts (Compose 1.12 / Material3 1.5) require AGP 9.1+ and
`compileSdk 37`. Upgrade path: AGP 9.4.x → `compileSdk 37` → Compose BOM 2026.09.00 →
Material3 1.5 / connected `ButtonGroup`. `targetSdk` can stay 36 until Play requires 37.

## Structure

```text
app/
  src/main/kotlin/com/example/sudoku/
    MainActivity.kt
    data/Puzzles.kt
    data/SettingsRepository.kt
    data/StatsRepository.kt
    designsystem/Theme.kt
    game/GameViewModel.kt
    game/GameScreen.kt
    game/SudokuBoard.kt
  src/main/res/...
  src/test/...                 # ViewModel tests
  src/androidTest/...          # Compose UI / accessibility tests
core/engine/
  src/main/kotlin/.../engine/  # pure Kotlin engine
  src/test/kotlin/...          # engine tests
docs/android-standards.md
```
