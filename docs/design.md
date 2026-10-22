# Design · Paper Sudoku 5.0

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
   is the ink of the stamp, the Coach and the one digit in hand — never a scolding).
5. **Native first.** Material 3 / Expressive components and adaptive scaffolds everywhere except
   the board, the one hand-drawn surface.

## Structure

| Surface | Component |
|---|---|
| App | Navigation 3 back stack: Notebook → Settings |
| Notebook + page | `NavigableListDetailPaneScaffold` (one pane on phones, two on wide windows); predictive back |
| Notebook | `LargeTopAppBar` (collapsing), today card, practice card, `ListItem` rows, handbook |
| Page header | small transparent `TopAppBar`: back (one-pane only), weekday · level, date, overflow |
| Tools | `HorizontalFloatingToolbar`: Undo, Erase, Notes (`FilledIconToggleButton`), Coach (`FilledIconButton`) |
| Keypad | `ButtonGroup` with connected keys (outer corners round, inner tight, press rounds fully) |
| Coach | margin note: 2dp ink rule, tag + steps, note in pen italic, two actions |
| Transitions | shared element thumbnail ↔ board; ink connector drawn by pen (450ms) |
| Widget | Glance: date, status, page miniature drawn as tinted layers (follows day/night); opens today |
| Icon | adaptive: paper background, one cell's nine pencil marks in ink blue with the fitting 5 ringed in terracotta; monochrome layer for themed icons |

## Interaction

- **Aim:** press shows the cell; sliding beyond touch slop opens a 76dp loupe 96dp above the
  finger and ticks a haptic per cell; lifting commits. A plain tap is a tap; a touch the system
  takes over commits nothing.
- **Write:** with an editable cell selected, a digit writes (again = clear). With nothing to write
  into, a digit is *picked up* (the one red key, status line "Holding 4 · Put down"); every tap
  writes it; tap it again or the status line to put it down. It is put down by itself when its
  last copy is placed. A digit typed on a keyboard always writes.
- **Notes:** the toggle switches the pen to pencil; keys and status line show it.
- **Coach:** Look tints the unit; Why adds the pattern, crossed-out candidates and the target;
  Answer shows the digit faintly and offers Place. A wrong digit is pointed out first.
- **Leaving** a page (back, notebook, background) stops the hidden clock.
- **First page:** until the first digit is ever written, the status line reads "Tap a cell, then a
  digit — or press and slide to aim." It is the app's only how-to.
- **Clean:** a page is clean unless the Coach gave its answer; pointing at a wrong digit does not
  count. The same rule drives stamps, practice levels and share text.

## Writing layers

| Layer | Type | Color |
|---|---|---|
| Printed givens | Fraunces 560, opsz 36 | `onSurface` |
| Pen | Fraunces Italic 420, SOFT 100 | `secondary` |
| Pencil | platform sans, medium | `onSurfaceVariant` |
| Margin (Coach) | platform sans italic | `primary` rule and tag, `secondary` note |
| Clash | + 2dp underline | `error` |

Numerals in the notebook (dates, times) are Fraunces; every other word is the platform sans.

## Color

Paper (light) and Night (dark) define every Material 3 role (see `designsystem/Theme.kt`); there
is no dynamic-color variant. Key: paper `#F4EFE5`, terracotta `#A8442F`, ink `#2D5577`, moss
`#5E6B3A`; night `#14130F` with `#EFA489`, `#A9C8E8`, `#C3CF9B`.

## Layout

- Phone portrait: header, board (4dp margins for 9×9, 12dp for 6×6) sitting just above the work
  area of height `max(176dp, toolbar + status + keypad)`; most of the spare height goes above the
  board (vertical bias 0.4), so it sits near the hand.
- Window wider than tall and ≥ 560dp: board left at full height, header and work area right
  (40%, 320–440dp).
- Wide windows with the notebook expanded: list-detail two panes; the page hides its back button.
  Short windows (a phone on its side, height below 480dp) show one pane at a time.

9×9 cells on a 390dp phone are ≈ 42dp; aiming with the loupe, digit pick-up and the keyboard make
precise input independent of finger size. Every other target is ≥ 48dp.

## Motion and haptics

`MotionScheme.expressive()`; connected keys animate width and corners; ink settles on write
(scale 1.25 → 1, spring 0.55); the stamp lands with overshoot; the Coach connector draws in.
Haptics follow the system's touch-feedback setting: keyboard tap on write, reject on a clash,
confirm on solve, segment tick on toggles and while aiming.

## Keys

Paper keys (`surfaceContainerHigh`) with printed ink; pencil mode greys them; the held digit is
the one terracotta key. Finished digits shrink to a check but stay ≥ 48dp wide.
