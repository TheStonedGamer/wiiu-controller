#!/usr/bin/env bash
set -euo pipefail
release_version=$(python3 tools/version.py)
release_tag="v$release_version"
release_repo="${GITHUB_REPOSITORY:?}"
release_commit="${GITHUB_SHA:?}"
release_dir="${1:?Asset directory required}"
# Existing published versions are immutable; future main pushes still run CI.
if gh release view "$release_tag" --repo "$release_repo" --json isDraft > /tmp/controller-release-state.json 2>/dev/null; then
  if python3 -c 'import json,sys;sys.exit(0 if not json.load(open("/tmp/controller-release-state.json"))["isDraft"] else 1)'; then
    echo "Release $release_tag already published; bump VERSION to publish another version."
    exit 0
  fi
  release_tag_commit=$(gh api "repos/$release_repo/commits/$release_tag" --jq .sha)
  test "$release_tag_commit" = "$release_commit" || { echo 'Draft belongs to another commit; refusing to replace it.' >&2; exit 1; }
else
  # If a maintainer pre-created a tag, it must refer to this exact source commit.
  if release_tag_commit=$(gh api "repos/$release_repo/commits/$release_tag" --jq .sha 2>/dev/null); then
    test "$release_tag_commit" = "$release_commit" || { echo 'Tag belongs to another commit.' >&2; exit 1; }
  fi
  gh release create "$release_tag" --repo "$release_repo" --target "$release_commit" --title "Wii U Controller $release_tag" --draft --notes-file docs/RELEASE_NOTES.md
fi
gh release upload "$release_tag" "$release_dir"/* --repo "$release_repo" --clobber
gh release edit "$release_tag" --repo "$release_repo" --draft=false --latest
echo "Published https://github.com/$release_repo/releases/tag/$release_tag"
