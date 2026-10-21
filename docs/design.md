# Design · Paper Sudoku 2.0

One idea runs through every screen: **a notebook that teaches**. A page a day, written in ink and
pencil, stamped when done, with a patient coach at your shoulder.

## Principles

1. **Paper first.** The board is a sheet, not a widget. Material 3 Expressive provides structure,
   motion and components; Paper provides material, type and color.
2. **Teach, don't tell.** The Coach answers last. Every puzzle is graded by the same techniques the
   Coach can explain, so "no simple hint" never happens on a generated puzzle.
3. **Calm by default.** No streaks, no badges, no mistake counter. Mistake marks are opt-in.
4. **One custom surface.** Only the board is drawn by hand; everything else is a Material
   component so accessibility, insets and motion behave natively.

## Writing layers

| Layer | Use | Type | Color role |
|---|---|---|---|
| Printed | givens | Fraunces 560, opsz 36, lining + tabular figures | `onSurface` |
| Pen | player digits | Fraunces Italic 420, SOFT 100 | `secondary` (ink blue) |
| Pencil | notes | system sans, medium | `onSurfaceVariant` |
| Correction | conflicts, opt-in mistakes | as above + 2dp underline | `error` |

A written digit lands with a short spring (scale 1.25 → 1, fade in). Conflicts always carry the
underline, so color is never the only cue.

## Color

Every Material 3 role is defined for Paper (light) and Night (dark) in
`designsystem/Theme.kt`, including all `surfaceContainer*` roles; nothing falls back to the
baseline palette. Key pairs:

| Role | Paper | Night |
|---|---|---|
| background / surface | `#F4EFE5` | `#14130F` |
| primary (terracotta) | `#A8442F` — 5.2:1 on paper | `#EFA489` |
| secondary (ink) | `#2D5577` | `#A9C8E8` |
| tertiary (moss, Coach focus) | `#5E6B3A` | `#C3CF9B` |
| onSurfaceVariant (pencil) | `#5F574C` — 6.2:1 | `#CFC5B5` |
| sheet (`surfaceContainerLowest`) | `#FFFDF8` | `#0F0E0A` |

Material You (Android 12+) is offered as an option; the paper grain is drawn only with Paper/Night.

## Board marks (Coach)

| Level | Board | Panel |
|---|---|---|
| 1 Look | `tertiaryContainer` tint on the unit(s) | "Look at box 4." + a one-line tip |
| 2 Why | + `primaryContainer` pattern cells, crossed-out candidates in `error`, target outline for singles | technique name + explanation |
| 3 Answer | + faint ink digit in the target | "Place 7 at row 3, column 5." + Place |

Selection: `primary` 16% + 2dp outline. Peers: `surfaceVariant` 45%. Same digit:
`secondaryContainer` 75%, and matching pencil notes turn bold ink.

## Layout

- **Stacked** (window not clearly wider than tall): top bar, board (square, up to 720dp),
  tool panel (dock + keypad / Coach / Solved) below, max 640dp wide.
- **Side by side** (width > height and ≥ 560dp): board left, panel right at 40% (320–440dp),
  scrollable. Covers phone landscape, tablets, unfolded foldables and desktop windows.
- Insets: `Scaffold` handles system bars; horizontal display-cutout padding in landscape.

Touch: dock items ≥ 56dp tall, keys 60dp tall; Mini cells ≈ 61dp on a 390dp phone. 9×9 cells are
≈ 41dp on phones — a documented exception, mitigated by digit lock, keyboard input and
landscape/tablet layouts where cells exceed 48dp.

## Motion and haptics

`MotionScheme.expressive()` for components; panel changes cross-fade and resize; the stamp lands
with an overshoot spring (damping 0.45). Predictive back scales the Coach card with the gesture.
Haptics: keyboard tap on writing, reject on a clash, confirm on solving, segment tick on toggles —
all behind a setting.

## Copy

Short, second person, no exclamation marks. The Coach never says "wrong"; it says
"Something doesn't fit." Chinese copy is written, not translated word for word.
