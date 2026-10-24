# Design · Paper Sudoku 11.0

**A notebook of pages.** Open it and today's page is in front of you. Turn back and every earlier
day is there. Finish a page and it is stamped. When you are stuck, the Coach writes in the margin.

## Principles

1. **One metaphor, all the way down.** Navigation is turning pages; history is the notebook; the
   reward is a stamp; help is a margin note. No menus that are not part of the notebook.
2. **Everything on the one page (11.0).** No settings, no hidden gestures, no screen that takes
   the keys away. The Coach writes above the keys, which stay in use; what a long press used to
   do is a visible action; the how-to is a one-line hint the first time each thing happens.
3. **The page is sacred.** Nothing moves the board once a page is open: it sits a fixed 16dp under
   the header, sized with the work area under it reserved, and nothing the work area shows (hint,
   Coach, solved note) changes its size or place.
4. **Teach, don't tell.** The Coach answers last, and only with the techniques every generated
   puzzle is graded with.
5. **Calm: real paper, quiet ink.** No streaks, no visible clock, no error marks unless digits
   truly clash. One accent: terracotta is the ink of the stamp, the Coach and the one digit in
   hand, never a label, a button or a scolding; the player's own selection and pencil are pen
   blue; everything else is ink. No filled buttons, no bounce.
6. **Native first.** Material 3 components (mapped onto the tokens below) and adaptive scaffolds
   everywhere except the board, the one hand-drawn surface.

## Tokens (`designsystem/Tokens.kt`, `Type.kt`)

| Token | Values |
|---|---|
| Sans (platform) | five styles only: Title 22/28 Medium, Heading 16/22 Medium, Body 16/24, Support 14/20, Caption 12/16 Medium +0.4 tracking. Material's roles map onto them (headline/title large → Title; title medium/small and button labels → Heading; body large → Body; body medium/small → Support; label medium/small → Caption), so components inherit the scale |
| Fraunces | three uses only: Display 400 at 44 (today's date; the stamp's capitals at 600), Figure 400 at 28 (list dates, times, the calendar; below 20sp the printed opsz-36 cut, `PaperType.figureSmall`), Printed 560 and pen Italic 420 (the board and the keys). Never inside a sans line |
| Spacing | 4, 8, 12, 16, 24, 32, 48 only. Page margin 16dp: the board's edge, the keys' edge, a card's edge. One text edge at 20dp (16 + 4 optical), lined up with the board's frame |
| Radii | 0 cells; 2 marks and pips; 8 board, cards, keys, buttons, sheets, dialogs; full only for dots and the icon ripple |
| Alpha | 5%, 8%, 16%, 100% |
| Sheets | `PaperSheet`: 8dp corners, a step off the page (`surfaceContainerLow`, `High` when selected) and a hairline `outlineVariant` edge in both themes |

## Structure

