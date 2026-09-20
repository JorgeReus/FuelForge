# Task 2 Report: SQLDelight Check-in Storage and Mutation Queue

## Status

Implemented the review fixes in commit `00d889b`.

## Changed files

- `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/CheckIn.sq`
  - Added `local_check_in` keyed by recorded day.
  - Added `pending_mutation` with operation, payload, timestamps, and attempt count.
  - Added direct SQLDelight queries for reading, upserting, queueing, deleting, and retry counting.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt`
  - Added `PendingMutation`.
  - Added `saveLocalCheckIn`, `loadLocalCheckIn`, and `pendingLocalMutations`.
  - Check-in persistence and mutation enqueueing run in the same SQLDelight transaction.
  - Same-day saves retain the existing local check-in ID, delete superseded
    pending mutations, and enqueue only the latest payload.
  - Existing Todo helpers were left unchanged.
- `composeApp/src/androidUnitTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt`
  - Added a fresh in-memory JDBC SQLDelight database per test.
  - Covers queue metadata, JSON round-trip, and repeated same-day saves.
- `composeApp/build.gradle.kts`
  - Added the SQLDelight JDBC driver only to Android unit tests.
- Removed the production-global-database common test in favor of the isolated
  Android unit-test fixture.

## Tests and verification

- `git diff --check`: passed.
- `git diff --check`: passed.
- `gradle :composeApp:testDebugUnitTest --tests com.reus.nutri.CheckInRepositoryTest --no-daemon`: blocked before project evaluation.

Both Gradle commands fail with:

```text
Gradle could not start your build.
> Could not initialize native services.
   > Failed to load native library 'libnative-platform.dylib' for Mac OS X aarch64.
```

Therefore SQLDelight code generation and the Android unit-test runner could not be executed in this environment.

## Concerns

- The generated `NutriDatabase` query API and Android unit-test source-set wiring could not be compiler-verified because Gradle cannot start. The fixture uses SQLDelight's in-memory JDBC driver and creates a new database for each test.
- Same-day saves deliberately preserve the first persisted ID so an already queued or remotely known row remains idempotent while its payload is replaced.
- Supabase sync, authentication, UI, and health integrations were intentionally not changed; they belong to later tasks.
