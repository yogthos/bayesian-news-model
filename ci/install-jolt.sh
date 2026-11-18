#!/usr/bin/env bash
# Install a jolt release, check it against its published SHA-256, and put it
# on the job's PATH.  Usage: ci/install-jolt.sh v0.8.27 aarch64-macos
set -euo pipefail
ver="$1"; target="$2"
dest="$HOME/.jolt-dist"; mkdir -p "$dest"
url="https://github.com/jolt-lang/jolt/releases/download/${ver}/jolt-${ver}-${target}.tar.gz"
curl -fsSL -o "$RUNNER_TEMP/jolt.tar.gz" "$url"
curl -fsSL -o "$RUNNER_TEMP/jolt.tar.gz.sha256" "${url}.sha256"
want=$(awk '{print $1}' "$RUNNER_TEMP/jolt.tar.gz.sha256")
got=$( (command -v sha256sum >/dev/null && sha256sum "$RUNNER_TEMP/jolt.tar.gz" || shasum -a 256 "$RUNNER_TEMP/jolt.tar.gz") | awk '{print $1}')
[ "$want" = "$got" ] || { echo "jolt ${ver} ${target}: checksum mismatch"; exit 1; }
tar xzf "$RUNNER_TEMP/jolt.tar.gz" -C "$dest"
exe=$(find "$dest" -name jolt -type f -perm -u+x | head -1)
[ -n "$exe" ] || { echo "no jolt binary in the ${ver} ${target} archive"; exit 1; }
dirname "$exe" >> "$GITHUB_PATH"
"$exe" --version
