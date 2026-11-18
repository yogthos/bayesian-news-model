#!/usr/bin/env bash
# Install a jolt release and put it on the job's PATH.
# Usage: ci/install-jolt.sh v0.8.27 x86_64-linux
set -euo pipefail
ver="$1"; target="$2"
dest="$HOME/.jolt-dist"; mkdir -p "$dest"
curl -fsSL -o "$RUNNER_TEMP/jolt.tar.gz" "https://github.com/jolt-lang/jolt/releases/download/${ver}/jolt-${ver}-${target}.tar.gz"
tar xzf "$RUNNER_TEMP/jolt.tar.gz" -C "$dest"
exe=$(find "$dest" -name jolt -type f -perm -u+x | head -1)
[ -n "$exe" ] || { echo "no jolt binary in the ${ver} ${target} archive"; exit 1; }
dirname "$exe" >> "$GITHUB_PATH"
"$exe" --version
