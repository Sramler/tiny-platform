# Tiny Platform Tenant Naming Guidelines

## Goal

Split tenant semantics so one field name no longer carries multiple meanings.

## Naming Rules

- `activeTenantId`
  - Current active tenant context.
  - Used by auth, session, token claims, route state, page context, and active-tenant-scoped requests.
- `recordTenantId`
  - Tenant ownership of a business record or an ownership-based list filter.
  - Used by process definitions, deployments, instances, historic instances, tasks, and similar business rows.
- `executionTenantId`
  - Tenant context captured into background execution, Quartz job data, worker execution context, and execution logs.
  - Used by scheduling runtime execution models where the value is neither current page context nor record ownership.
- `createdTenantId`
  - Tenant identifier produced by a control-plane create action.
  - Used in create responses where the result is “a new tenant was created”.

## What Should Not Be Renamed Yet

- Database entity ownership fields that are still true persistence attributes, such as storage-layer `tenantId` columns in JPA entities/repositories and export demo entities.
- Internal service/repository variables whose meaning is strictly “tenant ownership column in storage”.
- Storage/transport names that have not yet been migrated at the infrastructure layer.

These are persistence or transport concerns, not page-context concerns.

## First Slice Applied

The process/workflow module is the first completed slice:

- Route/page context uses `activeTenantId`.
- Record ownership/filter fields use `recordTenantId`.
- Tenant creation returns `createdTenantId`.

The scheduling module is the second applied slice:

- Current-context list/filter APIs no longer accept legacy `tenantId` query semantics.
- Create/update DTOs no longer accept `tenantId` as an input field.
- Scheduling record JSON now exposes ownership as `recordTenantId`.

The idempotent governance metrics module is the third applied slice:

- Current-context metrics filters now use `activeTenantId`.
- Metrics responses no longer expose active-tenant filters as `tenantId`.
- Idempotent console metrics follow the same `activeTenantId` naming.

The export demo/test-data module is the fourth applied slice:

- Operator-selected filters and generation/clear actions now use `activeTenantId`.
- Demo export records now expose ownership as `recordTenantId`.
- Export examples and test-data pages no longer use external `tenantId` semantics for current context.

The scheduling execution/logging runtime is the fifth applied slice:

- Quartz job data now uses `executionTenantId`.
- Scheduling execution context and executor logs no longer overload `tenantId` for runtime tenant semantics.
- Scheduling runtime keeps `recordTenantId` for persisted records and `executionTenantId` for execution context.

The dict module is the sixth applied slice:

- Dict type/item JSON responses now expose ownership as `recordTenantId`.
- Dict create/update inputs no longer use external `tenantId` semantics for current context.
- Dict pages only use `recordTenantId` for record ownership display; active tenant selection stays in auth/page context.

The auth control-plane user/role/resource module is the seventh applied slice:

- User, role, and resource external JSON now expose record ownership as `recordTenantId`.
- User/resource raw-entity detail and mutation endpoints no longer leak ambiguous external `tenantId` semantics.
- Front-end control-plane API types now distinguish `recordTenantId` from `activeTenantId`.

The transport contract is the eighth applied slice:

- Current active tenant request headers now use `X-Active-Tenant-Id`.
- Front-end request/auth/trace helpers no longer emit legacy `X-Tenant-Id`.
- Backend tenant resolution and idempotent scoping now consume `X-Active-Tenant-Id` as the only active-tenant transport header.

## Next Candidates

These areas should be evaluated next because they still expose ambiguous `tenantId` API semantics to callers:

No remaining cross-module candidates are tracked here. New modules should apply the rollout rule directly instead of introducing fresh `tenantId` overloading.
Remaining `tenantId` names should be treated as persistence/storage debt or legacy surface area, not as acceptable naming for new current-context contracts.

## Rollout Rule

When a module is migrated:

1. Replace page/context semantics with `activeTenantId`.
2. Rename ownership/filter fields to `recordTenantId` where they describe records.
3. Rename create-result fields to `createdTenantId` where applicable.
4. Remove legacy test cases that keep old tenant-context naming alive.
