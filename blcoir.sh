#!/usr/bin/env bash
# Runs the BLCoiR reproduction. Build first with:  mvn package
# Usage: ./blcoir.sh <query|eval|run|explain> [options]   (see README-REPRODUCTION.md)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
exec java -Xmx6g -Dblcoir.home="$ROOT" -cp "$ROOT/target/BLCoiR-1.0.jar:$ROOT/target/lib/*" uc.eecs.blcoir.BLCoiR "$@"
