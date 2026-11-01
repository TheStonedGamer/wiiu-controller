#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
export LOCAL_UID="${SUDO_UID:-$(id -u)}"
export LOCAL_GID="${SUDO_GID:-$(id -g)}"
mkdir -p dist
if [ "$#" -eq 0 ]; then set -- 3ds wiiu android ios; fi
for platform in "$@"; do
  case "$platform" in
    3ds|wiiu|android|ios) ;;
    *) echo "Usage: $0 [3ds|wiiu|android|ios ...]" >&2; exit 2 ;;
  esac
done
for platform in "$@"; do
  docker compose build "build-$platform"
  docker compose run --rm "build-$platform"
done
