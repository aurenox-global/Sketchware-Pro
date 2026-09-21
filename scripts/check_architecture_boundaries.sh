#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

VIOLATIONS=0

check_forbidden_imports() {
  local rule_name="$1"
  local forbidden_regex="$2"
  shift 2
  local dirs=("$@")
  local rule_hits=""

  for dir in "${dirs[@]}"; do
    if [ ! -d "$dir" ]; then
      continue
    fi

    while IFS= read -r -d '' file; do
      local hits
      hits="$(grep -nH -E "^[[:space:]]*import[[:space:]]+(${forbidden_regex})" "$file" || true)"
      if [ -n "$hits" ]; then
        rule_hits+="$hits"
        rule_hits+=$'\n'
      fi
    done < <(find "$dir" -type f \( -name "*.java" -o -name "*.kt" \) -print0)
  done

  if [ -n "$rule_hits" ]; then
    VIOLATIONS=$((VIOLATIONS + 1))
    echo "[FAIL] $rule_name"
    echo "$rule_hits"
  else
    echo "[PASS] $rule_name"
  fi
}

echo "Running architectural fitness checks..."

# Keep core configuration/storage packages independent from UI and legacy compiler/editor layers.
check_forbidden_imports \
  "Core packages must not depend on UI or legacy layers" \
  "com\\.besome\\.sketch|a\\.a\\.a|mod\\.|pro\\.sketchware\\.(activities|fragments|dialogs)\\." \
  "app/src/main/java/pro/sketchware/featureflags" \
  "app/src/main/java/pro/sketchware/metrics"

# Keep AI core services free of direct Activity imports.
check_forbidden_imports \
  "AI core package must not depend on Activities" \
  "pro\\.sketchware\\.activities\\." \
  "app/src/main/java/pro/sketchware/ai"

if [ "$VIOLATIONS" -gt 0 ]; then
  echo "Architectural fitness checks failed with $VIOLATIONS rule violation(s)."
  exit 1
fi

echo "Architectural fitness checks passed."
