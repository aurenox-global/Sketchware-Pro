#!/usr/bin/env bash
set -euo pipefail

# Creates GitLab issues for KMP Phase 7 from JSON payload files.
# Safe by default: dry-run unless --apply is provided.

APPLY=0
UPSERT=0
GITLAB_URL="https://gitlab.com"
PROJECT_ID=""
TOKEN="${GITLAB_TOKEN:-}"
JSON_DIR=""
MILESTONE_ID=""

usage() {
  cat <<'EOF'
Usage:
  scripts/create_kmp_phase7_gitlab_issues.sh [--apply] [--upsert] [--gitlab-url URL] [--project-id ID] [--token TOKEN] [--json-dir PATH] [--milestone-id ID]

Options:
  --apply                 Actually create issues. Without this flag, script runs in dry-run mode.
  --upsert                In --apply mode, update an existing issue with the same title instead of creating duplicates.
  --gitlab-url URL        GitLab base URL (default: https://gitlab.com).
  --project-id ID         GitLab numeric project ID (required in --apply mode).
  --token TOKEN           GitLab API token (optional if GITLAB_TOKEN env var is set).
  --json-dir PATH         Directory with issue JSON payload files.
  --milestone-id ID       GitLab milestone ID to assign to all created issues (optional).
  -h, --help              Show this help.

Behavior:
  If --json-dir is omitted, this script auto-generates payloads using:
  scripts/create_kmp_phase7_github_issues.sh --export-json <tmpdir>

Examples:
  scripts/create_kmp_phase7_gitlab_issues.sh
  scripts/create_kmp_phase7_gitlab_issues.sh --json-dir /tmp/kmp-issues
  scripts/create_kmp_phase7_gitlab_issues.sh --apply --project-id 123456 --token "$GITLAB_TOKEN"
  scripts/create_kmp_phase7_gitlab_issues.sh --apply --upsert --project-id 123456 --token "$GITLAB_TOKEN"
  scripts/create_kmp_phase7_gitlab_issues.sh --apply --project-id 123456 --milestone-id 42
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)
      APPLY=1
      shift
      ;;
    --upsert)
      UPSERT=1
      shift
      ;;
    --gitlab-url)
      GITLAB_URL="$2"
      shift 2
      ;;
    --project-id)
      PROJECT_ID="$2"
      shift 2
      ;;
    --token)
      TOKEN="$2"
      shift 2
      ;;
    --json-dir)
      JSON_DIR="$2"
      shift 2
      ;;
    --milestone-id)
      MILESTONE_ID="$2"
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

if ! command -v python3 >/dev/null 2>&1; then
  echo "Error: python3 is required but was not found in PATH." >&2
  exit 1
fi

find_existing_issue_iid() {
  local title="$1"
  local api_url="${GITLAB_URL%/}/api/v4/projects/${PROJECT_ID}/issues"

  local response
  response="$(curl \
    --silent \
    --show-error \
    --fail \
    -G \
    -H "PRIVATE-TOKEN: ${TOKEN}" \
    --data-urlencode "search=${title}" \
    --data-urlencode "in=title" \
    --data-urlencode "state=all" \
    --data-urlencode "per_page=100" \
    "$api_url")"

  printf '%s' "$response" | python3 -c 'import json,sys; title=sys.argv[1]; data=json.load(sys.stdin); print(next((str(i.get("iid")) for i in data if i.get("title")==title), ""))' "$title"
}

if [[ "$APPLY" -eq 1 ]]; then
  if ! command -v curl >/dev/null 2>&1; then
    echo "Error: curl is required in --apply mode but was not found in PATH." >&2
    exit 1
  fi
  if [[ -z "$PROJECT_ID" ]]; then
    echo "Error: --project-id is required in --apply mode." >&2
    exit 1
  fi
  if [[ -z "$TOKEN" ]]; then
    echo "Error: --token or GITLAB_TOKEN is required in --apply mode." >&2
    exit 1
  fi
fi

generated_tmp=0
if [[ -z "$JSON_DIR" ]]; then
  JSON_DIR="$(mktemp -d)"
  generated_tmp=1
  scripts/create_kmp_phase7_github_issues.sh --export-json "$JSON_DIR" >/dev/null
