#!/bin/sh
# Prints one version's section of the changelog: from "## <version>" up to the next "## ".
# Usage: changelog-section.sh 4.0.0 CHANGELOG.md
awk -v v="$1" '
  $0 == "## " v { on = 1; print "# Paper Sudoku " v; next }
  on && /^## / { exit }
  on { print }
' "$2"
