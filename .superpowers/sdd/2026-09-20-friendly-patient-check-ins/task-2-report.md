# Task 2 Report: SQLDelight Check-in Storage and Mutation Queue

## Status

Implemented and committed as `feat: persist check-ins locally`.

## Changed files

- `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/CheckIn.sq`
  - Added `local_check_in` keyed by recorded day.
  - Added `pending_mutation` with operation, payload, timestamps, and attempt count.
  - Added direct SQLDelight queries for reading, upserting, queueing, deleting, and retry counting.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt`
  - Added `PendingMutation`.
  - Added `saveLocalCheckIn`, `loadLocalCheckIn`, and `pendingLocalMutations`.
  - Check-in persistence and mutation enqueueing run in the same SQLDelight transaction.
  - Existing Todo helpers were left unchanged.
- `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt`
  - Added the required queue test for a saved check-in.

## Tests and verification

- `git diff --check`: passed.
- `gradle :composeApp:allTests --tests com.reus.nutri.CheckInRepositoryTest`: blocked before project evaluation.
- `gradle :composeApp:generateCommonMainNutriDatabaseInterface :composeApp:compileKotlinMetadata --no-daemon`: blocked before project evaluation.

Both Gradle commands fail with:

```text
Gradle could not start your build.
> Could not initialize native services.
   > Failed to load native library 'libnative-platform.dylib' for Mac OS X aarch64.
```

Therefore SQLDelight code generation and the test runner could not be executed in this environment.

## Concerns

- The generated `NutriDatabase` query API could not be compiler-verified because Gradle cannot start. The SQLDelight query names and generated property names follow the existing project conventions and the brief.
- The queue uses a deterministic `check-in:<id>` mutation ID, so saving the same check-in replaces its pending upsert instead of creating duplicate pending mutations.
- Supabase sync, authentication, UI, and health integrations were intentionally not changed; they belong to later tasks.
