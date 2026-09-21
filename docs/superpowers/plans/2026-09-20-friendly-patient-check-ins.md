# Friendly Patient Check-ins Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Todo prototype with a friendly, offline-first daily patient check-in and optional Android/iOS health-data suggestions.

**Architecture:** Keep numeric recovery scores in Supabase and SQLDelight, but expose four human labels per metric in Compose. A shared repository writes a local check-in and pending mutation atomically, then syncs an upsert to `patient_check_ins`. An `expect`/`actual` health provider makes Health Connect and HealthKit optional platform sources without leaking platform APIs into `commonMain`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight 2.1.0, supabase-kt/PostgREST, Android Health Connect, Apple HealthKit.

**Spec:** `docs/superpowers/specs/2026-09-20-friendly-patient-check-ins-design.md`

## Global Constraints

- Apply existing Supabase migrations `008`, `009`, and `010` with `supabase db push` before testing against the linked project.
- Store weight as integer grams and sleep as integer minutes; never use floating point for either.
- Store normalized scores, but never render a numeric 0–10 control to patients.
- Require a Supabase Auth session for remote operations; do not hardcode a patient ID or use anonymous writes.
- Health data is optional, read-only, and must be editable before saving.
- Do not add a service-role key, background reads, historical reads, or heart-rate permissions.
- Keep manual check-ins usable when health permissions are denied or no device data exists.

---

## File structure

