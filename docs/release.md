# Release Checklist

## Product identity

- App name: **Paper Sudoku** / **纸上数独**
- `applicationId`: `com.titpea.papersudoku`
- Version: `9.0.0` (`versionCode 90000`: major × 10000 + minor × 100 + patch)
- Minimum SDK: 26
- Target SDK: 36
- Compile SDK: 37 (required by Material 3 Expressive `1.5.0-beta01`)
- Material 3 `1.5.0-beta01`: current public Expressive API; replace with 1.5 stable before rollout if available
- No `INTERNET`, billing, ads, analytics, or account permissions
- Android backup: Room database + DataStore only (`xml/data_extraction_rules.xml`)

## Signed AAB

Create an upload key once, outside the repository, and keep it backed up:

```bash
keytool -genkeypair \
  -keystore paper-sudoku-upload.jks \
  -alias paper-sudoku \
  -keyalg RSA -keysize 4096 -validity 10000
```

Export the four values in the shell that runs Gradle. Do not commit them or put them in
`local.properties`.

```bash
export PAPER_SUDOKU_KEYSTORE=/secure/path/paper-sudoku-upload.jks
export PAPER_SUDOKU_STORE_PASSWORD='...'
export PAPER_SUDOKU_KEY_ALIAS=paper-sudoku
export PAPER_SUDOKU_KEY_PASSWORD='...'

./gradlew --no-daemon :app:bundleRelease
```

Without those variables, `assembleRelease` still verifies R8/resource shrinking but produces an
unsigned artifact. A Play upload must use the signed AAB. Enroll in Play App Signing and keep the
upload key separate from the app-signing key.

## Tagged release (CI)

**Sideload:** `git tag v9.0.0-sideload && git push origin v9.0.0-sideload` publishes an APK for
testing outside Play, signed with the fixed sideload key (see [Sideload key](#sideload-key)) when
its four secrets are set, otherwise with the CI runner's debug key. Debug-key builds each have a
different key, so testers uninstall before updating; the release notes say which key was used.
Not for Play.

Without tag-push access, run the **Release** workflow from the Actions tab with the tag name
(for example `v9.0.0-sideload`); it creates the tag on the commit it builds.

**Signed (Play-ready):**

Store the upload key as repository secrets, then push a tag:

```bash
base64 -w0 paper-sudoku-upload.jks   # → PAPER_SUDOKU_KEYSTORE_BASE64
# also: PAPER_SUDOKU_STORE_PASSWORD, PAPER_SUDOKU_KEY_ALIAS, PAPER_SUDOKU_KEY_PASSWORD
git tag v9.0.0 && git push origin v9.0.0
```

`.github/workflows/release.yml` runs the tests and lint, builds the signed AAB and attaches it
alone to a GitHub release with `CHANGELOG.md` as notes. No APK is published for a plain tag:
under Play App Signing, Play re-signs the app, so an upload-key APK could neither update from Play
nor be updated by it. Either kind of tag first checks the release APK with `tools/ci/check-apk.sh`
(permissions exactly as `tools/ci/permissions.allowed`, Baseline Profile packaged, zip alignment
with `zipalign -c -P 16 -v 4`, size printed). Without all four upload-key secrets the workflow
stops with an error instead of publishing an unsigned or debug-signed build. The AAB is not run
through `bundletool validate`: bundletool is published on Google's Maven repository and GitHub,
not Maven Central, so the workflow does not fetch it; Play validates the AAB on upload.

Before it builds anything, `tools/ci/release-preflight.sh` checks that the tag is `v<versionName>`
(or `-sideload`), that `versionCode` matches `versionName` (below), that `CHANGELOG.md` has a
non-empty section for that version, that no GitHub release exists for the tag yet, that the
commit is on `main`, and that Android CI passed on that exact commit (wait for it after merging).

**Version code:** `versionCode = major × 10000 + minor × 100 + patch` of `versionName`, so
`9.0.0` is `90000` and `9.1.2` would be `90102` (minor and patch stay below 100, no leading
zeros). Every 9.x code is above the 1–8 used before, so Play and sideload updates keep working.

### Jobs, permissions and the `release` environment

The workflow runs three jobs, one release at a time (`concurrency: release`; a release started
meanwhile waits):

1. **preflight**: read-only token (`contents: read`, `actions: read`); no secrets.
2. **build**: tests, lint, build, signing, checks; runs in the GitHub environment `release` and
   is the only job that reads the signing secrets. Its token is read-only. It keeps the files
   (`paper-sudoku-<version>.apk` or `.aab`, and `release-notes.md`) as the run's
   `paper-sudoku-release` artifact (14 days).
3. **publish**: `contents: write` to create the release and its tag, from that artifact; no
   secrets, no checkout. Skipped in a dry run.

GitHub creates the `release` environment the first time the workflow runs. To approve each
release by hand before the signing secrets are used, the owner opens **Settings → Environments →
release** and adds **Required reviewers** (themselves), optionally with **Deployment branches and
tags** limited to `v*` tags and `main` (dry runs from other branches are then refused). Every
run then waits at the build job until approved (dry
runs too). For the approval to guard the secrets themselves, move the eight `PAPER_SUDOKU_*`
secrets from repository secrets to that environment's secrets (same names; the workflow reads
them the same way); repository secrets are readable by any workflow.

### Dry run

Actions → **Release** → **Run workflow**, choose the branch, enter the tag (e.g. `v9.0.0` or
`v9.0.0-sideload`) and tick **Dry run**. From the command line:

```bash
gh workflow run release.yml --ref <branch> -f tag=v9.0.0 -f dry_run=true
```

A dry run does everything a release does (preflight's tag, versionCode and changelog checks,
tests, lint, build, signing, `check-apk.sh`, signature verification, release notes), then
publishes nothing and creates no tag: download `paper-sudoku-release` from the run's page to
look at the files, and the notes are also shown in the run's summary. It skips the checks that
only matter for publishing (no existing release, commit on `main`, Android CI passed), so it can
build a branch before it is merged. A dry run of a plain tag without the upload-key secrets
signs with a throwaway key generated on the runner (`keytool`, PKCS12, alias `dry-run`, valid
two days), so the signed-AAB path runs end to end; its notes start with "Dry run: signed with a
throwaway key". That AAB can never be uploaded to Play. With the secrets set, a dry run signs
with the real upload key.

### Sideload key

A fixed key for the `-sideload` APKs, separate from the Play upload key, lets one sideload build
install over the next and keep the notebook. Create it once, locally, outside the repository, and
keep it backed up with its passwords (losing it means testers uninstall once more):

```bash
keytool -genkeypair \
  -keystore paper-sudoku-sideload.jks -storetype PKCS12 \
  -alias paper-sudoku-sideload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Paper Sudoku sideload"
base64 -w0 paper-sudoku-sideload.jks > paper-sudoku-sideload.jks.b64   # macOS: base64 -i paper-sudoku-sideload.jks
```

(keytool asks for the store password; with PKCS12 the key password is the same.) Then in the
repository's **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| --- | --- |
| `PAPER_SUDOKU_SIDELOAD_KEYSTORE_BASE64` | the contents of `paper-sudoku-sideload.jks.b64` |
| `PAPER_SUDOKU_SIDELOAD_STORE_PASSWORD` | the store password |
| `PAPER_SUDOKU_SIDELOAD_KEY_ALIAS` | `paper-sudoku-sideload` |
| `PAPER_SUDOKU_SIDELOAD_KEY_PASSWORD` | the key password (the store password for PKCS12) |

Delete the `.b64` file afterwards. With all four set, the next `-sideload` tag is signed with this
key (the workflow passes them to Gradle as `PAPER_SUDOKU_SIDELOAD_KEYSTORE`,
`_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`; locally, exporting the same four and building
with `-PpaperSudoku.sideload=true` does the same). With none set, it falls back to the runner's
debug key exactly as before, with a warning in the log and the debug-key wording in the notes;
with only some set, the build stops with an error.

Every sideload release's notes end with `Signing certificate SHA-256: <digest>` (from
`apksigner verify --print-certs`), so testers can check that an update comes from the same key:
`apksigner verify --print-certs paper-sudoku-<version>.apk` prints the same digest. With the fixed
key it stays the same from release to release.

