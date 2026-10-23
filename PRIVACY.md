# Paper Sudoku — Privacy Policy

Last updated: 2026-10-23 (version 7.0)

Paper Sudoku is an offline puzzle game. It does not require an account, does not contain ads or
analytics, and does not request the `INTERNET` permission.

## Data stored on your device

The app stores only the data needed to run the game locally:

- your games (board, notes, undo history, time, how much the Coach helped) in a local Room
  database;
- finished-puzzle results (mode, difficulty, time, Coach use, date) for the notebook;
- settings (theme, note tidying, techniques you have met) in Android DataStore.

The home-screen widget reads today's page from the same local database.

The app has no network access and no server, analytics SDK, advertising SDK, or third-party
tracking. If you use Android's own backup or device-to-device transfer, the system may include
the database and settings above in your backup, under your Google account and Android's backup
encryption; the developer cannot access it. You can turn this off in Android settings.

Sharing a result sends only the text you see in the share sheet to the app you choose. Printing
uses Android's print service and the printer or "Save as PDF" target you pick.

## Permissions and sharing

The app does not request network, location, contacts, camera, microphone, notification or
advertising permissions. It asks Android for exactly three, all brought in by the job scheduler
the home-screen widget uses to redraw at midnight:

- `WAKE_LOCK` — keeps the device awake for the moment the widget redraws;
- `RECEIVE_BOOT_COMPLETED` — restores the scheduled redraw after the phone restarts;
- `com.titpea.papersudoku.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a permission the app
  defines for itself (AndroidX adds it) so that only the app can message its own internal
  receivers.

None of them gives access to any data. The app does not run foreground services. A build check
fails if the app ever asks for anything else. The app does not share data with third parties.

## Children

The app does not knowingly collect personal information from anyone, including children.

## Changes

If a future version adds a feature that changes this policy, the policy and the Play Store Data
Safety declaration will be updated before that version is published.

## Contact

For privacy questions, contact the developer at the address listed on the Play Store listing.
