# Release Checklist

## Product identity

- App name: **Paper Sudoku** / **纸上数独**
- `applicationId`: `com.titpea.papersudoku`
- Version: `2.0.0` (`versionCode 2`)
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

Store the upload key as repository secrets, then push a tag:

```bash
base64 -w0 paper-sudoku-upload.jks   # → PAPER_SUDOKU_KEYSTORE_BASE64
# also: PAPER_SUDOKU_STORE_PASSWORD, PAPER_SUDOKU_KEY_ALIAS, PAPER_SUDOKU_KEY_PASSWORD
git tag v2.0.0 && git push origin v2.0.0
```

`.github/workflows/release.yml` runs the tests and lint, builds the signed APK and AAB, and
attaches both to a GitHub release with `CHANGELOG.md` as notes. Without the secrets the workflow
stops with an error instead of publishing an unsigned or debug-signed build.

Upgrading from the 1.0 sideload APK: that APK was signed with a local debug key. Android will not
update it with a release-signed 2.0 build; users must uninstall 1.0 first (its local stats are
lost unless restored from an Android backup).

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
6. Add phone and tablet screenshots, feature graphic, short/full description, and support contact.
7. Run the Play pre-launch report on at least one phone and one tablet.
8. Roll out to internal testing, then staged production.

## Before every release

- [ ] `PRIVACY.md` and Play Data Safety still match the build
- [ ] No `INTERNET`/billing/analytics dependency was added
- [ ] `versionCode` increased and `versionName` updated
- [ ] Signed AAB verified with the upload key
- [ ] TalkBack, 200% font, dark theme, rotation, and process-death restore checked on a device
- [ ] Engine/game/ViewModel tests pass; Room schema committed (CI checks)
- [ ] Upgrade test: install the previous Play build, play, then update; journal and in-progress game survive
- [ ] Store screenshots and descriptions match the shipped UI
