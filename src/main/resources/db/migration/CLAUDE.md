# migrations

Flyway owns the schema: `V<n>__<what>.sql`, applied on boot. JPA runs with
`ddl-auto=validate`, so Hibernate never changes the database and refuses to start when an
entity and its table differ.

## Rules

- Every entity change ships with a new migration: a column added, renamed, retyped or
  dropped.
- A value added to or renamed in an `@Enumerated(EnumType.STRING)` enum also needs one.
  Each enum column has a `<table>_<column>_check` constraint, and `validate` does not check
  it. Copy `V2__rename_otp_purpose_email_change_to_email_verification.sql`.
- Never edit a migration that has been deployed. Add the next version.
- Write a migration so it is safe on a database baselined at V1, which is only assumed to
  match it: guard each change with `IF EXISTS`, as V2 and V3 do.

## Gotchas

- `./gradlew bootRun` connects to the real Supabase database and applies any pending
  migration to it at startup.
- `./gradlew test` touches no database, so it does not check a migration.
- The check below proves the entities match the tables. It does not prove a changed check
  constraint: read that one by eye.
- Every unique constraint on Supabase carries a Hibernate-generated name (`uk...`), not the
  `<table>_<columns>_key` name in V1. Find one by its table and columns in `pg_constraint`,
  as V4 does. Primary key, foreign key and check constraint names match V1.

## Verify a new migration

Against a throwaway Postgres, never Supabase:

```
docker run -d --name pg -e POSTGRES_PASSWORD=pg -p 55432:5432 postgres:17
MIGRATION_CHECK=true MIGRATION_SCHEMA_CHECK=validate \
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/postgres \
  SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=pg ./gradlew test --rerun-tasks
```

Run it twice: once on an empty database, where V1 runs, and once on a database already
holding the deployed migrations, where only the new one runs.
