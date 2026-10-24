# Paper Sudoku — Privacy Policy

Last updated: 2026-10-24 (version 8.0)

Paper Sudoku is an offline puzzle game. It does not require an account, does not contain ads or
analytics, and does not request the `INTERNET` permission.

## Data stored on your device

The app stores only the data needed to run the game locally:

- your games (board, notes, undo history, the digits written in order, time, how much the Coach
  helped), including puzzles you type in, in a local Room database;
- finished-puzzle results (mode, difficulty, time, Coach use, date) for the notebook;
- settings (theme, note tidying, techniques you have met, and per technique how many pages you
  wrote with it yourself and when,
  whether you have written your first digit) in Android DataStore.

The home-screen widget reads today's page from the same local database.

The app has no network access and no server, analytics SDK, advertising SDK, or third-party
tracking. If you use Android's own backup or device-to-device transfer, the system may include
the database and settings above in your backup, under your Google account and Android's backup
encryption; the developer cannot access it. You can turn this off in Android settings.

Sharing a result sends only the text you see in the share sheet to the app you choose. Printing
uses Android's print service and the printer or "Save as PDF" target you pick.

## Permissions and sharing

The app does not request network, location, contacts, camera, microphone, notification or
advertising permissions. It asks Android for exactly one, added by AndroidX:

- `com.titpea.papersudoku.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a permission the app
  defines for itself (androidx.core adds it) so that only the app can message its own internal
  receivers.

The widget's redraw just after midnight is an ordinary alarm and needs no permission.

It gives access to no data. The app does not run foreground services. A build check
fails if the app ever asks for anything else. The app does not share data with third parties.

## Children

The app does not knowingly collect personal information from anyone, including children.

## Changes

If a future version adds a feature that changes this policy, the policy and the Play Store Data
Safety declaration will be updated before that version is published.

## Contact

For privacy questions, contact the developer at the address listed on the Play Store listing.