- `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/CheckIn.sq` — local check-in and mutation-queue tables/queries.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInModels.kt` — domain models and friendly label-to-score mappings.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInRepository.kt` — local-first load/save/sync behavior.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/HealthDataProvider.kt` — shared health snapshot contract.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/Identifier.kt` — KMP UUID generator for offline IDs.
- `composeApp/src/androidMain/kotlin/com/reus/nutri/Identifier.android.kt` — Android identifier actual.
- `composeApp/src/iosMain/kotlin/com/reus/nutri/Identifier.ios.kt` — iOS identifier actual.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt` — serializable PostgREST DTOs for check-ins.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt` — database construction and narrow check-in helpers.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt` — Today-card entry point and check-in sheet.
- `composeApp/src/androidMain/kotlin/com/reus/nutri/HealthDataProvider.android.kt` — Health Connect read/permission implementation.
- `composeApp/src/iosMain/kotlin/com/reus/nutri/HealthDataProvider.ios.kt` — HealthKit read/permission implementation.
- `composeApp/src/androidMain/AndroidManifest.xml` — Health Connect read permissions.
- `composeApp/build.gradle.kts` — Health Connect dependency and test dependencies.
- `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInModelsTest.kt` — mapping tests.
- `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt` — local-first queue tests.

## Task 1: Define check-in domain models and friendly score mappings

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInModels.kt`
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/Identifier.kt`
- Create: `composeApp/src/androidMain/kotlin/com/reus/nutri/Identifier.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/reus/nutri/Identifier.ios.kt`
- Create: `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInModelsTest.kt`

**Interfaces:**
- Produces `CheckInMetric`, `CheckInChoice`, `DailyCheckIn`, and `HealthDailySnapshot` for repository and UI tasks.

- [ ] **Step 1: Write the failing mapping test**

```kotlin
@Test
fun sorenessOptionsPersistFriendlyScores() {
    assertEquals(0, CheckInMetric.Soreness.choices[0].score)
    assertEquals("Very sore", CheckInMetric.Soreness.choices.last().label)
    assertEquals(9, CheckInMetric.Soreness.choices.last().score)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInModelsTest`

Expected: FAIL because `CheckInMetric` does not exist.

- [ ] **Step 3: Add the minimal shared models**

```kotlin
@Serializable
data class CheckInChoice(val label: String, val score: Int)

enum class CheckInMetric(val title: String, val choices: List<CheckInChoice>) {
    Soreness("Soreness", listOf(
        CheckInChoice("None", 0), CheckInChoice("Light", 3),
        CheckInChoice("Moderate", 6), CheckInChoice("Very sore", 9)
    )),
    Performance("Performance", listOf(
        CheckInChoice("Struggled", 2), CheckInChoice("Okay", 5),
        CheckInChoice("Strong", 8), CheckInChoice("Best yet", 10)
    )),
    Motivation("Motivation", listOf(
        CheckInChoice("Low", 2), CheckInChoice("Neutral", 5),
        CheckInChoice("Ready", 8), CheckInChoice("Locked in", 10)
    )),
    Hunger("Hunger", listOf(
        CheckInChoice("Not hungry", 2), CheckInChoice("Normal", 5),
        CheckInChoice("Hungry", 8), CheckInChoice("Ravenous", 10)
    )),
    Fatigue("Fatigue", listOf(
        CheckInChoice("Fresh", 1), CheckInChoice("A little tired", 4),
        CheckInChoice("Tired", 7), CheckInChoice("Drained", 10)
    )),
    Stress("Stress", listOf(
        CheckInChoice("Calm", 1), CheckInChoice("Manageable", 4),
        CheckInChoice("High", 7), CheckInChoice("Overwhelmed", 10)
    )),
    SleepQuality("Sleep quality", listOf(
        CheckInChoice("Poor", 2), CheckInChoice("Fair", 5),
        CheckInChoice("Good", 8), CheckInChoice("Great", 10)
    )),
}

@Serializable
data class DailyCheckIn(
    val id: String,
    val recordedOn: String,
    val weightGrams: Int? = null,
    val sleepMinutes: Int? = null,
    val neatMinutes: Int? = null,
    val soreness: Int? = null,
    val performance: Int? = null,
    val motivation: Int? = null,
    val hunger: Int? = null,
    val fatigue: Int? = null,
    val stress: Int? = null,
    val sleepQuality: Int? = null,
    val comments: String = "",
)

data class HealthDailySnapshot(
    val weightGrams: Int? = null,
    val sleepMinutes: Int? = null,
    val steps: Long? = null,
    val activeMinutes: Int? = null,
    val sourceLabel: String? = null,
)
```

Create `Identifier.kt` with `expect fun newIdentifier(): String`; provide
platform `actual` implementations using `java.util.UUID.randomUUID()` on
Android and `NSUUID().UUIDString()` on iOS. Use it for both the check-in and
queued mutation IDs.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInModelsTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInModels.kt composeApp/src/commonMain/kotlin/com/reus/nutri/Identifier.kt composeApp/src/androidMain/kotlin/com/reus/nutri/Identifier.android.kt composeApp/src/iosMain/kotlin/com/reus/nutri/Identifier.ios.kt composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInModelsTest.kt
git commit -m "feat: add friendly check-in score mappings"
```

## Task 2: Add SQLDelight check-in storage and mutation queue

**Files:**
- Create: `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/CheckIn.sq`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt`
- Create: `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt`

**Interfaces:**
- Consumes `DailyCheckIn` from Task 1.
- Produces `saveLocalCheckIn(checkIn: DailyCheckIn, nowEpochMs: Long)`,
  `loadLocalCheckIn(day: String): DailyCheckIn?`, and
  `pendingLocalMutations(): List<PendingMutation>`.

- [ ] **Step 1: Write the failing queue test**

```kotlin
private val checkIn = DailyCheckIn(id = "check-in-1", recordedOn = "2026-09-20")

@Test
fun savingCheckInQueuesAnUpsert() {
    saveLocalCheckIn(checkIn, nowEpochMs = 1L)
    assertEquals("check-in-1", pendingLocalMutations().single().entityId)
    assertEquals("upsert", pendingLocalMutations().single().operation)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInRepositoryTest`

Expected: FAIL because check-in SQLDelight queries and repository do not exist.

- [ ] **Step 3: Create `CheckIn.sq`**

```sql
CREATE TABLE local_check_in (
  id TEXT NOT NULL PRIMARY KEY,
  recorded_on TEXT NOT NULL UNIQUE,
  payload_json TEXT NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);

CREATE TABLE pending_mutation (
  id TEXT NOT NULL PRIMARY KEY,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  operation TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at_epoch_ms INTEGER NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0
);

checkInForDay:
SELECT * FROM local_check_in WHERE recorded_on = ?;

upsertCheckIn:
INSERT OR REPLACE INTO local_check_in(id, recorded_on, payload_json, updated_at_epoch_ms)
VALUES (?, ?, ?, ?);

enqueueMutation:
INSERT OR REPLACE INTO pending_mutation(id, entity_type, entity_id, operation, payload_json, created_at_epoch_ms, attempt_count)
VALUES (?, ?, ?, ?, ?, ?, 0);

pendingMutations:
SELECT * FROM pending_mutation ORDER BY created_at_epoch_ms ASC;

deleteMutation:
DELETE FROM pending_mutation WHERE id = ?;

incrementMutationAttempts:
UPDATE pending_mutation SET attempt_count = attempt_count + 1 WHERE id = ?;
```

- [ ] **Step 4: Add narrow local helpers**

In `LocalDatabase.kt`, add `PendingMutation`, `saveLocalCheckIn`,
`loadLocalCheckIn`, and `pendingLocalMutations`. Serialize `DailyCheckIn`
with kotlinx.serialization and run `upsertCheckIn` plus `enqueueMutation` in
one `localDatabase.transaction` block. Do not create a generic database
abstraction.

- [ ] **Step 5: Run the test to verify it passes**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInRepositoryTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/sqldelight/com/reus/nutri/db/CheckIn.sq composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt
git commit -m "feat: persist check-ins locally"
```

## Task 3: Replace Todo Supabase calls with check-in sync

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt`
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt`
- Delete: `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/Todo.sq`

**Interfaces:**
- Consumes local query helpers and `DailyCheckIn`.
- Produces `CheckInRepository.local(day: String): DailyCheckIn?`,
  `suspend fun refresh(day: String): DailyCheckIn?`,
  `suspend fun save(checkIn: DailyCheckIn): SyncResult`, and
  `suspend fun syncPending(): SyncResult`.

- [ ] **Step 1: Write the failing remote-sync test**

```kotlin
private val checkIn = DailyCheckIn(id = "check-in-1", recordedOn = "2026-09-20")

@Test
fun successfulSyncRemovesOnlyTheSentMutation() = runBlocking {
    val repository = CheckInRepository(
        remoteFetch = { null },
        remoteUpsert = { SyncResult.Synced },
        nowEpochMs = { 1L },
    )
    repository.save(checkIn)
    repository.syncPending()
    assertTrue(local.pendingMutations().isEmpty())
    assertEquals(checkIn, local.loadCheckIn(checkIn.recordedOn))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInRepositoryTest`

Expected: FAIL because `syncPending` does not exist.

- [ ] **Step 3: Define the PostgREST DTO and calls**

Replace `TodoItem` and `loadTodos` in `Supabase.kt` with a `@Serializable`
`PatientCheckInDto`. Use snake_case fields matching `patient_check_ins`.

Add `implementation("io.github.jan-tennert.supabase:auth-kt")` to
`commonMain` and install `Auth` alongside `Postgrest` in the shared client.
Remote helpers call `supabase.auth.currentUserOrNull()?.id`; if it is null,
return `SyncResult.SignedOut` without changing the local queue. The DTO must
include `id`, `user_id`, `recorded_on`, every nullable check-in field, and
`comments` so its serialized payload is an exact upsert request.

```kotlin
suspend fun fetchCheckIn(day: String): PatientCheckInDto?
suspend fun upsertCheckIn(dto: PatientCheckInDto)
```

Fetch with `select { filter { eq("recorded_on", day) } }` and map the first
row. Upsert with `onConflict = "user_id,recorded_on"`. The authenticated user
ID must come from the Supabase auth session; do not hardcode Alex or a UUID.

- [ ] **Step 4: Implement the local-first repository**

Define `sealed interface SyncResult { data object Synced; data object Pending;
data object SignedOut }`. `local` returns the local row without network I/O.
`refresh` fetches the remote row and replaces the local row only when one
exists. `save` persists and queues the JSON payload in one local transaction,
then calls `syncPending`. `syncPending` iterates oldest-first, deletes a
mutation only after a successful upsert, and increments attempts on exceptions
without throwing away the row. The constructor receives `remoteFetch`,
`remoteUpsert`, and `nowEpochMs` lambdas so its unit test does not call the
network; production passes the Supabase helpers and `System.currentTimeMillis`.

```kotlin
class CheckInRepository(
    private val remoteFetch: suspend (String) -> DailyCheckIn?,
    private val remoteUpsert: suspend (DailyCheckIn) -> SyncResult,
    private val nowEpochMs: () -> Long,
)
```

Delete `Todo.sq` and the Todo UI after `rg -n "TodoItem|loadTodos|TodoCard" composeApp/src` returns no matches.

- [ ] **Step 5: Run the tests**

Run: `gradle :composeApp:allTests`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInRepository.kt composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt composeApp/src/commonMain/sqldelight composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt
git commit -m "feat: sync patient check-ins offline first"
```

## Task 4: Build the friendly patient check-in sheet

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt`

**Interfaces:**
- Consumes `DailyCheckIn`, `CheckInMetric`, and `CheckInRepository`.
- Produces an accessible sheet with friendly choices and save state.

- [ ] **Step 1: Write the failing form-state test**

```kotlin
@Test
fun selectingVerySoreProducesScoreNine() {
    val form = CheckInFormState(DailyCheckIn(id = "id", recordedOn = "2026-09-20"))
    form.select(CheckInMetric.Soreness, CheckInMetric.Soreness.choices.last())
    assertEquals(9, form.toCheckIn().soreness)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInSheetTest`

Expected: FAIL because `CheckInFormState` does not exist.

- [ ] **Step 3: Implement the sheet**

Add `CheckInFormState(initial: DailyCheckIn)` to `CheckInSheet.kt`. It holds
the editable values, provides `select(metric, choice)`, `clear(metric)`, and
`toCheckIn()`, and converts weight kg to rounded grams plus sleep hours/minutes
to total minutes. Use a Material 3 modal bottom sheet. For every `CheckInMetric`, render the
title and four `FilterChip`s using each `CheckInChoice.label`; selected chips
write only their mapped integer score. Add editable text fields for weight in
kg and sleep in hours/minutes, converting to grams/minutes on save. Add a
single optional notes field.

Show import rows as `Imported from ${snapshot.sourceLabel}` plus an Edit action. If an import
is absent, show `No data yet` without an error state. Save button text is
`Save check-in`; while syncing, keep the saved local state visible and show a
small `Saved locally` or `Sync pending` status.

- [ ] **Step 4: Put the entry point on Today**

Replace `TodoCard` in `Dashboard` with a `Daily check-in` card. It shows a
one-line completion summary when a local check-in exists, otherwise `Take
today's check-in`. It opens `CheckInSheet` for a signed-in patient. When
`supabase.auth.currentUserOrNull()` is null, render `Sign in required` and do
not open a form or create an orphaned local mutation.

- [ ] **Step 5: Run UI and full tests**

Run: `gradle :composeApp:allTests`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInSheet.kt composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt composeApp/src/commonTest
git commit -m "feat: add friendly daily check-in sheet"
```

## Task 5: Add the shared health provider contract and no-data fallbacks

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/HealthDataProvider.kt`
- Create: `composeApp/src/androidMain/kotlin/com/reus/nutri/HealthDataProvider.android.kt`
- Create: `composeApp/src/iosMain/kotlin/com/reus/nutri/HealthDataProvider.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInSheet.kt`
- Test: `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInRepositoryTest.kt`

**Interfaces:**
- Produces `expect object HealthDataProvider` with `suspend fun readDailySnapshot(day: String): HealthDailySnapshot`.
- Produces `@Composable expect fun HealthImportAction(day: String, onSnapshot: (HealthDailySnapshot) -> Unit)` for the platform-owned permission prompt and import button.

- [ ] **Step 1: Write the failing import-merge test**

```kotlin
private val manualCheckIn = DailyCheckIn(id = "id", recordedOn = "2026-09-20")

@Test
fun healthSnapshotPrefillsOnlyMissingValues() = runBlocking {
    val merged = repository.prefillFromHealth(manualCheckIn, HealthDailySnapshot(weightGrams = 72400))
    assertEquals(72400, merged.weightGrams)
    assertEquals(manualCheckIn.soreness, merged.soreness)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInRepositoryTest`

Expected: FAIL because `HealthDataProvider` and `prefillFromHealth` do not exist.

- [ ] **Step 3: Implement the shared contract and merge rule**

```kotlin
expect object HealthDataProvider {
    suspend fun readDailySnapshot(day: String): HealthDailySnapshot
}

@Composable
expect fun HealthImportAction(
    day: String,
    onSnapshot: (HealthDailySnapshot) -> Unit,
)
```

`prefillFromHealth` fills `weightGrams`, `sleepMinutes`, and `neatMinutes`
only when the check-in field is null. It never changes subjective scores or
comments.

The initial Android and iOS `HealthDataProvider` actuals must return an empty
snapshot when the platform store is unavailable or access is not granted.
The initial `HealthImportAction` actuals render a `Connect health data` action
that returns that empty snapshot. This keeps the manual check-in path testable
before platform integrations land.

- [ ] **Step 4: Run the test to verify it passes**

Run: `gradle :composeApp:allTests --tests com.reus.nutri.CheckInRepositoryTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/reus/nutri/HealthDataProvider.kt composeApp/src/androidMain/kotlin/com/reus/nutri/HealthDataProvider.android.kt composeApp/src/iosMain/kotlin/com/reus/nutri/HealthDataProvider.ios.kt composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInRepository.kt composeApp/src/commonTest
git commit -m "feat: add shared health data boundary"
```

## Task 6: Implement Android Health Connect reads

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Modify: `composeApp/src/androidMain/kotlin/com/reus/nutri/HealthDataProvider.android.kt`
- Test: Android instrumented/manual permission test on a Health Connect capable device.

**Interfaces:**
- Implements the shared provider from Task 5.
- Produces optional weight, sleep duration, steps, and active minutes from Health Connect.

- [ ] **Step 1: Add the dependency and manifest declarations**

Add Health Connect client `androidx.health.connect:connect-client:1.1.0-alpha12` to `androidMain`. Declare only:

```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_ACTIVITY_INTENSITY" />
```

Do not declare background/history, write, heart-rate, or calorie permissions.

- [ ] **Step 2: Implement availability and the import action**

Use `HealthConnectClient.getSdkStatus(context)` before creating a client.
Make the Android `actual HealthImportAction` register the Health Connect
permission activity-result contract from the current `ComponentActivity`.
It requests exactly the four record-type permissions. On grant, call
`HealthDataProvider.readDailySnapshot(day)` and invoke `onSnapshot`; on an
unavailable SDK or denial, invoke `onSnapshot(HealthDailySnapshot())`.

- [ ] **Step 3: Implement daily aggregation**

For the requested local calendar day, read:

- Latest `WeightRecord`, convert kilograms to rounded grams.
- `SleepSessionRecord` duration, convert duration to minutes.
- Aggregated `StepsRecord.COUNT_TOTAL`.
- Aggregated activity-intensity minutes; use this as `activeMinutes`.

Deduplicate via Health Connect aggregation/read APIs; do not sum individual
records manually. Return nullable values for unavailable types and use source
label `Health Connect` when at least one value exists.

- [ ] **Step 4: Verify on Android**

Run:

```bash
gradle :composeApp:assembleDebug
gradle :composeApp:installDebug
```

On-device: deny permissions and confirm manual save works; grant one or more
permissions and confirm only available readings prefill the sheet.

- [ ] **Step 5: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/androidMain/AndroidManifest.xml composeApp/src/androidMain/kotlin/com/reus/nutri/HealthDataProvider.android.kt
git commit -m "feat: import Android Health Connect check-in data"
```

## Task 7: Implement iOS HealthKit reads

**Files:**
- Create: `docs/ios-healthkit-host-setup.md`
- Modify: `composeApp/src/iosMain/kotlin/com/reus/nutri/HealthDataProvider.ios.kt`
- Test: iPhone or HealthKit-enabled simulator manual test.

**Interfaces:**
- Implements `HealthDataProvider` and `HealthImportAction` from Task 5 using HealthKit.

- [ ] **Step 1: Document the host setup required before an iOS host exists**

The repository has no Xcode host project. Create `docs/ios-healthkit-host-setup.md`
with these exact host requirements: enable the HealthKit capability; add
`NSHealthShareUsageDescription` with “Nutri reads your weight, sleep, and
activity to prefill an optional daily check-in.”; request no write
authorization. When an iOS host is later added, it must apply these three
items before enabling `HealthImportAction` in the release build.

- [ ] **Step 2: Request the minimum read set from the import action**

Make the iOS `actual HealthImportAction` request read authorization for body
mass, sleep analysis, step count, and workout data used for active minutes.
On completion, call `HealthDataProvider.readDailySnapshot(day)` and invoke
`onSnapshot`. Treat HealthKit authorization status as non-diagnostic for read
access; an empty query result is a normal empty snapshot.

- [ ] **Step 3: Implement the same daily snapshot**

Use `HKSampleQuery`/statistics queries bounded to the local calendar day:

- Most recent body-mass sample → rounded grams.
- Sleep-analysis samples → total asleep minutes.
- Step-count statistics → steps.
- Workout/activity samples → active minutes when available.

Return `HealthDailySnapshot` with source label `Apple Health` and nullable
fields. Do not upload raw HealthKit samples.

- [ ] **Step 4: Verify manually**

Confirm the manual sheet works with no Health permission, then confirm
available Apple Health values prefill but remain editable.

- [ ] **Step 5: Commit**

```bash
git add docs/ios-healthkit-host-setup.md composeApp/src/iosMain/kotlin/com/reus/nutri/HealthDataProvider.ios.kt
git commit -m "feat: import Apple Health check-in data"
```

## Plan review

- Spec coverage: Tasks 1–4 deliver the friendly, local-first patient flow;
  Tasks 5–7 add optional platform data without making health access required.
- No task requests data outside weight, sleep, steps, and activity.
- Numeric scoring remains a storage/analytics concern and is not exposed in
  patient UI.