Switching from the debug-signed sideload builds (8.0 and earlier) to the fixed key needs one last
uninstall: Android refuses an update signed with a different key. Android backup does not restore
data across a signing-key change either, so the notebook of a debug-signed install is lost on
that switch (the notes of every fixed-key build say so). Builds after that update in place.

## Preflight

```bash
./gradlew --no-daemon \
  :core:engine:test :core:game:test \
  :app:testDebugUnitTest \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest \
  :app:lintDebug \
  :app:bundleRelease

aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

The debug APK should report `com.titpea.papersudoku`, `minSdkVersion 26`, `targetSdkVersion 36`,
and no `INTERNET` permission. The UI tests need a connected device/emulator; they are not a
substitute for a signed release check.

## Play Console

1. Create the app with package name `com.titpea.papersudoku`; the package name is permanent.
2. Upload the signed AAB to internal testing first.
3. Complete Data Safety using `PRIVACY.md`: no data collected, no data shared, no network.
4. Complete content rating and target audience for a puzzle game.
5. Set pricing/monetization. The v1 recommendation is a one-time paid app; there is no billing
   code and no IAP.
6. Add phone and tablet screenshots (`StoreScreenshotsTest`: 1080×1920 phone, 1080×608 tablet (16:9);
   CI publishes them under `store/` on the captures branch), the feature graphic
   (`docs/assets/feature-graphic.png`, drawn by `tools/store/feature_graphic.py`), short/full
   description, and support contact.
7. Run the Play pre-launch report on at least one phone and one tablet.
8. Roll out to internal testing, then staged production.

## Before every release

- [ ] `PRIVACY.md` and Play Data Safety still match the build
- [ ] No `INTERNET`/billing/analytics dependency was added
- [ ] `versionName` updated and `versionCode` = major × 10000 + minor × 100 + patch (preflight checks)
- [ ] Signed AAB verified with the upload key
- [ ] TalkBack, 200% font, dark theme, rotation, and process-death restore checked on a device
- [ ] Engine/game/ViewModel tests pass; Room schema committed (CI checks)
- [ ] Upgrade test: install the previous Play build, play, then update; journal and in-progress game survive
- [ ] Store screenshots and descriptions match the shipped UI
