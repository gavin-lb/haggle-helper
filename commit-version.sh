#!/usr/bin/env bash
set -euo pipefail

version=$(grep '^version=' gradle.properties | cut -d= -f2-)

if ! git diff --cached --quiet; then
    git stash push --staged
    trap 'git stash pop >/dev/null 2>&1 || true' EXIT
fi

git add gradle.properties
git commit -m "chore: update version $version"