fi

if [[ ! -d "$JSON_DIR" ]]; then
  echo "Error: JSON directory does not exist: $JSON_DIR" >&2
  exit 1
fi

json_files=()
while IFS= read -r file; do
  json_files+=("$file")
done < <(find "$JSON_DIR" -maxdepth 1 -type f -name '*.json' | sort)

if [[ ${#json_files[@]} -eq 0 ]]; then
  echo "Error: no JSON payload files found in: $JSON_DIR" >&2
  exit 1
fi

issue_count=0
created_count=0
updated_count=0

for json_file in "${json_files[@]}"; do
  meta_line="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1], encoding="utf-8")); print("\t".join([d.get("title", ""), ",".join(d.get("labels", [])), (d.get("source") or {}).get("file", ""), (d.get("source") or {}).get("heading", "")]))' "$json_file")"

  IFS=$'\t' read -r title labels source_file source_heading <<< "$meta_line"

  title="${title:-}"
  labels="${labels:-}"
  source_file="${source_file:-}"
  source_heading="${source_heading:-}"

  if [[ -z "$title" ]]; then
    echo "Error: missing title in $json_file" >&2
    exit 1
  fi

  if [[ "$APPLY" -eq 1 ]]; then
    body_file="$(mktemp)"
    python3 -c 'import json,sys; d=json.load(open(sys.argv[1], encoding="utf-8")); open(sys.argv[2], "w", encoding="utf-8").write(d.get("body", ""))' "$json_file" "$body_file"

    api_url="${GITLAB_URL%/}/api/v4/projects/${PROJECT_ID}/issues"

    curl_args=(
      --silent
      --show-error
      --fail
      -X POST
      -H "PRIVATE-TOKEN: ${TOKEN}"
      --data-urlencode "title=${title}"
      --data-urlencode "labels=${labels}"
      --data-urlencode "description@${body_file}"
    )

    if [[ -n "$MILESTONE_ID" ]]; then
      curl_args+=(--data-urlencode "milestone_id=${MILESTONE_ID}")
    fi

    existing_iid=""
    if [[ "$UPSERT" -eq 1 ]]; then
      existing_iid="$(find_existing_issue_iid "$title")"
    fi

    if [[ -n "$existing_iid" ]]; then
      response="$(curl "${curl_args[@]}" -X PUT "$api_url/${existing_iid}")"

      issue_iid="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("iid", ""))')"
      web_url="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("web_url", ""))')"

      echo "Updated: ${title} (iid=${issue_iid}) ${web_url}"
      updated_count=$((updated_count + 1))
    else
      response="$(curl "${curl_args[@]}" "$api_url")"

      issue_iid="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("iid", ""))')"
      web_url="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("web_url", ""))')"

      echo "Created: ${title} (iid=${issue_iid}) ${web_url}"
      created_count=$((created_count + 1))
    fi

    rm -f "$body_file"
  else
    body_lines="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1], encoding="utf-8")); print(len(d.get("body", "").splitlines()))' "$json_file")"

    echo "[DRY-RUN] ${title}"
    echo "  json: ${json_file}"
    echo "  labels: ${labels}"
    echo "  source: ${source_file} :: ${source_heading}"
    echo "  body_lines: ${body_lines}"
    if [[ "$UPSERT" -eq 1 ]]; then
      echo "  mode: upsert (create or update by exact title in apply mode)"
    fi
  fi

  issue_count=$((issue_count + 1))
done

echo "Done. Processed ${issue_count} issue payload files."
if [[ "$APPLY" -eq 0 ]]; then
  echo "Dry-run mode only. Re-run with --apply to create issues in GitLab."
else
  echo "Created ${created_count} GitLab issues."
  if [[ "$UPSERT" -eq 1 ]]; then
    echo "Updated ${updated_count} existing GitLab issues by title."
  fi
fi

if [[ "$generated_tmp" -eq 1 ]]; then
  echo "Note: payloads were auto-generated in temporary directory: ${JSON_DIR}"
fi
