# Changelog

## 5.0.0

Everything that was found wrong in 4.0, fixed; the words and the Coach made exact.

**Changed**
- The notebook starts on the day you installed the app (or your earliest page): no wall of blank
  days. The technique handbook sits above the earlier pages; the stamp count includes practice.
- One rule for a clean page everywhere: the Coach did not give the answer. Being shown which digit
  is wrong keeps a page clean. The solved note, share text and restart dialog say so.
- The Coach names the technique it is actually explaining and marks every cell its note mentions.
  Claiming has its own hint.
- One line on the first page explains how to write, until the first digit is written.
- Chinese: difficulties 入门 / 进阶 / 挑战, Coach steps 看 / 想 / 答, a corrected X-Wing
  explanation and more natural wording throughout. "Pencil" is used consistently in both languages.
- Print is "Print a blank copy", with a titled file and the system font for words.
- With very large text the Coach's note grows instead of hiding words behind its buttons.

**Fixed**
- Tapping the Settings back arrow twice no longer closes the app.
- Reopening the page you were on while another one loads keeps your last moves and time.
- A solved practice page comes back after Android reclaims the app; Practice tapped during a load
  opens practice.
- A confirm dialog no longer applies to a different page that opened behind it.
- The widget opens today even from Settings, fits its smallest size, no longer flashes a blank
  page, and turns to the new day at midnight and when the clock or time zone changes.
- A keyboard works on a page opened beside the notebook; "today" follows a new time zone.
- Edge cells near the thumb keep slides away from the back gesture; an interrupted slide leaves no
  highlight behind; system bars match the app's theme on Android 8–9.
- A practice page set aside can no longer come back; a page that graded easier than its level can
  never be dealt twice; a solve just before "Next page" counts towards the level.
- Damaged saves are refused instead of crashing later.

**Under the hood**
- The release APK is checked for network permissions (WorkManager's network-state permission is
  removed) and its signature is verified; release notes carry only that version's section.
- CI no longer leaves a write token where build scripts can read it, and taps an ANR dialog only
  when one is shown. New tests cover each fix, the puzzle cache, the notebook and stored names.

## 4.0.0

A polish release: nothing new to learn, many things that now simply work.

**New**
- New icon: one cell's nine pencil marks, with the one that fits ringed. Themed icon on Android 13+.
- Tomorrow's daily and the next practice page are prepared ahead, so pages open without waiting.

**Fixed**
- A touch the system takes over (an edge back swipe, palm rejection) no longer writes a digit;
  the board's edges are kept clear of the back gesture.
- Restarting a page keeps the Coach's help on record, so it cannot turn into a clean solve; a
  finished page can no longer be restarted.
- A finished page opened again after midnight stays open (it no longer jumps to the new day).
- The open page comes back after Android reclaims the app in the background.
- On a hardware keyboard a typed digit always writes; arrows move on the board only when it has
  focus, so they can reach the tools and the notebook.
- A solve is saved and stamped in one step; a page can no longer end up solved but unstamped.
- A corrupt settings file is replaced instead of failing every later write.
- The widget opens today's page, follows the system's light and dark theme, resizes, shows a real
  preview in the picker and updates when today's page is solved or put away.
- The stamp lands once, not again on rotation or reopening.
- A held digit is put down when its last copy is placed.

**Accessibility**
- TalkBack: one stop per cell, including what the Coach suggests or rules out; solving is
  announced; the Coach's step is spoken; selected pages in the two-pane notebook are announced.
- Cell lines meet 3:1 contrast in Paper and Night; box lines are a clear step darker.
- Large text: the header, status line and work area grow instead of clipping; the stamp no longer
  overflows. Landscape cutouts are respected in the notebook and settings.
- Every key is at least 48dp wide, finished digits included.

**Changed**
- Keys are paper with printed ink; only a held digit is red. The holding line is short and quiet.
- On wide windows the board uses the full height and the header sits above the tools.
- Haptics follow the system's touch-feedback setting; the in-app switch is gone.
- The notebook reads pages without decoding every saved game (database version 3), and saving
  happens off the main thread.

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
