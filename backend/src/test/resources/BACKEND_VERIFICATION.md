# Backend runner verification handoff

Status: implementation edited; compilation, RED/GREEN, Maven, Docker, network and PostgreSQL execution are **awaiting runner verification**. No execution result is claimed. Existing `contracts/openapi.yaml` fixture is unchanged.

## Test targets

- Surefire: `*ContractTest` (Login, Users, Organizations, Roles, UserRoles, MenuPermissions, MenuStructure, MenuInformation, CodeGroups, DetailCodes, Accounts, Health, Security, ChangeTrace). The shared MockMvc support derives operation IDs/methods/routes/response required keys from `ClassPathResource("contracts/openapi.yaml")`; all 33 operations have focused cases. Controllers, feature services and authorization policy are real; only persistence/auth/personnel ports are mocked.
- Failsafe: `SchemaIT`, `LoginIT`, `UsersIT`, `OrganizationsIT`, `RolesIT`, `UserRolesIT`, `MenuPermissionsIT`, `MenuStructureIT`, `MenuInformationIT`, `CodeGroupsIT`, `DetailCodesIT`, `AccountsIT`. They use actual random-port Boot HTTP requests, production MyBatis XML and actual PostgreSQL `postgres:16.8`; no mocked facade, H2 or replacement DDL.
- PostgreSQL singleton is started only when runner invokes ITs; clean+migrate restores packaged production/local migrations before each case. Test-only `clean-disabled=false` never changes production settings. Do not enable parallel execution against this shared fixture.
- ITs cover byte-aligned schema/V1, 11 tables, nullable status fields, generated PKs, FK NO ACTION, BCrypt seed login, session persistence/logout, all nine initial screen reads, role/menu checks, database readback of supported mutations, JSONB/date materialization, surrogate-key reference preservation and rollback after a later FK failure in role/permission batches.
- Runner targets: `mvn -f backend/pom.xml test`; `mvn -f backend/pom.xml verify` with Docker available. These commands were NOT run by this implementation agent.

## Authorization boundary

All admin operation `x-roles` currently name R09 and are represented at their exact controller boundary, AND require the DB screen menu decision. There is no R09 menu bypass. CurrentUser.allowedMenus contains only permitted screen leaf rows and their ancestors; including an ancestor does not grant the menu-structure operation. Login is public, me/logout require only a valid session, health is public and queries actual migration/database state.

A single explicit permission subject/decision per menu in the whole ancestry is evaluable. Missing or explicit single false decisions do not permit access. Multiple matching rows, combined targets, unspecified decisions, unknown status effects, duplicate screen mappings and malformed hierarchy are fail-closed approval-pending; no allow union, specificity precedence, or deny-overrides composition is invented. Duplicate business group IDs or stored permission target rows are never resolved by arbitrary first-row selection.

## Unresolved scopes — NOT complete

- **OQ-001**: Both organization mutation routes are registered, validate exact input fields, and return 400 with pending reason before any organization/history write. No overlay or history table is invented. Relationship write/history acceptance remains blocked.
- **OQ-003/004**: Permission composition and functional/data-scope effects remain pending. Raw supported menu permission storage is implemented for one explicit target; conflicting effective access is 403 pending, not an invented precedence rule. Stored role default_data_scope is information only.
- **OQ-005/OQ-DATA-004**: Grant/change persist explicit approver, role, dates and source; actor is never substituted for approver. No automatic position grants or date-expiry boundaries are applied. Configured temporal/status-dependent authorization effects are 403 pending. Revoke is registered and validates all revoke fields, but returns 400 pending without changing the assignment. A full user-role replacement that would drop an existing mapping is likewise pending before writes; unambiguous retained-role updates/additions are supported. Unknown state strings are stored literally when the input permits them, never synthesized as ACTIVE/Y/REVOKED.
- **OQ-006**: Unlinked internal account creation, BCrypt hashing and supported non-password updates work. Explicit personnel connection changes return 400 pending; password reset/source mutation fields are rejected. Account/status-dependent authentication effect is pending when a non-null unapproved status is present. Session lifetime is not invented. Logout deletes the ephemeral DB credential, leaving accounts and role history intact.
- **OQ-002**: ChangeTrace carries explicit-field before/after values, authenticated actor, processing time and nullable reason through an in-memory application event. Secrets are omitted. There is no invented operational audit persistence, retention, audit search or export; durable tracing remains pending.
- **OQ-DATA-003**: Local business group/code renames preserve numeric surrogate IDs and local FKs. External business-key reference repair is not implemented or claimed.
- **OQ-UI-001**: Missing page/size returns the complete matching collection with informational meta.page=0, meta.size=max(1, returned-count), meta.total=count; no default limit or upper bound is imposed. Explicit page/size describes a zero-based page and exact total. One supplied without the other is rejected. Sorting is deterministic by PK, with submitted display order for siblings on the structure read; this is technical ordering, not an approved business ranking policy.

## Ownership

Edits are confined to backend/pom.xml, backend Java, application YAML, mapper XML and backend test trees. Existing db/**, schema.sql, contract fixture, Dockerfile/.dockerignore, frontend and infra are not modified by this agent. No commits.
