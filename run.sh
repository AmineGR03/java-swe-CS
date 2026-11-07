#!/usr/bin/env bash
# Grades the assessment.
#
#   ./run.sh                grade your code in src/
#   ./run.sh task3          grade one section
#   ./run.sh --quiet        failures and totals only
#   ./run.sh --reference    grade the reference solutions instead of src/
#
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root"

if ! command -v javac >/dev/null 2>&1; then
  echo "javac not found on PATH. Install a JDK 17 or newer." >&2
  exit 2
fi

source_dir="src"
runner_args=()
for arg in "$@"; do
  case "$arg" in
    --reference) source_dir="solutions" ;;
    *) runner_args+=("$arg") ;;
  esac
done

rm -rf out
mkdir -p out

mapfile -t files < <(find "$source_dir" tests -name '*.java' | sort)
echo "Compiling ${#files[@]} files from $source_dir/ ..."

if ! javac -d out -encoding UTF-8 -nowarn "${files[@]}"; then
  echo
  echo "Compilation failed - fix the errors above, then run again." >&2
  exit 1
fi

if [ ${#runner_args[@]} -eq 0 ]; then
  java -cp out harness.Runner
else
  java -cp out harness.Runner "${runner_args[@]}"
fi
