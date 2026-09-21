# Task 5 Review

Verdict: NEEDS CHANGES

Finding count: 2

## Findings

### 1. [P1] Common test constructs the production Android database driver

`composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt:10-15`

The test does not pass a database, so constructing `CheckInRepository` evaluates
the default `database = localDatabase`. On Android, that initializes
`AndroidSqliteDriver(..., androidContext, ...)`, but `androidContext` is only
initialized by the application process. A common test running as an Android
unit test can therefore fail before exercising `prefillFromHealth` with
`UninitializedPropertyAccessException`. The test also unnecessarily requires a
real platform database for a pure merge test.

Pass a test database/driver explicitly, or change the merge helper so the test
does not initialize the repository's production database dependency.

### 2. [P2] The focused test does not cover the complete “only missing fields” rule

`composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt:16-20`

The test only verifies that weight is filled and that two subjective values are
unchanged. It does not verify that existing `sleepMinutes` and `neatMinutes`
remain unchanged, nor that missing sleep/movement fields are filled from the
snapshot. A regression in either of those three health-derived assignments in
`CheckInRepository.prefillFromHealth` would still pass. Add one fixture with
existing weight/sleep/neat values and one assertion set for missing
weight/sleep/neat values.

## Resolution

The health merge is now exposed as the pure common function
`prefillFromHealth(checkIn, snapshot)`. `CheckInRepository.prefillFromHealth`
delegates to it, preserving production behavior without requiring the
repository's default `localDatabase` in common tests.

Tests now verify that missing weight, sleep, and NEAT values are filled from
the health snapshot, while existing health-derived values and every subjective
field/comments remain unchanged.
