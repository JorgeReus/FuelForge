# Task 3 Report: Replace Todo Supabase Calls with Check-in Sync

## Status

Implemented the local-first patient check-in repository, addressed the Task 3
review findings, and removed the Todo prototype path.

## Changes

- Added the Supabase Auth plugin and installed `Auth` beside `Postgrest`.
- Replaced `TodoItem`/`loadTodos` with `PatientCheckInDto`, authenticated
  fetch, authenticated upsert, and domain/DTO mappings.
- Added `CheckInRepository` with the required `local`, `refresh`, `save`, and
  `syncPending` operations.
- Local saves persist the check-in and enqueue its JSON mutation in one
  SQLDelight transaction before attempting synchronization.
- Pending mutations are processed oldest-first, removed only after successful
  upsert, and retain/increment attempt state after failures.
- Signed-out sessions return `SyncResult.SignedOut` without deleting queued
  local data. No user ID is hardcoded.
- Every repository-created queue payload records its authenticated owner ID;
  ownership mismatches are retained and never uploaded.
- Local check-in rows store plain `DailyCheckIn` JSON while queue rows store
  the owner-enveloped payload, so authenticated saves remain locally readable.
- Remote fetch distinguishes found, not-found, signed-out, and offline states.
- Repository refresh exposes those states through the public `RefreshResult`
  type while retaining any local check-in.
- Remote refresh preserves local data when a pending same-day mutation exists.
- Same-day remote refresh preserves the existing local check-in ID and applies
  the replacement transactionally.
- Removed Todo local helpers, Todo UI, and `Todo.sq` after confirming there are
  no remaining source references.
- Added focused Android unit tests for signed-out queue retention, account
  ownership mismatch, failed-upload attempt increments, pending-local refresh
  preservation, same-day ID reconciliation, authenticated owner/check-in
  upload arguments, local readability after save, and successful sync deletion.

## Verification

- `git diff --check` passed.
- `rg -n "TodoItem|loadTodos|TodoCard|loadLocalTodos|saveLocalTodos|Todo" composeApp/src` returned no matches.
- Source review confirmed only the scoped Task 3 files are staged for the fix.
- `gradle :composeApp:testDebugUnitTest --tests com.reus.nutri.CheckInRepositoryTest` could not start because the environment cannot load Gradle's macOS ARM native library:
  `libnative-platform.dylib`.

## Concerns

- Gradle did not reach Kotlin compilation or test execution, so dependency/API
  compatibility still needs verification in a working Gradle environment.
- Task 4 must provide the signed-in UI state and authentication flow; this task
  intentionally does not add login UI.
