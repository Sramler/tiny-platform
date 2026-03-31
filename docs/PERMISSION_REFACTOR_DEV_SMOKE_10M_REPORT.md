# Permission Refactor Dev Smoke 10M Report

## 1. Execution Window

- Start: 2026-03-25 12:26:03
- End: 2026-03-25 12:26:18
- Duration: ~10m automation window (active trigger mode)
- Tenant scope: 1, 3
- Scope types: PLATFORM, TENANT

## 2. Action Checklist

| action | result |
| --- | --- |
| PLATFORM login path | PASS |
| TENANT switch path | PASS |
| role_assignment change linkage | PASS |
| permission.enabled toggle + restore | PASS |
| role_hierarchy change linkage | PASS |

## 3. Core Signals

| signal | count |
| --- | ---: |
| DENY_DISABLED | 24 |
| DENY_UNKNOWN | 0 |
| ROLE_ASSIGNMENT_CHANGED | 0 |
| OLD_PERMISSION_INPUT_CHANGED | 0 |
| ROLE_PERMISSION_CHANGED | 0 |
| PERMISSION_MASTER_CHANGED | 0 |
| ROLE_HIERARCHY_CHANGED | 0 |

- Total matched events: 24
- Signal detail file: `test-results/dev-smoke-10m-signals.md`

## 4. SQL Snapshot

- SQL output file: `test-results/dev-smoke-10m-sql.log`
- Includes:
  - permission enabled distribution
  - active role_assignment bucket summary
  - role_hierarchy edge summary

## 5. Non-Menu Guardrail

- Menu chain migration: not touched
- /sys/menus/tree SQL path: not touched

## 6. Conclusion

- Result: **PASS**
- Decision:
  - PASS -> proceed to integration/testing stage
  - FAIL -> fix and rerun 10m smoke
