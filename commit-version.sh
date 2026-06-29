#!/usr/bin/env bash
set -euo pipefail

if git diff --quiet -- gradle.properties; then
    exit 0
fi

version=$(grep '^version=' gradle.properties | cut -d= -f2-)

git add gradle.properties
git commit gradle.properties -m "chore: update version $version"
