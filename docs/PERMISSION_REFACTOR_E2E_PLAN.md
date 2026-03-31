# Permission Refactor E2E Plan

## Scope

- Goal: convert Phase A-F outputs into repeatable E2E assets.
- Priority: non-menu chain first.
- Non-goals:
  - no menu authorization source migration
  - no fallback removal
  - no new permission model redesign

## Test Suites

1. **Suite 1 - Auth and context chain**
   - PLATFORM login context
   - TENANT login context
   - same user tenant switch

2. **Suite 2 - Permission change linkage**
   - role assignment add/remove
   - permission.enabled 1->0->1
   - role_hierarchy add/remove edge

3. **Suite 3 - New/old path consistency**
   - new path full cover case
   - old fallback case
   - deny disabled / deny unknown

4. **Suite 4 - Scope bucket isolation**
   - PLATFORM vs TENANT
   - tenant 1 vs tenant 3
   - ORG/DEPT (pending if fixture missing)

5. **Suite 5 - Menu non-drift regression**
   - `/sys/menus/tree` structural stability
   - permission-side changes should not drift menu output

6. **Suite 6 - Short stability loop**
   - repeated login + tenant switch
   - repeated role/permission/hierarchy mutate-restore loop

## Execution Layers

- **Layer A (required each PR):**
  - targeted Java integration tests already in `src/test/java`
  - smoke script `run-permission-dev-smoke-10m.sh`
- **Layer B (required for integration branch):**
  - Playwright real-link permission-refactor suite skeleton in `src/main/webapp/e2e/real/permission-refactor-non-menu.spec.ts`
- **Layer C (pre-release):**
  - full E2E matrix with prepared data SQL + cleanup SQL

## Data Lifecycle

- Prepare script: `tiny-oauth-server/scripts/e2e/prepare-permission-refactor-e2e-data.sql`
- Cleanup script: `tiny-oauth-server/scripts/e2e/cleanup-permission-refactor-e2e-data.sql`
- Must restore:
  - role_assignment test rows
  - permission.enabled toggles
  - role_hierarchy test edges

## Result Artifacts

- `test-results/permission-refactor-e2e-summary.json`
- `test-results/permission-refactor-e2e-summary.md`
- `test-results/permission-refactor-e2e-test.log`

## Entry Commands

- Dev smoke:
  - `tiny-oauth-server/scripts/run-permission-dev-smoke-10m.sh`
- E2E skeleton run (real-link):
  - `cd tiny-oauth-server/src/main/webapp && npx playwright test e2e/real/permission-refactor-non-menu.spec.ts --config=playwright.real.config.ts`
- Unified wrapper:
  - `tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh`
