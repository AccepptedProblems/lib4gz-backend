# notification

Owns: telling people things, and the wording. Email is the only channel today.

## Rules

- Code outside this folder uses `NotifierService` only. A caller names the business event;
  the subject, body and channel are decided here.
- `email/EmailSender.kt` is transport only: recipient, subject, body.
- One provider class per vendor under `email/<vendor>/`, each opted in by name through
  `application.mail.provider`. Exactly one `EmailSender` is in the context. Switching
  provider is an environment variable and a redeploy, never a code edit and never a
  `@Qualifier`.
- `LoggingEmailSender` is the fallback for a blank or missing `MAIL_PROVIDER`. It is the
  only provider not guarded by `@ConditionalOnProperty`; its class comment says why.
- A sending provider checks its settings in its constructor through
  `MailProperties.requireConfigured`, so a missing variable fails the boot with one line
  instead of a 503 on someone's first sign-up.
- A provider that cannot deliver throws `ResponseStatusException(503)`. Callers that send
  inside a transaction rely on that to roll back.
- Everything here is blocking on purpose.

## Adding things

- A new message: a method on `NotifierService`.
- A new vendor: a class in `email/<vendor>/` and its name in the `MailProvider` enum.
- A new channel: a sibling folder of `email/`.

## Verify

`./gradlew test --tests "com.example.lib4gz.common.notification.*"`