| Surface | Component |
|---|---|
| App | Navigation 3 back stack: Notebook → Licences, Handbook, Read back, Type in a page; every page is an opaque sheet of paper, drawn once (no fill behind the stack), and the last three are laid out as pages (header, board, lower panel). No settings: the theme follows the system, pencil marks are always tidied, language is Android's per-app language setting |
| Notebook + page | `NavigableListDetailPaneScaffold` (one pane on phones, two on wide windows); predictive back |
| Notebook | One notebook (10.0). `MediumTopAppBar` (collapsing) with "Handbook" in words (no Settings since 11.0: the licences are at the foot, through `onOpenLicences`; cards are `PaperSheet`s); today and practice cards (one build, 112dp thumbnails, 88dp at large text; under practice, one ink line "Next: X-Wing" that opens the handbook at that technique); unfinished pages (days, typed-in pages, this week's page once begun); the month's contents (a day grid: filled dot clean, ring with the answer, grey dash begun; today circled; a key under it; the month's stamped and practice pages counted — never days in a row); then the journal: stamped days and every finished 9×9 page (practice, technique, weekly, typed in; filed under the day it was finished) together under sticky month headers (grouped in the ViewModel); "Type in a page" is one quiet ink row at the end. One text edge 20dp in from the cards. Only pages written on are listed: blank days are reached through the contents, and a page only looked at is not kept as begun. Unfinished dailies are kept for good; an unfinished practice page set aside is dropped. There is no weekly card and no handbook card; old weekly and technique pages still open from the journal. |
| Handbook | paper pages turned with a `HorizontalPager` (and previous / next at the foot), under a page header (overline "Handbook · 7 of 11", the technique as title): one technique a page, led by the board 16dp under the header — its worked example from a real practice page, drawn as the Coach does it — then how well it is known (three ink strokes inked to seen / practised / known; nothing before it is met), its summary and the Coach's explanation, all in ink. No how-to page (11.0): the page teaches itself with first-time hints. No "Practise this": practice is one path |
| Licences | reached from the notebook's foot (`onOpenLicences`): the version and the privacy note, then the typeface, icons and libraries with their licences in full |
| Read back | the page itself, scrubbed back through its moves: the page header ("Read back · Wednesday · Steady", the date), the board 16dp under it at the page's size with the digits after the move (the move's cell selected, a slip as a clash), and in the work area the move and why it was justified then, over "Move n of m", previous, a slider (a 2dp ink rule with a 12dp dot; a slider to TalkBack) and next; "This page" in the header swaps the note for the page's counts |
| Type in a page | a fixed page: the page header (overline "Type in a page", title "Your page"), Clear and "Check and add" in the bar, the board, one line of how-to or refusal, the keypad |
| Page header | small transparent `TopAppBar`: back (one-pane only), weekday · level, date, overflow (start over, print and, on practice, a new page). A solved page's note already offers share, print and read back, so its overflow keeps only "New practice page", and a solved page with nothing else has no overflow |
| Status line | one row over the keys: on the text edge what the pen is doing ("4 in hand · Tap to put down" as a quiet text button; in pencil on 9×9 the action "Fill every candidate"; "Pencil on" on 6×6), and at its end the three tools as bare icon buttons: Undo, Pencil (pen blue with a 2dp underline when on; a long press still fills candidates), Coach — all three equally quiet. No floating toolbar |
| Keypad | paper keys in a row with 4dp gaps, 8dp corners, on the page margin; a press is an 8% ink tint, nothing moves or reshapes; Erase is the last key: 5 + 5 on 9×9, seven keys on 6×6 |
| Coach | margin note above the keys, which stay in use: a 2dp terracotta rule hanging in the margin (8dp in), its words on the 20dp text edge, the technique as a Caption tag ("· Why 2/3" while walking through steps), then one Heading line, where to look. The line opens into why (with what to look for, or what the technique is the first time); the last step offers "Show the answer" as a small text action with its cost beside it; the answer offers Place as the one hairline-outlined button. × (and back, Esc) puts it away. Boxes are named by position (top right box / 右上宫); it reads the page as the player has it: a pencil mark leaving out a cell's answer first, steps near the selected cell before others |
| Solved note | in the work area: how the page was done (Title), "Solved in 2:05" in plain sans, one outlook line (news in ink Heading, else Support), share / print / read back as icons and the way on as a 1dp ink-outlined button |
| Transitions | taps: shared axis X (10%) on the motion scheme; back gesture: the leaving page scales to 90%, rounds 28dp and lifts, linear with the finger, fading as it commits; thumbnail ↔ board as shared bounds (cross-fade); ink connector drawn by pen from the note's rule to a bracket on the board's edge. The work area never animates between tools and note: the note simply appears above the keys |
| Icons | Material Symbols Rounded, weight 400, unfilled, as vector drawables |
| Widget | Glance: date, status, page miniature drawn as tinted layers (follows day/night); opens today |
| Icon | adaptive: paper background, a Fraunces Italic 5 in pen blue circled by a tapered terracotta pencil ring (the Coach marking an answer); readable at 36dp; monochrome layer for themed icons |

## Interaction

- **Aim:** press shows the cell; sliding beyond touch slop opens a 76dp loupe 96dp above the
  finger and ticks a haptic per cell; lifting commits. A plain tap is a tap; a touch the system
  takes over commits nothing.
- **Write:** with an editable cell selected, a digit writes (again = clear). With **no cell
  selected**, a digit is *picked up* (the one terracotta digit, underlined; status line "4 in hand ·
  Tap to put down"); every tap writes it; tap it again or the status line to put it down. It is put
  down by itself when its last copy is placed. With a printed digit selected, a digit key only shows
  where that digit is on the page (as the same-digit wash) and enters no mode. Tapping the selected
  cell again lets go of it. A digit typed on a keyboard always writes.
- **Notes:** the toggle switches the pen to pencil; keys (smaller digits in pen blue) and the
  status line show it. Writing a digit always tidies it out of the pencil marks it sees (no
  setting). On a 9×9 page the status line offers "Fill every candidate": every candidate in the
  cells with no marks yet (one undo takes it back; notes only, so the page stays clean).
- **Mistakes:** erasing a wrong digit the Coach found also gives back the pencil marks it had
  crossed out.
- **Coach:** the note and its marks stay on the board until the step is done: a digit written or
  erased anywhere ends it (the right digit in its cell ends the hint by itself), a missing mark
  pencilled back ends that note; asking again for the same step keeps the note as far as it went.
  Look washes the unit in the Coach's terracotta and outlines it inside its cells, and the ink line brackets the board's side beside it (a column gets a bracket under it, in every layout; on a phone a box is bracketed on its own side); Why adds the pattern and crossed-out candidates. When the
  placement needs eliminations first, Why walks through each (only those the placement depends
  on), keeping earlier crossings-out, and names the cell only at the last. Answer shows the digit
  faintly and offers Place. A wrong digit is pointed out first.
- **Leaving** a page (back, notebook, background) stops the hidden clock.
- **First times:** until the first digit is ever written, the line above the status line reads "Tap
  a cell, then a digit. Or press and slide to aim." The first pencil, the first digit in hand and the
  first Coach each add one line, once (remembered in DataStore, as `hintsSeen`). That is the app's
  whole how-to.
- **Solved:** the note says one true thing — a level reached, else the hardest technique the
  player wrote with themselves on this page ("X-Wing: you found it yourself"), else how many clean
  pages to the next level, or that a new daily page comes tomorrow. A page whose answer was shown says so in
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
| Margin (Coach) | platform sans, upright (no synthetic CJK italic) | `primary` rule and tag, `onSurface` note |
| Clash | + dotted 2dp underline (shape, not only colour) | `error` |

Numerals in the notebook (dates, times, the widget's date) are Fraunces figures; every other word,
and any number inside a sentence ("Solved in 2:05"), is the platform sans. The stamp's lettering is
Fraunces capitals at 600, tracked 0.12em; the stamp carries no time (the solved note says it).

Digits are sized by the cell (0.50 of a 6×6 cell, 0.56 of a 9×9 cell; pencil marks 0.76 of a
third). Lines: cells 1dp, boxes 2dp, frame 2.5dp.

## Color

Paper (light) and Night (dark) define every Material 3 role (see `designsystem/Theme.kt`); there
is no dynamic-color variant, and no theme setting: it follows the system. Key: paper `#F4EFE5`,
terracotta `#A8442F`, ink `#2D5577`; night `#14130F` with `#EFA489`, `#A9C8E8`. There is no third
hue: the tertiary roles repeat the pen, and nothing on screen is drawn in a primary or secondary
container. The page sheet is a step lighter than the desk in both themes (night sheet
`#1C1A16`); cards and sheets carry a hairline `outlineVariant` edge in both themes. Terracotta
(`primary`) is only the Coach (target, pattern, rule, tag), the stamp and the digit in hand;
section labels, outlook lines and every button are ink (`onSurface` / `onSurfaceVariant`). The
Coach button is a plain tool; navigation (Back to the notebook) is a 1dp ink outline.

## Layout

- Page-type screens (page, read back, handbook): the board sits a fixed 16dp under the header, on
  the 16dp page margin; spare height goes between the board and the work area.
- Phone portrait: header, board, then the work area of at least `max(176dp, note room 64dp +
  status 48dp + keypad)` (note room and minimum grow with text up to 130%); the board is sized with
  that reserved. The note's room takes the spare height and scrolls when a note is longer still.
- Window wider than tall and ≥ 560dp: board at full height on the left, header and work area right
  (40%, 320–440dp); the keypad's foot is on the board's foot.
- Wide windows with the notebook expanded: list-detail two panes; the page hides its back button.
  Short windows (a phone on its side, height below 480dp) show one pane at a time.

9×9 cells on a 390dp phone are ≈ 40dp; aiming with the loupe, digit pick-up and the keyboard make
precise input independent of finger size. Every other target is ≥ 48dp.

## Motion and haptics

`MotionScheme.standard()`: quiet, no overshoot; custom motion takes its specs (page transitions,
connector). Keys never move: a press is a tint. Ink settles calmly on write; the stamp is pressed
from 1.06 to rest with no bounce, multiplies into the paper (screens at night) and is tilted
−14°…−6° and nudged ±6dp by the page, so each page's stamp is its own and stays the same. It lands once per page, with a confirm
haptic as it touches; turning the phone or coming back shows it already landed.
Haptics follow the system's touch-feedback setting: keyboard tap on write, reject on a clash,
confirm on solve, segment tick on toggles and while aiming.

## Keys

Paper keys (`surfaceContainerHigh`, 8dp corners) with printed ink; pencil mode turns them a step
quieter with smaller pen-blue digits; the held digit is a terracotta digit on its paper key with a
2dp terracotta underline. Finished digits turn into a check icon and keep their width. Keys share
the width between the 16dp margins with 4dp gaps (the seven 6×6 keys are 43dp on a 360dp phone,
47dp from 390dp) and are 56dp tall; their glyphs are sized by the key, not the font scale, so they
never clip at 200% text.
