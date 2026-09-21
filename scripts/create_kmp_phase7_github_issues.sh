#!/usr/bin/env bash
set -euo pipefail

# Creates GitHub issues for KMP Phase 7 stories from docs issue batches.
# Safe by default: dry-run unless --apply is provided.

APPLY=0
REPO=""
MILESTONE=""
EXPORT_JSON_DIR=""

usage() {
  cat <<'EOF'
Usage:
  scripts/create_kmp_phase7_github_issues.sh [--apply] [--repo owner/repo] [--milestone "M1 - KMP Foundations"] [--export-json path]

Options:
  --apply                 Actually create issues. Without this flag, script runs in dry-run mode.
  --repo owner/repo       Target repository (optional). If omitted, uses current gh repo context.
  --milestone NAME        Milestone name to assign (optional).
  --export-json PATH      Export one JSON payload file per issue to PATH.
  -h, --help              Show this help.

Examples:
  scripts/create_kmp_phase7_github_issues.sh
  scripts/create_kmp_phase7_github_issues.sh --export-json /tmp/kmp-issues
  scripts/create_kmp_phase7_github_issues.sh --apply --repo owner/repo
  scripts/create_kmp_phase7_github_issues.sh --apply --milestone "M1 - KMP Foundations"
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)
      APPLY=1
      shift
      ;;
    --repo)
      REPO="$2"
      shift 2
      ;;
    --milestone)
      MILESTONE="$2"
      shift 2
      ;;
    --export-json)
      EXPORT_JSON_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$APPLY" -eq 1 ]]; then
  if ! command -v gh >/dev/null 2>&1; then
    echo "Error: gh CLI is required in --apply mode but was not found in PATH." >&2
    exit 1
  fi

  if ! gh auth status >/dev/null 2>&1; then
    echo "Error: gh CLI is not authenticated. Run: gh auth login" >&2
    exit 1
  fi
fi

extract_section_body() {
  local file="$1"
  local heading="$2"

  awk -v marker="## ${heading}" '
    $0 == marker { in_section = 1; next }
    in_section && /^## / { exit }
    in_section { print }
  ' "$file"
}

json_escape() {
  printf '%s' "$1" | awk '
    BEGIN { ORS = "" }
    {
      gsub(/\\/, "\\\\")
      gsub(/"/, "\\\"")
      gsub(/\t/, "\\t")
      gsub(/\r/, "\\r")
      if (NR > 1) {
        printf "\\n"
      }
      printf "%s", $0
    }
  '
}

json_nullable_string() {
  local value="$1"
  if [[ -n "$value" ]]; then
    printf '"%s"' "$(json_escape "$value")"
  else
    printf 'null'
  fi
}

repo_args=()
if [[ -n "$REPO" ]]; then
  repo_args+=(--repo "$REPO")
fi

milestone_args=()
if [[ -n "$MILESTONE" ]]; then
  milestone_args+=(--milestone "$MILESTONE")
fi

if [[ -n "$EXPORT_JSON_DIR" ]]; then
  mkdir -p "$EXPORT_JSON_DIR"
fi

issue_count=0
export_count=0

while IFS='|' read -r file source_heading issue_title priority sprint area epic; do
  [[ -z "$file" ]] && continue

  if [[ ! -f "$file" ]]; then
    echo "Error: file not found: $file" >&2
    exit 1
  fi

  body="$(extract_section_body "$file" "$source_heading")"
  if [[ -z "$body" ]]; then
    echo "Error: could not extract section: '$source_heading' from $file" >&2
    exit 1
  fi

  label_values=("kmp" "story" "$priority" "$sprint" "$area" "$epic")
  labels=()
  for label in "${label_values[@]}"; do
    labels+=(--label "$label")
  done

  if [[ -n "$EXPORT_JSON_DIR" ]]; then
    safe_title="$(printf '%s' "$issue_title" | sed -E 's/[^A-Za-z0-9._-]+/_/g')"
    json_file="$EXPORT_JSON_DIR/${safe_title}.json"

    labels_json="["
    for label in "${label_values[@]}"; do
      if [[ "$labels_json" != "[" ]]; then
        labels_json+=" , "
      fi
      labels_json+="\"$(json_escape "$label")\""
    done
    labels_json+="]"

    cat > "$json_file" <<EOF
{
  "title": "$(json_escape "$issue_title")",
  "body": "$(json_escape "$body")",
  "labels": ${labels_json},
  "milestone": $(json_nullable_string "$MILESTONE"),
  "repo": $(json_nullable_string "$REPO"),
  "source": {
    "file": "$(json_escape "$file")",
    "heading": "$(json_escape "$source_heading")"
  }
}
EOF
    export_count=$((export_count + 1))
  fi

  if [[ "$APPLY" -eq 1 ]]; then
    gh issue create \
      "${repo_args[@]}" \
      "${milestone_args[@]}" \
      --title "$issue_title" \
      "${labels[@]}" \
      --body "$body"
    echo "Created: $issue_title"
  else
    echo "[DRY-RUN] $issue_title"
    echo "  file: $file"
    echo "  source: ## $source_heading"
    echo "  labels: kmp, story, $priority, $sprint, $area, $epic"
    echo "  body_lines: $(printf '%s\n' "$body" | wc -l | tr -d ' ')"
    if [[ -n "$EXPORT_JSON_DIR" ]]; then
      echo "  json: ${json_file}"
    fi
  fi

  issue_count=$((issue_count + 1))
