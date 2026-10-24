# Changelog

## 10.0.0

A notebook that knows you: practice follows what you have shown you can do, the Coach reads your
page, and every page opens at once.

**Changed**
- What you know is what you wrote. A technique counts as practised, then known, from the digits you
  wrote with it yourself, read back when the page is finished; the Coach explaining it there does
  not count. Pages that only needed a technique no longer do. Your 9.0 marks carry over as a start.
- One path. Practice is the only 9×9 stack: each page is dealt to need what you are about to learn,
  and every few pages it goes back to a technique you found long ago. The levels stay.
- The Coach reads your page. It first points out a pencil mark that leaves out a cell's answer (and
  puts it back if you ask), prefers a step near the cell you are on, says one line about
  techniques you know, and opens with what a technique is the first time it explains it.
- A solved page says one true thing about it: "X-Wing: you found it yourself."
- One notebook: today, practice (with "Next: X-Wing"), the contents, and the pages you wrote, now
  including every finished 9×9 page. The handbook is a page per technique, board first; read back
  and type in are pages, not lists. "This week" and "Practise this" are gone as entries; pages
  you began from them are still in the notebook.

**Faster**
- Every page, today's included, is dealt in one step from a precomputed table (the same puzzles
  as before), so nothing waits on the generator.
- Saves are about a seventh of their size; the board redraws only the cells that change; fonts are
  static cuts, 80 KB smaller.
- CI fails when start-up or writing on the page is slower than its budget, or the app grows more
  than 5%.

## 9.0.0

Room to grow: a fourth level, a handbook that shows what you know, a way to read a page back, and
pages of your own.

**Added**
- A fourth practice level, Deep, after three clean Tricky pages. It teaches naked and hidden
  triples, Swordfish and XY-Wing; the Coach explains each one the way it explains the others, and
  the handbook has a worked example for each. Earlier pages and levels are exactly as they were.
- The handbook shows how well you know each technique: met, practised (with how many clean pages
  needed it) and known (three). Each technique has "Practise this", which deals a page that needs
  it, outside the practice levels.
- Read back a solved page: every digit you wrote, one move at a time, with the technique that
  justified it then, the slips, and where the Coach helped. Pages written before 9.0 have no moves
  to read.
