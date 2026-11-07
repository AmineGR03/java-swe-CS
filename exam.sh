#!/usr/bin/env bash
# Starts the exam environment.
#
#   ./exam.sh                    real clock (Part A 70 min, Part B 90 min)
#   ./exam.sh --port 9000        serve somewhere else
#   ./exam.sh --demo-seconds 120 short clock, for trying the environment out
#
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo "java not found on PATH. Install a JDK 17 or newer." >&2
  exit 2
fi

echo "Starting the exam environment. Open the printed address in a browser."
exec java exam/ExamServer.java "$@"