done <<'EOF'
docs/kmp_s1_issue_batch.md|KMP-101: Define KmpProject schema and serialization contract|KMP-101: Define KmpProject schema and serialization contract|p0|sprint-1|area-core-model|epic-kmp-01
docs/kmp_s1_issue_batch.md|KMP-102: Implement source set hierarchy contract validation|KMP-102: Implement source set hierarchy contract validation|p0|sprint-1|area-core-model|epic-kmp-01
docs/kmp_s1_issue_batch.md|KMP-103: Add deterministic KMP project scaffold generator|KMP-103: Add deterministic KMP project scaffold generator|p0|sprint-1|area-build-system|epic-kmp-01
docs/kmp_s1_issue_batch.md|KMP-104: Gate KMP project creation with feature flag|KMP-104: Gate KMP project creation with feature flag|p0|sprint-1|area-build-system|epic-kmp-01
docs/kmp_s1_issue_batch.md|KMP-201: Add compile pipeline stages for commonMain and target fan-out|KMP-201: Add compile pipeline stages for commonMain and target fan-out|p0|sprint-1|area-build-system|epic-kmp-02
docs/kmp_s1_issue_batch.md|KMP-202: Integrate Android target packaging path|KMP-202: Integrate Android target packaging path|p0|sprint-1|area-build-system|epic-kmp-02
docs/kmp_s2_issue_batch.md|KMP-203: Integrate Desktop target packaging path|KMP-203: Integrate Desktop target packaging path|p0|sprint-2|area-build-system|epic-kmp-02
docs/kmp_s2_issue_batch.md|KMP-206: Unified build report and artifact index|KMP-206: Unified build report and artifact index|p0|sprint-2|area-build-system|epic-kmp-02
docs/kmp_s2_issue_batch.md|KMP-301: Add block scope metadata model|KMP-301: Add block scope metadata model|p0|sprint-2|area-blocks|epic-kmp-03
docs/kmp_s2_issue_batch.md|KMP-302: Implement target compatibility validator in editor|KMP-302: Implement target compatibility validator in editor|p0|sprint-2|area-editor|epic-kmp-03
docs/kmp_s2_issue_batch.md|KMP-303: Implement expect/actual generator from block contracts|KMP-303: Implement expect/actual generator from block contracts|p0|sprint-2|area-blocks|epic-kmp-03
docs/kmp_s2_issue_batch.md|KMP-304: Add initial template catalog seed (logger, key-value, clock)|KMP-304: Add initial template catalog seed (logger, key-value, clock)|p1|sprint-2|area-blocks|epic-kmp-03
docs/kmp_s2_issue_batch.md|Optional Stretch: KMP-401 Add dependency compatibility resolver by target|KMP-401: Add dependency compatibility resolver by target|p1|sprint-2|area-dependencies|epic-kmp-04
EOF

echo "Done. Processed $issue_count issue definitions."
if [[ -n "$EXPORT_JSON_DIR" ]]; then
  echo "Exported $export_count JSON payload files to $EXPORT_JSON_DIR."
fi
if [[ "$APPLY" -eq 0 ]]; then
  echo "Dry-run mode only. Re-run with --apply to create issues."
fi