- Type in a page from a book or newspaper. It is checked for one solution, graded, and the Coach
  helps with it as with any page (or says when it needs more than the Coach's techniques).
- A 9×9 page for each week, dealt on Sunday and open all week, beside the 6×6 dailies.
- Traditional Chinese (繁體中文).

**Fixed**
- Taking back a wrong digit the Coach found returns exactly the pencil marks that digit took away.
- A blank daily left open overnight turns to the new day, also after Android closed the app.
- "Today's page is waiting" shows only right after finishing an earlier day, and only while today
  is not stamped.
- Tidying unbegun pages at start can no longer remove a page begun at the same moment.
- A page whose save was damaged no longer shows as stamped with a blank page behind it.
- The Coach's bracket sits beside the box it means, and beside a column in landscape too.

**Changed**
- Crossed-out pencil marks have a firm slash, readable at arm's length.
- TalkBack names each cell's box ("the top-right box"), as the Coach does.
- The notebook's title bar is smaller, so the first screen shows more of the notebook. A begun day
  in the contents is a dash, told apart from a stamp by shape.
- Words: the month reads "3 daily · 2 practice pages stamped"; the pencil "removes" marks; in
  Chinese, 换一页练习？, 难度提高了 and 擦掉.

**Under the hood**
- Version code 90000 (major × 10000 + minor × 100 + patch).
- CI: every action pinned to a commit, read-only jobs (only the job that publishes captures can
  write), weekly Dependabot updates, and a lint summary printed in the log.
- Releases: a dry run that publishes nothing, an approval step, and an optional fixed signing key
  for sideload builds (its certificate fingerprint is in the release notes).
- Faster start (settings and database warmed in the background), a tiled paper grain, and less
  redrawing while aiming.

## 8.0.0

Quiet, and it keeps what matters: a page you only looked at stays fresh, the notebook lists only
what you wrote, and the handbook teaches with real examples.

**Changed**
- The notebook's contents are its archive: every day since you started is one tap away there,
  with a key to its marks and the month's stamped and practice pages counted. Below them, only
  pages you have written on: unfinished ones first, then stamped pages by month.
- The handbook opens: "Using the pen" (tap, aim, hold a digit, pencil, keyboard), then each
  technique with a worked example from a real practice page, drawn and explained the way the
  Coach does it.
- On a 9×9 page, hold the pencil to pencil in every candidate in the cells you have not marked.
  It is only notes: the page stays clean, and one undo takes it back.
- The Coach button is as quiet as Undo and Pencil. What the Coach points at is washed in its
  terracotta and outlined, and its ink line brackets the board's side beside it.
- At large text, "Back to the page" comes before "Show the answer", and "answer shown" wraps
  instead of being cut off. "Back to the notebook" is ink, not the stamp's red. Pencil keys are
  pencil blue instead of greyed out.
- Words: the Coach puts the digit first ("A 3 fits only one cell in row 2"), names the next level
  ("2 more clean pages to Tricky", "Up a level"), and in Chinese says 干净完成，全靠自己, 回到这一页
  and 铅笔记号.

**Fixed**
- Opening a page no longer marks it "In progress": the widget, the Today card and the contents
  only count a page once something is written on it. Pages saved that way before are tidied up.
- Erasing a wrong digit the Coach found gives back the pencil marks it had crossed out, so a
  cell's own answer is never missing from its notes.
- A daily finished a day late says today's page is waiting, not "a new page tomorrow".
- An unreadable save is set aside instead of being overwritten.
- H at the Coach's answer places it; TalkBack says "Why, step 2 of 3" once.

**Under the hood**
- Database schema 4: saved pages keep their JSON last and are indexed, so the notebook reads
  less as it grows.
- The widget no longer wakes the device every hour and skips redraws that change nothing.
- A release only builds from a commit on main that passed CI, with a matching version and
  release notes. Lint warnings are printed in CI; Gradle caches builds.
- Benchmarks: a fresh page per run, animations on, a cold start without the Baseline Profile to
  compare, a smoke test of the minified build, and a Baseline Profile generator.
- Play listing artwork: screenshots at Play's sizes and a feature graphic.

## 7.0.0

What the app says, it does: the Coach's every word checked against the board, the notebook keeps
everything, and the privacy policy lists exactly what the app asks Android for.

**Fixed**
- The Coach no longer says "only one option left" for a hidden single; it says where the digit has
  only one place to go. Every crossing-out it shows is one its technique really allows, worked out
  on your own board; a pair or an X-Wing no longer marks candidates it does not rule out.
- The Coach's ink line points at the row, column or box it is talking about, not across every unit
  it mentions; those units are outlined on the board.
- A daily page is always the level the calendar promises (about one weekend page in a hundred was
  easier or harder). Those days now have a new puzzle; pages you have started or finished are kept.
- The handbook records every technique a solved page needed, not only the hardest.
- The stamp lands once per page: turning the phone or coming back no longer replays it.
- Taps and the keypad land in the right place in right-to-left languages.
- A damaged save (an impossible puzzle size, or a page marked solved with the wrong digits) is
  set aside instead of opened.
- Pen and printed digits use the weights designed for them, on the board, the widget and in print.

**Changed**
- Unfinished daily pages are kept for good; nothing in the notebook is deleted any more.
- The notebook has a contents page: the month as a grid of days, each marked clean, coached or
  begun. Earlier pages are grouped under month headings that stay in view.
- Boxes have names: "top right box", 右上宫, 中宫.
- The Coach shows "Why 2/3" with its steps; once it has helped, its button reads "Back to the
  page". A page whose answer was shown says so in its header.
- The solved note says what comes next: the practice level you reached, how many clean pages to
  the next, or that tomorrow brings a new page. The stamp lands with a tap of haptics.
- Clearer colour on the board: a firmer selection, crossings-out in the Coach's red, darker box
  lines at night, and a wider board on phones and in landscape.
- In Chinese, "clean" pages are 干净完成.
- The widget says "Ready when you are" before you start.

**Under the hood**
- The app asks for exactly three permissions (keeping the device awake for a moment, restoring
  the widget's midnight redraw after a restart, and one AndroidX defines for its own receivers),
  and a build check fails on anything else. The foreground service a library brought in is gone.
- Plain release tags publish the Play AAB only.
- UI tests run Compose's accessibility checks; the benchmark writes digits from the keypad.
- Licences now credit Okio.

## 6.0.0

Right, then refined: three real bugs fixed, a new icon, and every screen brought to one standard.

**Changed**
- A new icon: one pen-blue 5, circled by the Coach's pencil. It reads at any size and as a themed
  icon.
- Going back is a page lifting off the notebook: with the back gesture the page shrinks and rounds
  under your finger, and nothing shows through it any more. Taps slide pages along one axis.
- Calmer colour: red belongs to the Coach, the stamp and the digit in hand. Your selection and the
  pencil are ink blue; a clash is underlined in dots.
- The keypad has Erase as its last key: two even rows on 9×9, one row on 6×6. The toolbar keeps
  Undo, Pencil and the Coach.
- The notebook lines up on one edge; today's date is said once; the two cards match; the handbook
  is one compact card that marks only the techniques you have met. At large text, cards and the
  Coach rearrange instead of breaking lines badly.
- The Coach explains every step a placement needs, in order, and says a cell is settled only when
  it is. Its note is in a quiet upright voice; asking for the answer is the quiet button and says
  what it costs. Chinese steps are 提示 / 原因 / 答案.
- The stamp is ink on the page, in cut capitals, tilted a little differently on every page. The
  solved note leads with how the page was done.
- One icon set throughout. Settings has a licences page with the full SIL OFL and Apache texts.
- Words: "clean pages", "Start this page over", "Daily page" in shares; 独立完成 and 候选 in
  Chinese.

**Fixed**
- Weekend dailies and harder practice pages no longer repeat earlier puzzles (a seeding flaw dealt
  96 of 2026's dailies twice). Puzzles from now on differ from 5.0's; pages you have started or
  finished are kept as they are.
- A practice page set aside while the next one was still being prepared could stop saving, or come
  back; it now does neither.
- The Coach no longer claims a cell has a single answer when an earlier elimination it did not show
  was needed.
- The keypad never clips at 200% text. The Coach's line no longer runs across the board.

**Under the hood**
- Seeds are hashed (generator version 3); tests check a year of dailies and 60 practice pages per
  level for repeats, and that every Coach hint's steps lead to its placement.
- Custom motion uses the theme's motion scheme; material-icons-core is gone.

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
