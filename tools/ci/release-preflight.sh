#!/bin/sh
# Checks a release before anything is built:
# - the tag names the app's versionName: v<versionName> or v<versionName>-sideload;
# - CHANGELOG.md has a non-empty section for that version (it becomes the release notes);
# - unless SKIP_CI_CHECK=1: the commit is on main and its latest "Android CI" push run passed.
# Usage: release-preflight.sh <tag> <commit sha>
# Needs GH_TOKEN and GITHUB_REPOSITORY for the CI check (the workflow needs `actions: read`).
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

notes=$(sh "$here/changelog-section.sh" "$version" "$root/CHANGELOG.md" | sed 1d)
echo "$notes" | grep -q '[^[:space:]]' || fail "CHANGELOG.md has no (or an empty) \"## $version\" section"
echo "CHANGELOG.md has notes for $version."

if [ "${SKIP_CI_CHECK:-0}" = "1" ]; then
  echo "CI check skipped."
  exit 0
fi

repo="$GITHUB_REPOSITORY"
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
