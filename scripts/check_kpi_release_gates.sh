#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RESULTS_DIR="app/build/test-results/testDebugUnitTest"
MIN_TOTAL_TESTS=80
VIOLATIONS=0
WARNINGS=0

required_suites=(
  "TEST-pro.sketchware.metrics.KpiDashboardReleaseGatesEvaluatorTest.xml"
  "TEST-pro.sketchware.metrics.StartupRegressionPolicyTest.xml"
  "TEST-pro.sketchware.accessibility.AccessibilityChecksEngineTest.xml"
  "TEST-pro.sketchware.accessibility.AccessibilityCheckersTest.xml"
)

extract_attr() {
  local line="$1"
  local attr="$2"
  printf '%s\n' "$line" | sed -n "s/.* ${attr}=\"\([0-9][0-9]*\)\".*/\1/p"
}

check_suite_gate() {
  local suite_file="$1"
  local file_path="$RESULTS_DIR/$suite_file"

  if [ ! -f "$file_path" ]; then
    echo "[FAIL] Missing required suite: $suite_file"
    VIOLATIONS=$((VIOLATIONS + 1))
    return
  fi

  local suite_line
  suite_line="$(grep -m1 '<testsuite ' "$file_path" || true)"
  if [ -z "$suite_line" ]; then
    echo "[FAIL] Missing <testsuite> metadata in: $suite_file"
    VIOLATIONS=$((VIOLATIONS + 1))
    return
  fi

  local tests failures errors
  tests="$(extract_attr "$suite_line" "tests")"
  failures="$(extract_attr "$suite_line" "failures")"
  errors="$(extract_attr "$suite_line" "errors")"

  tests="${tests:-0}"
  failures="${failures:-0}"
  errors="${errors:-0}"

  if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
    echo "[FAIL] Suite failed: $suite_file (tests=$tests failures=$failures errors=$errors)"
    VIOLATIONS=$((VIOLATIONS + 1))
    return
  fi

  if [ "$tests" -le 0 ]; then
    echo "[FAIL] Suite has no tests: $suite_file"
    VIOLATIONS=$((VIOLATIONS + 1))
    return
  fi

  echo "[PASS] Required suite healthy: $suite_file (tests=$tests)"
}

echo "Running KPI release gate checks..."

if [ ! -d "$RESULTS_DIR" ]; then
  echo "[FAIL] Missing test results directory: $RESULTS_DIR"
  exit 1
fi

for suite in "${required_suites[@]}"; do
  check_suite_gate "$suite"
done

total_tests=0
total_failures=0
total_errors=0
suite_files_found=0

for file in "$RESULTS_DIR"/TEST-*.xml; do
  if [ ! -f "$file" ]; then
    continue
  fi

  suite_files_found=$((suite_files_found + 1))
  suite_line="$(grep -m1 '<testsuite ' "$file" || true)"
  if [ -z "$suite_line" ]; then
    echo "[WARN] Could not parse testsuite metadata: $file"
    WARNINGS=$((WARNINGS + 1))
    continue
  fi

  tests="$(extract_attr "$suite_line" "tests")"
  failures="$(extract_attr "$suite_line" "failures")"
  errors="$(extract_attr "$suite_line" "errors")"

  total_tests=$((total_tests + ${tests:-0}))
  total_failures=$((total_failures + ${failures:-0}))
  total_errors=$((total_errors + ${errors:-0}))
done

if [ "$suite_files_found" -eq 0 ]; then
  echo "[FAIL] No test suite files found under $RESULTS_DIR"
  VIOLATIONS=$((VIOLATIONS + 1))
fi

if [ "$total_tests" -lt "$MIN_TOTAL_TESTS" ]; then
  echo "[FAIL] Total test count below release-gate floor: $total_tests < $MIN_TOTAL_TESTS"
  VIOLATIONS=$((VIOLATIONS + 1))
else
  echo "[PASS] Total test coverage floor met: $total_tests >= $MIN_TOTAL_TESTS"
fi

if [ "$total_failures" -gt 0 ] || [ "$total_errors" -gt 0 ]; then
  echo "[FAIL] Aggregated test suites report failures/errors (failures=$total_failures errors=$total_errors)"
  VIOLATIONS=$((VIOLATIONS + 1))
else
  echo "[PASS] Aggregated suites report zero failures/errors"
fi

if [ "$VIOLATIONS" -gt 0 ]; then
  echo "KPI release gate checks failed with $VIOLATIONS violation(s) and $WARNINGS warning(s)."
  exit 1
fi

echo "KPI release gate checks passed with $WARNINGS warning(s)."
