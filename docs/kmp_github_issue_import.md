# KMP GitHub Issue Import

This document explains how to create KMP Phase 7 issues in GitHub from the prepared Sprint 1 and Sprint 2 batch files.

## Importer Script

- Script: scripts/create_kmp_phase7_github_issues.sh
- Source issue bodies:
  - docs/kmp_s1_issue_batch.md
  - docs/kmp_s2_issue_batch.md

The script creates one GitHub issue per story and applies labels automatically.

## Behavior

- Default mode: dry-run (no issues created).
- Apply mode: creates issues using gh CLI.
- Export mode: writes one JSON payload file per issue.

## Prerequisites

- For dry-run: no extra tooling required.
- For JSON export: no extra tooling required.
- For apply mode: gh CLI installed and gh auth login completed for target repository.

## Commands

Dry-run:

```bash
scripts/create_kmp_phase7_github_issues.sh
```

Create issues in current gh repo context:

```bash
scripts/create_kmp_phase7_github_issues.sh --apply
```

Create issues in a specific repository:

```bash
scripts/create_kmp_phase7_github_issues.sh --apply --repo owner/repo
```

Create issues and assign a milestone:

```bash
scripts/create_kmp_phase7_github_issues.sh --apply --repo owner/repo --milestone "M1 - KMP Foundations"
```

Export JSON payload files (portable for systems without gh CLI):

```bash
scripts/create_kmp_phase7_github_issues.sh --export-json /tmp/kmp-issues
```

Export JSON payload files and also create issues:

```bash
scripts/create_kmp_phase7_github_issues.sh --apply --repo owner/repo --export-json /tmp/kmp-issues
```

## What Gets Created

- Sprint 1 stories:
  - KMP-101, KMP-102, KMP-103, KMP-104, KMP-201, KMP-202
- Sprint 2 stories:
  - KMP-203, KMP-206, KMP-301, KMP-302, KMP-303, KMP-304
- Optional stretch:
  - KMP-401

Total definitions: 13 issues.

## Notes

- In dry-run mode, gh CLI is not required.
- In apply mode, gh CLI and authentication are required.
- In export mode, payload files include title, body, labels, optional milestone/repo, and source section metadata.
- If issue titles or body sections are edited in batch docs, re-run dry-run to verify extraction before apply.
