#!/bin/sh
# Checks a release before anything is built:
# - the tag names the app's versionName: v<versionName> or v<versionName>-sideload;
# - versionCode is major × 10000 + minor × 100 + patch of versionName (9.0.0 → 90000);
# - CHANGELOG.md has a non-empty section for that version (it becomes the release notes);
# - unless SKIP_CI_CHECK=1 or DRY_RUN=1: no GitHub release exists for the tag yet, the commit is
#   on main, and its latest "Android CI" push run passed. A dry run publishes nothing, so it may
#   build any commit (a branch before it is merged, or a tag already released).
# Usage: release-preflight.sh <tag> <commit sha>
# Needs GH_TOKEN and GITHUB_REPOSITORY for the GitHub checks (the workflow needs `actions: read`).
set -eu
tag="$1"
sha="$2"
here=$(dirname "$0")
root="$here/../.."

fail() {
  echo "::error::$1"
  exit 1
}

case "$tag" in
  v*) ;;
  *) fail "Tag $tag does not start with v" ;;
esac
version="${tag#v}"
version="${version%-sideload}"
app_version=$(sed -n 's/^ *versionName = "\([^"]*\)"$/\1/p' "$root/app/build.gradle.kts")
[ -n "$app_version" ] || fail "No versionName in app/build.gradle.kts"
[ "$version" = "$app_version" ] || fail "Tag $tag is for $version, but app/build.gradle.kts has versionName $app_version"
echo "Tag $tag matches versionName $app_version."

# No leading zeros (the shell would read 08 as octal); minor and patch stay below 100.
echo "$app_version" | grep -Eq '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$' \
  || fail "versionName $app_version is not major.minor.patch (no leading zeros, minor and patch below 100)"
major=${app_version%%.*}
rest=${app_version#*.}
minor=${rest%%.*}
patch=${rest#*.}
expected_code=$((major * 10000 + minor * 100 + patch))
app_code=$(sed -n 's/^ *versionCode = \([0-9][0-9]*\)$/\1/p' "$root/app/build.gradle.kts")
[ -n "$app_code" ] || fail "No versionCode in app/build.gradle.kts"
[ "$app_code" = "$expected_code" ] \
  || fail "versionCode is $app_code, but versionName $app_version needs $expected_code (major × 10000 + minor × 100 + patch)"
echo "versionCode $app_code matches versionName $app_version."

notes=$(sh "$here/changelog-section.sh" "$version" "$root/CHANGELOG.md" | sed 1d)
echo "$notes" | grep -q '[^[:space:]]' || fail "CHANGELOG.md has no (or an empty) \"## $version\" section"
echo "CHANGELOG.md has notes for $version."

if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "::notice::Dry run: no check for an existing release, main or Android CI; nothing will be published."
  exit 0
fi
if [ "${SKIP_CI_CHECK:-0}" = "1" ]; then
  echo "GitHub checks skipped."
  exit 0
fi

repo="$GITHUB_REPOSITORY"
# A tag is released once: fail now rather than after the build.
if out=$(gh api "repos/$repo/releases/tags/$tag" --jq .html_url 2>&1); then
  fail "A GitHub release for $tag already exists ($out); a new release needs a new version"
fi
case "$out" in
  *"(HTTP 404)"*) echo "No GitHub release for $tag yet." ;;
  *) fail "Could not check for an existing release of $tag: $out" ;;
esac

# main contains the commit when comparing it with main shows main ahead of (or equal to) it.
status=$(gh api "repos/$repo/compare/$sha...main" --jq .status) || fail "Could not compare $sha with main"
case "$status" in
  ahead | identical) echo "$sha is on main." ;;
  *) fail "$sha is not on main (compare status: $status); release only commits merged to main" ;;
esac

run=$(gh api "repos/$repo/actions/workflows/android.yml/runs?head_sha=$sha&event=push&per_page=20" \
  --jq '.workflow_runs | sort_by(.created_at) | last | if . == null then "none" else "\(.status) \(.conclusion) \(.html_url)" end') \
  || fail "Could not read the Android CI runs for $sha"
echo "Latest Android CI run for $sha: $run"
case "$run" in
  "completed success "*) echo "Android CI passed for $sha." ;;
  none) fail "No Android CI run on main for $sha" ;;
  *) fail "Android CI for $sha has not passed ($run); wait for it or fix it, then re-run" ;;
esac
