# Database migrations (Flyway)

This directory holds Flyway-managed SQL migrations. Flyway applies them in version order
on every application startup (see `quarkus.flyway.migrate-at-start=true` in `application.properties`).

## File naming

```
V<version>__<description>.sql
```

- `V` prefix marks a **versioned** migration (runs once, in order, tracked in `flyway_schema_history`).
- `<version>` is the migration version. Use a single integer (`V1`, `V2`, ...) or dotted form (`V1_1`).
- Double underscore `__` separates the version from the description.
- `<description>` is human-readable (e.g. `add_user_audit_columns`). Use underscores, not spaces.

Examples:
- `V1__initial.sql`
- `V2__add_appuser_deprovisioned_at.sql`
- `V3__create_audit_event_index.sql`

Other prefixes (rarely needed here):
- `R__<description>.sql` — **repeatable** migration, re-applied whenever its checksum changes (e.g. views, stored procs).
- `U<version>__<description>.sql` — undo migration (Flyway Teams only — we don't use these).

## Rules

1. **Never edit a migration after it's been applied to any environment.** Flyway checksums
   migrations and will fail startup if a file's contents change. To "edit", write a new
   migration that does the corrective change.

2. **One logical change per migration.** Keep them small and reviewable.

3. **Make migrations idempotent where reasonable** (`IF NOT EXISTS`, `IF EXISTS`).
   For DDL that can't be made idempotent (e.g. `ALTER TABLE ADD COLUMN`), at least make
   sure the migration is correct on a fresh DB.

4. **Forward-only.** We don't write `<rollback>` blocks. To "rollback" a bad migration,
   write a new migration that reverses it.

5. **Hibernate is in `validate` mode** in all profiles. If you add/remove/rename an entity
   field, you MUST add a migration in the same PR — otherwise the app fails to start.

## Regenerating V1 from current entities

If the entity model changes significantly before V1 is committed, regenerate V1:

```bash
# 1. Drop & recreate the local DB
psql -U postgres -c 'DROP DATABASE "docu-hyphen-postgres-db";'
psql -U postgres -c 'CREATE DATABASE "docu-hyphen-postgres-db";'

# 2. Run the bootstrap profile to capture the new V1
mvn quarkus:dev -Dquarkus.profile=local,bootstrap

# 3. Stop the app, drop & recreate the DB once more, then run normally
mvn quarkus:dev
```

After V1 is committed and applied to any real environment, this regeneration shortcut
no longer applies — write a V2, V3, etc. instead.
