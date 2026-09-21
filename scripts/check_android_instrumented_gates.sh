#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RESULTS_ROOT="app/build/outputs/androidTest-results"
MIN_TOTAL_TESTS=5
VIOLATIONS=0

required_classes=(
  "pro.sketchware.AndroidDeviceSmokeTest"
)

extract_attr() {
  local line="$1"
  local attr="$2"
  printf '%s\n' "$line" | sed -n "s/.* ${attr}=\"\([0-9][0-9]*\)\".*/\1/p"
}

collect_suite_files() {
  find "$RESULTS_ROOT" -type f -name 'TEST-*.xml' 2>/dev/null
}

suite_line_for_file() {
  local file="$1"
  grep -m1 '<testsuite ' "$file" || true
}

echo "Running Android instrumented quality gates..."

if [ ! -d "$RESULTS_ROOT" ]; then
  echo "[FAIL] Missing instrumented test results root: $RESULTS_ROOT"
  exit 1
fi

suite_files="$(collect_suite_files || true)"
if [ -z "$suite_files" ]; then
  echo "[FAIL] No instrumented test suite files found under $RESULTS_ROOT"
  exit 1
fi

total_tests=0
total_failures=0
total_errors=0

while IFS= read -r suite_file; do
  [ -z "$suite_file" ] && continue
  suite_line="$(suite_line_for_file "$suite_file")"
  if [ -z "$suite_line" ]; then
    echo "[FAIL] Missing <testsuite> metadata in: $suite_file"
    VIOLATIONS=$((VIOLATIONS + 1))
    continue
  fi

  tests="$(extract_attr "$suite_line" "tests")"
  failures="$(extract_attr "$suite_line" "failures")"
  errors="$(extract_attr "$suite_line" "errors")"

  total_tests=$((total_tests + ${tests:-0}))
  total_failures=$((total_failures + ${failures:-0}))
  total_errors=$((total_errors + ${errors:-0}))
done <<EOF
$suite_files
EOF

for required_class in "${required_classes[@]}"; do
  if printf '%s\n' "$suite_files" | grep -q "TEST-${required_class}.xml"; then
    echo "[PASS] Required smoke suite found: $required_class"
  else
    echo "[FAIL] Missing required smoke suite: $required_class"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done

if [ "$total_tests" -lt "$MIN_TOTAL_TESTS" ]; then
  echo "[FAIL] Instrumented test floor not met: $total_tests < $MIN_TOTAL_TESTS"
  VIOLATIONS=$((VIOLATIONS + 1))
else
  echo "[PASS] Instrumented test floor met: $total_tests >= $MIN_TOTAL_TESTS"
fi

if [ "$total_failures" -gt 0 ] || [ "$total_errors" -gt 0 ]; then
  echo "[FAIL] Instrumented suites report failures/errors (failures=$total_failures errors=$total_errors)"
  VIOLATIONS=$((VIOLATIONS + 1))
else
  echo "[PASS] Instrumented suites report zero failures/errors"
fi

if [ "$VIOLATIONS" -gt 0 ]; then
  echo "Android instrumented quality gates failed with $VIOLATIONS violation(s)."
  exit 1
fi

echo "Android instrumented quality gates passed."
