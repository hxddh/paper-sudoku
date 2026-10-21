# Changelog

## 3.0.0

Paper Sudoku is rebuilt around one idea: a notebook of pages.

**New**
- The notebook is home: today's page, the practice stack, every earlier day (stamped with its time
  when done) and the technique handbook. On tablets and unfolded foldables the notebook and the
  open page sit side by side (Material 3 adaptive list-detail), with a shared-element transition
  between a page's thumbnail and its board and predictive back.
- Press and slide to aim on the board, with a loupe; the cell is written where the finger lifts.
- Digit-first input without a long press: tap a digit with nothing to write into to pick it up.
- The Coach writes in the margin, in the space the tools use, with an ink line to the board.
- Practice difficulty rises by itself after three clean pages per level.
- Material 3 Expressive floating toolbar and connected keypad; finished digits shrink to a check.
- Home-screen widget with today's page.
- Baseline Profile, Macrobenchmark (cold start, writing frames) and a screenshot matrix in CI.

**Removed**
- The puzzle picker, the separate journal, Classic difficulty choice, the visible timer, manual
  pause, the mistake-marking switch, Material You colors and long-press digit lock.

**Changed**
- Numerals are Fraunces; all other text uses the platform sans.
- Settings: theme, tidy notes, haptics, and the system's per-app language.
- Existing 2.0 games, results and settings carry over unchanged.


## 2.0.0

Paper Sudoku becomes a notebook that teaches.

**New**
- Three-step Coach: Look → Why → Answer, with on-board marks (focus, pattern, crossed-out
  candidates, faint answer). Points out a wrong digit before anything else.
- Technique ladder: hidden/naked singles, pointing, claiming, naked and hidden pairs, X-Wing.
- On-device puzzle generator and grader. Dailies are derived from the date (same for everyone,
  offline); Classic has Gentle, Steady and Tricky with an unlimited numbered sequence.
- Journal: stamped calendar (play any past day), pages and solved counts, best times, technique
  handbook.
- Paper design: Fraunces type, printed/pen/pencil digit layers, paper grain (Android 13+), ink
  stamp, full Paper and Night color roles, optional Material You colors.
- Print a puzzle or save it as PDF; share a spoiler-free result.
- Digit lock (long-press a key), remaining-digit counts, same-digit highlight, tidy notes.
- Pause (hides the board); optional mistake marks; haptics; splash screen; keyboard shortcuts
  helper; predictive back for the Coach and screens.
- Adaptive layout for landscape, tablets, foldables and desktop windows.

**Fixed**
- Progress is saved continuously and survives closing the app, reboots and updates.
- Switching modes or starting a new game no longer discards progress without asking.
- Best times are per mode and difficulty and ignore games where the Coach gave the answer.
- A puzzle counts once; replays do not inflate stats.
- The timer counts only while the board is visible and no longer drifts after solving.
- No baseline-purple surfaces: every Material color role is defined for Paper and Night.
- Notes are legible on 9×9 (about 9sp instead of 6sp).
- TalkBack now reads notes and reports row/column spans correctly.

**Changed**
- Android backup now includes the journal and settings (the app itself still has no network).
- Store short description fits Play's 80-character limit.

## 1.0.0

First release: 61 daily 6×6 puzzles, 31 Classic 9×9 puzzles, a two-technique Coach, notes,
undo, timer, local stats, Paper/Night theme.
