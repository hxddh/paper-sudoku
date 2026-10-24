# Paper Sudoku — Store Listing Draft

## Icon

`docs/assets/icon-512.png` (512 × 512, Play's hi-res icon), rendered from `docs/assets/icon.svg`, which is
the launcher icon's visible area. Play applies its own mask and shadow, so the file is full-bleed.

## Short description (80 characters max — this one is 72)

A Sudoku notebook: one page a day, a Coach in the margin, fully offline.

## Full description

**A Sudoku notebook.**

Open Paper Sudoku and today's page is in front of you — a small 6×6, made for a coffee break.
Turn back and every day since you started is there, stamped when you finished it. When you have longer, the
practice stack waits: 9×9 pages that grow harder as you learn.

Stuck? The Coach writes in the margin: where to look, then why, and the answer only if you ask.

- One page a day, the same for everyone, fully offline
- Practice that follows you: singles → pointing and pairs → hidden pairs and X-Wings → triples,
  Swordfish and XY-Wing
- A handbook that shows what you know, and pages to practise each technique
- Read a solved page back, move by move
- Type in a puzzle from a book or newspaper; a 9×9 page each week
- Press and slide to aim; pick up a digit and write it across the page
- Printed digits, your ink, pencil marks — and print a blank copy of any page on real paper
- A notebook of stamped days. No streaks.
- Today's page on your home screen
- Tablets and foldables: notebook and page side by side
- No ads, accounts, analytics or `INTERNET` permission

## Category and tags

- Category: Games / Puzzle
- Tags (chosen from Play's own list in the console): Sudoku, Logic, Offline

## Monetization

Recommended v1: paid app, no ads and no in-app purchases. The current build has no billing
dependency. If the pricing changes to free + one-time unlock, billing and the corresponding
privacy/data-safety updates must be designed before release.

## Screenshot plan

`StoreScreenshotsTest` draws them at Play's sizes (phone 1080×1920, 9:16; tablet 1080×608,
16:9); CI publishes them under `store/` on the PR's captures branch; the current set is in `docs/store/`. The matrix captures are
not used: at 540×1200 they are taller than Play's 2:1 limit.

1. A practice page with pencil marks (Paper)
2. The Coach at "Why", the unit washed and bracketed
3. A stamped page
4. The notebook: today, practice, the month's contents
5. The handbook's X-Wing example
6. Night
7. Tablet: the page beside its tools

Feature graphic: `docs/assets/feature-graphic.png` (1024×500, `tools/store/feature_graphic.py`).

## Support / privacy

- Privacy policy: `PRIVACY.md` (host publicly before submitting the store listing)
- Support contact: set the developer contact in Play Console before publishing
