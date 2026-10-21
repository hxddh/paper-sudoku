# Release Checklist

## Product identity

- App name: **Paper Sudoku** / **纸上数独**
- `applicationId`: `com.titpea.papersudoku`
- Version: `1.0.0` (`versionCode 1`)
- Minimum SDK: 26
- Target/compile SDK: 36
- No `INTERNET`, billing, ads, analytics, or account permissions

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

## Preflight

```bash
./gradlew --no-daemon \
  :core:engine:test \
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
- [ ] Daily puzzle bank validator and unit tests pass
- [ ] Store screenshots and descriptions match the shipped UI
