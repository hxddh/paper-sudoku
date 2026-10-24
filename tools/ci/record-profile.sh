#!/bin/sh
# Records the Baseline Profile of the real journey on the CI emulator and prints what it ran
# outside the committed app/src/main/baseline-prof.txt, to extend that file from. The release
# ships the committed profile, so this only reports: a recording that fails (the profile rule and
# newer system images do not always agree on `pm dump-profiles` output) is a warning, not a red
# build. A script of its own because the emulator step runs its script one line at a time.
./gradlew --no-daemon :benchmark:connectedBenchmarkAndroidTest -PpaperSudoku.benchmarkRules=BaselineProfile ||
  echo "::warning::Recording the Baseline Profile failed; the committed profile still ships"
generated=$(find benchmark/build/outputs -name "*generate-baseline-prof.txt" | head -n 1)
if [ -n "$generated" ]; then
  cp "$generated" app/build/outputs/generated-baseline-prof.txt
  find benchmark/build/outputs -name "*generate-startup-prof.txt" -exec cp {} app/build/outputs/generated-startup-prof.txt \; || true
  python3 tools/ci/profile-coverage.py "$generated" app/src/main/baseline-prof.txt || true
else
  echo "::warning::The emulator recorded no Baseline Profile"
fi
