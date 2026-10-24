# common

Owns: what every feature module uses. `notification/`, `freshness/` and `config/security/`
have their own `CLAUDE.md`.

## Rules

- Services throw the exceptions in `exception/`; they never build an error response.
  `config/error/GlobalExceptionHandler.kt` maps them:

  | Exception | Status |
  | --- | --- |
  | `ResourceNotFoundException` | 404 |
  | `UnauthorizedException` | 403, not 401 |
  | `ValidationException`, `BusinessException`, `BadRequestException` | 400 |
  | `ResponseStatusException` | its own (used for 409 conflicts, 401 bad credentials, 503 mail down) |
  | `DataIntegrityViolationException` from a duplicate key, a missing parent or an exclusion | 409 with one fixed sentence |
  | anything else | 500 with one fixed sentence |

- The message of those exceptions is sent to the browser and shown to the person. Word it
  for them.
- An unhandled exception never reaches the wire. It is logged under the request id.
- The frontend reads the text from `errors[0].reason`, or `errors[0].message` for an
  input-format error. Keep that shape.
- A context test (`@SpringBootTest`) must mock `RegistrationCleanupJob` with `@MockitoBean`.
  The real job sweeps at startup and would delete expired rows in whatever database `.env`
  names.
- A new entity id is `IdGenerator.generate("<prefix>")` as the default of the `id` field,
  with one prefix per entity. Freshness resolution relies on ids being unique across
  entity types.
- `ExpandTokens` ignores unknown tokens and case. Add a token rather than an endpoint.
- Controllers return `Mono` or `Flux` on a servlet stack. Blocking JPA work goes in
  `Mono.fromCallable { }.subscribeOn(Schedulers.boundedElastic())`.
- `@Transactional` on a method that returns that `Mono` does not cover the work inside it.
  When several writes must commit together, wrap them in `transactionTemplate.execute`
  inside the callable, as `auth/service/RegistrationService.kt` does.

## Verify

`./gradlew test --tests "com.example.lib4gz.common.*"`. `config/OpenApiDocumentTest` fails
when a controller endpoint is missing from the generated API document.
