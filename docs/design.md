# Design · Paper Sudoku 8.0

**A notebook of pages.** Open it and today's page is in front of you. Turn back and every earlier
day is there. Finish a page and it is stamped. When you are stuck, the Coach writes in the margin.

## Principles

1. **One metaphor, all the way down.** Navigation is turning pages; history is the notebook; the
   reward is a stamp; help is a margin note. No menus that are not part of the notebook.
2. **The page is sacred.** Nothing moves the board once a page is open: the work area under it
   (tools, Coach, solved note) keeps one height (it grows only for very large text).
3. **Teach, don't tell.** The Coach answers last, and only with the techniques every generated
   puzzle is graded with.
4. **Calm.** No streaks, no visible clock, no error marks unless digits truly clash (terracotta
   is the ink of the stamp, the Coach and the one digit in hand — never a scolding; the player's
   own selection and pencil are pen blue).
5. **Native first.** Material 3 / Expressive components and adaptive scaffolds everywhere except
   the board, the one hand-drawn surface.

## Structure

| Surface | Component |
|---|---|
| App | Navigation 3 back stack: Notebook → Settings → Licences, Handbook, Read back, Type in a page; every page is an opaque sheet of paper, and the last three are laid out as pages (header, board, lower panel) |
| Notebook + page | `NavigableListDetailPaneScaffold` (one pane on phones, two on wide windows); predictive back |
| Notebook | One notebook (10.0). `MediumTopAppBar` (collapsing) with "Handbook" in words beside Settings; today and practice cards (one build, 112dp thumbnails, 88dp at large text; under practice, one ink line "Next: X-Wing" that opens the handbook at that technique); unfinished pages (days, typed-in pages, this week's page once begun); the month's contents (a day grid: filled dot clean, ring with the answer, grey dash begun; today circled; a key under it; the month's stamped and practice pages counted — never days in a row); then the journal: stamped days and every finished 9×9 page (practice, technique, weekly, typed in; filed under the day it was finished) together under sticky month headers (grouped in the ViewModel); "Type in a page" is one quiet ink row at the end. One text edge 20dp in from the cards. Only pages written on are listed: blank days are reached through the contents, and a page only looked at is not kept as begun. Unfinished dailies are kept for good; an unfinished practice page set aside is dropped. There is no weekly card and no handbook card; old weekly and technique pages still open from the journal. |
| Handbook | paper pages turned with a `HorizontalPager` (and previous / next at the foot), under a page header (overline "Handbook · 8 of 12", the topic as title): using the pen (tap, aim, hold a digit, pencil, pencil-in-candidates, keyboard), then one technique a page, led by the board — its worked example from a real practice page, drawn as the Coach does it — then how well it is known as ink (three pen strokes inked to seen / practised / known, in pen blue; nothing before it is met), its summary and the Coach's explanation. No "Practise this": practice is one path |
| Read back | the page itself, scrubbed back through its moves: the page header ("Read back · Wednesday · Steady", the date), the board at the page's size with the digits after the move (the move's cell selected, a slip as a clash), and in the work area, at its one height, the move and why it was justified then, over "Move n of m", previous, a slider and next; "This page" in the header swaps the note for the page's counts |
| Type in a page | a fixed page: the page header (overline "Type in a page", title "Your page"), Clear and "Check and add" in the bar, the board, one line of how-to or refusal, the keypad |
| Page header | small transparent `TopAppBar`: back (one-pane only), weekday · level, date, overflow (start over, print and, on practice, a new page). A solved page's note already offers share, print and read back, so its overflow keeps only "New practice page", and a solved page with nothing else has no overflow |
| Tools | `HorizontalFloatingToolbar`: Undo, Pencil (an icon toggle, secondary container when on; a long press on 9×9 pencils in every candidate), Coach — all three equally quiet |
| Keypad | `ButtonGroup` with connected keys (outer corners round, inner tight, press rounds fully); Erase is the last key: 5 + 5 on 9×9, seven keys on 6×6 |
| Coach | margin note: 2dp ink rule, the technique as tag, "Why n/m" with step pips, note in upright `onSurfaceVariant`, two actions ("Back to the page" once it has helped); the answer is an outlined button with its cost said above it; boxes are named by position (top right box / 右上宫) |
| Transitions | taps: shared axis X (10%) on the motion scheme; back gesture: the leaving page scales to 90%, rounds 28dp and lifts, linear with the finger, fading as it commits; thumbnail ↔ board as shared bounds (cross-fade); ink connector drawn by pen to a bracket on the board's edge |
| Icons | Material Symbols Rounded, weight 400, unfilled, as vector drawables |
| Widget | Glance: date, status, page miniature drawn as tinted layers (follows day/night); opens today |
| Icon | adaptive: paper background, a Fraunces Italic 5 in pen blue circled by a tapered terracotta pencil ring (the Coach marking an answer); readable at 36dp; monochrome layer for themed icons |

## Interaction

- **Aim:** press shows the cell; sliding beyond touch slop opens a 76dp loupe 96dp above the
  finger and ticks a haptic per cell; lifting commits. A plain tap is a tap; a touch the system
  takes over commits nothing.
- **Write:** with an editable cell selected, a digit writes (again = clear). With nothing to write
  into, a digit is *picked up* (the one red key, status line "4 in hand · Tap to put down"); every tap
  writes it; tap it again or the status line to put it down. It is put down by itself when its
  last copy is placed. A digit typed on a keyboard always writes.
- **Notes:** the toggle switches the pen to pencil; keys (smaller digits in pencil blue) and the
  status line show it. Holding the pencil on a 9×9 page pencils in every candidate in the cells
  with no marks yet (one undo takes it back; notes only, so the page stays clean).
- **Mistakes:** erasing a wrong digit the Coach found also gives back the pencil marks it had
  crossed out.
- **Coach:** Look washes the unit in the Coach's terracotta and outlines it inside its cells, and the ink line brackets the board's side beside it (a column gets a bracket under it, in every layout; on a phone a box is bracketed on its own side); Why adds the pattern and crossed-out candidates. When the
  placement needs eliminations first, Why walks through each (only those the placement depends
  on), keeping earlier crossings-out, and names the cell only at the last. Answer shows the digit
  faintly and offers Place. A wrong digit is pointed out first.
- **Leaving** a page (back, notebook, background) stops the hidden clock.
- **First page:** until the first digit is ever written, the status line reads "Tap a cell, then a
  digit. Or press and slide to aim." It is the app's only how-to.
- **Solved:** the note says what comes next — the practice level reached or how many clean pages
  to the next, or that a new daily page comes tomorrow. A page whose answer was shown says so in
  its header.
- **Clean:** a page is clean unless the Coach gave its answer; pointing at a wrong digit does not
  count. The same rule drives stamps, practice levels and share text.

## Writing layers

| Layer | Type | Color |
|---|---|---|
| Printed givens | Fraunces 560, opsz 36 | `onSurface` |
| Pen | Fraunces Italic 420, SOFT 100 | `secondary` |
| Struck candidates (Coach) | strike line | `primary` (terracotta) |
| Pencil | platform sans, regular, tabular | `onSurfaceVariant` |
| Margin (Coach) | platform sans, upright (no synthetic CJK italic) | `primary` rule and tag, `onSurfaceVariant` note |
| Clash | + dotted 2dp underline (shape, not only colour) | `error` |

Numerals in the notebook (dates, times, the solved time, the widget's date) are Fraunces; every
other word is the platform sans. The stamp's lettering is Fraunces capitals, tracked 0.12em.

Digits are sized by the cell (0.50 of a 6×6 cell, 0.56 of a 9×9 cell; pencil marks 0.76 of a
third). Lines: cells 1dp, boxes 2dp, frame 2.5dp.

## Color

Paper (light) and Night (dark) define every Material 3 role (see `designsystem/Theme.kt`); there
is no dynamic-color variant. Key: paper `#F4EFE5`, terracotta `#A8442F`, ink `#2D5577`, moss
`#5E6B3A`; night `#14130F` with `#EFA489`, `#A9C8E8`, `#C3CF9B`. The page sheet is a step
lighter than the desk in both themes (night sheet `#1C1A16`); night cards carry a 1dp
`outlineVariant` edge. Terracotta marks the Coach (target, pattern, rule), the stamp and the
held digit; the selection is `secondary` at 20%, same digits 8% (12% at night), the Coach's unit a terracotta wash (8%, 14% at night) with a 1.5dp outline inset 3dp. The Coach button is a plain tool; red is for what the Coach marks, the stamp and the held digit, and navigation (Back to the notebook) is tonal ink.

## Layout

- Phone portrait: header, board (8dp margins for 9×9, 12dp for 6×6) sitting just above the work
  area of height `max(176dp, toolbar + status + keypad)`; most of the spare height goes above the
  board (vertical bias 0.4), so it sits near the hand.
- Window wider than tall and ≥ 560dp: board at full height, pushed toward the centre, header and
  work area right (40%, 320–440dp, 24dp from the edge).
- Wide windows with the notebook expanded: list-detail two panes; the page hides its back button.
  Short windows (a phone on its side, height below 480dp) show one pane at a time.

9×9 cells on a 390dp phone are ≈ 42dp; aiming with the loupe, digit pick-up and the keyboard make
precise input independent of finger size. Every other target is ≥ 48dp.

## Motion and haptics

`MotionScheme.expressive()`; custom motion takes its specs (keys, work area, page transitions,
connector). Ink settles on write (scale 1.25 → 1, spring 0.55); the stamp lands with overshoot,
multiplies into the paper (screens at night) and is tilted −14°…−6° and nudged ±6dp by the page,
so each page's stamp is its own and stays the same. It lands once per page, with a confirm
haptic as it touches; turning the phone or coming back shows it already landed.
Haptics follow the system's touch-feedback setting: keyboard tap on write, reject on a clash,
confirm on solve, segment tick on toggles and while aiming.

## Keys

Paper keys (`surfaceContainerHigh`) with printed ink; pencil mode greys them; the held digit is
the one terracotta key. Finished digits shrink to a check icon. Keys are at least 44dp wide (the
seven 6×6 keys are 45dp on a 360dp phone, 48dp from 378dp) and 56dp tall; their glyphs are sized by the key, not the font
scale, so they never clip at 200% text.
