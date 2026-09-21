# KMP GitLab Issue Import

This document explains how to create KMP Phase 7 issues in GitLab from prepared JSON payloads.

## Importer Script

- Script: scripts/create_kmp_phase7_gitlab_issues.sh
- Source payload strategy:
  - Auto-generate from docs using scripts/create_kmp_phase7_github_issues.sh --export-json <tmpdir>
  - Or use an existing JSON directory with --json-dir

## Behavior

- Default mode: dry-run (no issues created).
- Apply mode: creates issues through GitLab API.
- Upsert mode: in apply mode, updates existing issues with the same exact title instead of creating duplicates.

## Prerequisites

- For dry-run: python3 required.
- For apply mode:
  - python3
  - curl
  - GitLab project ID
  - GitLab API token (via --token or GITLAB_TOKEN)

## Commands

Dry-run with auto-generated JSON payloads:

```bash
scripts/create_kmp_phase7_gitlab_issues.sh
```

Dry-run using an existing JSON directory:

```bash
scripts/create_kmp_phase7_gitlab_issues.sh --json-dir /tmp/kmp-issues
```

Create issues in GitLab:

```bash
scripts/create_kmp_phase7_gitlab_issues.sh --apply --project-id 123456 --token "$GITLAB_TOKEN"
```

Create issues idempotently (upsert by exact title):

```bash
scripts/create_kmp_phase7_gitlab_issues.sh --apply --upsert --project-id 123456 --token "$GITLAB_TOKEN"
```

Create issues with explicit GitLab URL (self-hosted):

```bash
scripts/create_kmp_phase7_gitlab_issues.sh --apply --gitlab-url https://gitlab.example.com --project-id 123456 --token "$GITLAB_TOKEN"
```

Create issues and assign milestone ID:

```bash
scripts/create_kmp_phase7_gitlab_issues.sh --apply --project-id 123456 --milestone-id 42 --token "$GITLAB_TOKEN"
```

## What Gets Created

- Sprint 1 stories:
  - KMP-101, KMP-102, KMP-103, KMP-104, KMP-201, KMP-202
- Sprint 2 stories:
  - KMP-203, KMP-206, KMP-301, KMP-302, KMP-303, KMP-304
- Optional stretch:
  - KMP-401

Total payload definitions: 13 issues.

## Notes

- Labels are imported from payload JSON and sent as comma-separated GitLab labels.
- Milestone in payload JSON is not used directly for GitLab; use --milestone-id.
- Upsert matching is by exact issue title.
- If docs batches change, regenerate payloads and run dry-run before apply.
