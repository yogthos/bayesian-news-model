#!/usr/bin/env bash
# Install a jolt-metrics release and put it on the job's PATH.
# Usage: ci/install-jolt-metrics.sh v0.6.0 x86_64-linux
set -euo pipefail
ver="$1"; target="$2"
dest="$HOME/.jolt-metrics-dist"; mkdir -p "$dest"
curl -fsSL -o "$RUNNER_TEMP/jm.tar.gz" "https://github.com/jlt-commons/jolt-metrics/releases/download/${ver}/jolt-metrics-${ver}-${target}.tar.gz"
tar xzf "$RUNNER_TEMP/jm.tar.gz" -C "$dest"
exe=$(find "$dest" -name jolt-metrics -type f -perm -u+x | head -1)
[ -n "$exe" ] || { echo "no jolt-metrics binary in the ${ver} ${target} archive"; exit 1; }
dirname "$exe" >> "$GITHUB_PATH"
"$exe" --help | head -1
