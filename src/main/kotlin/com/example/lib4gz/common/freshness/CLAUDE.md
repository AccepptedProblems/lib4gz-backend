# freshness

Owns: keeping one token per (course, resource type) current, so the web app can ask one
cheap question before reusing what it has cached. The read endpoint is in
`courses/service/FreshnessService.kt`.

## Rules

- Tokens are bumped by `FreshnessEntityListener` from Hibernate entity events. No service
  ever bumps a token by hand.
- A new entity whose data appears in a cached payload must be added to `toPendingBump`,
  with every resource type whose payload embeds it. If its course is reached through a parent
  type the listener does not know yet, it also needs a branch in `idOf` and a resolution
  query. A new resource type also needs the
  `course_resource_freshness_resource_type_check` constraint changed by a migration.
- A bump failure is logged and swallowed. It must never fail the write it belongs to.
  The bump runs under a savepoint for that: without one, a failed statement aborts the
  Postgres transaction and the commit rolls the write back with no error.
- Bumps are resolved with plain SQL on the transaction's own connection, on purpose: it
  must see soft-deleted rows, so that a deletion also invalidates the cache.
- DRAFT submissions and their answers bump nothing.
- A course, module, lesson or exercise delete hides its children with bulk updates, which
  fire no event. The parent's own delete event carries the bumps for its descendants:
  `toPendingBump(entity, deleted = true)`. A new child level needs its types added there.
- Clients compare tokens by equality only. A token of 0 means "never changed".

## Gotchas

- The listener is registered by `FreshnessListenerRegistrar`; Hibernate does not find
  listeners in the Spring context by itself.
- It hooks Hibernate's own before-commit phase, not a Spring transaction synchronization.
  The class comment explains why the Spring one never fires here.
- A write that bypasses Hibernate entities (a native or bulk JPQL update) fires no event and
  bumps nothing.

## Verify

`./gradlew test --tests "com.example.lib4gz.common.freshness.*"`
