#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

PRIMARY_RESULTS_FILE="app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt"
FALLBACK_RESULTS_FILE="app/build/reports/lint-results-debug.txt"

if [ -f "$PRIMARY_RESULTS_FILE" ]; then
  RESULTS_FILE="$PRIMARY_RESULTS_FILE"
elif [ -f "$FALLBACK_RESULTS_FILE" ]; then
  RESULTS_FILE="$FALLBACK_RESULTS_FILE"
else
  echo "[FAIL] Lint results text report not found. Run ./gradlew :app:lintDebug first."
  exit 1
fi

fixed_count="$(grep -c "LintBaselineFixed" "$RESULTS_FILE" || true)"

if [ "$fixed_count" -gt 0 ]; then
  echo "[FAIL] Lint baseline drift detected: $fixed_count fixed issue(s) are still present in baseline."
  echo "Run ./gradlew :app:lintDebug --update-lint-baseline and commit app/lint-baseline.xml"
  grep -n "LintBaselineFixed" "$RESULTS_FILE" || true
  exit 1
fi

echo "[PASS] No lint baseline drift detected."
