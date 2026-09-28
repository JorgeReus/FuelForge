# Exercise and Workout Schema Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support the shared exercise catalog, trainer-managed patient plans, mutable daily workouts, and advanced set logging in Supabase and the KMP offline store.

**Architecture:** Keep `public.exercises` as the imported read-only catalog. Store a patient’s recurring weekly program separately from its generated daily workout instances, then snapshot planned exercises into independently editable instance rows. Enforce patient/trainer boundaries with RLS based on `coach_patients` and mirror active workout data in SQLDelight for offline use.

**Tech Stack:** Supabase PostgreSQL, Supabase Storage, Supabase CLI migrations, Kotlin Multiplatform, SQLDelight, supabase-kt/PostgREST.

**Spec:** `docs/superpowers/specs/2026-09-18-exercise-workout-schema-design.md`

## Global Constraints

- Use `supabase migration new <name>` for every remote schema change, then `supabase db push`.
- The app uses the publishable Supabase key only; import tooling alone may use `SUPABASE_SERVICE_ROLE_KEY` from the local environment.
- Do not create reusable cross-patient templates.
- Daily workout instances must be editable without changing their source plan day.
- Keep the exercise dataset attribution and media paths intact.
- Store custom exercise media in Storage, not in PostgreSQL byte columns.

---

## File structure

- `supabase/migrations/003_workout_plans.sql` — trainer/patient links, plans, recurring plan days, prescribed exercises, and RLS.
- `supabase/migrations/004_workout_instances.sql` — daily instance data, instance exercises, advanced set metrics, backfill, indexes, and RLS.
- `supabase/migrations/005_custom_exercise_media.sql` — custom exercise data, Storage bucket, Storage policies, and RLS.
- `scripts/import_exercises.py` — batch upsert of the upstream 1,324-record catalog.
- `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/Workout.sq` — local offline tables and queries for active plans/workouts/sets.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/WorkoutRepository.kt` — read local first, enqueue mutations, then sync remote changes.
- `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt` — serializable database models and PostgREST calls.

## Task 1: Add trainer-to-patient relationships and recurring plans

**Files:**

- Create: `supabase/migrations/003_workout_plans.sql`
- Test: Supabase local database assertions after `supabase db reset`

**Interfaces:**

- Produces `public.coach_patients`, `public.workout_plans`, `public.workout_plan_days`, and `public.workout_plan_exercises`.
- Consumes existing `public.profiles` and `public.exercises`.
- Later tasks use `workout_plan_days.id` to generate daily workout snapshots.

- [ ] **Step 1: Create the migration**

```bash
supabase migration new workout_plans
```

- [ ] **Step 2: Define the trainer/patient relationship**

Add this to the generated migration. The pair is unique so duplicate roster rows cannot exist.

```sql
create table public.coach_patients (
  id uuid primary key default gen_random_uuid(),
  coach_id uuid not null references public.profiles(id) on delete cascade,
  patient_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'active' check (status in ('active', 'archived')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (coach_id, patient_id),
  check (coach_id <> patient_id)
);
```

- [ ] **Step 3: Define plans, plan days, and prescribed exercises**

Use text catalog IDs and optional custom-exercise IDs. The check prevents an ambiguous exercise reference.

```sql
create table public.workout_plans (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_by uuid not null references public.profiles(id) on delete restrict,
  name text not null,
  description text not null default '',
  active boolean not null default true,
  starts_on date,
  ends_on date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (ends_on is null or starts_on is null or ends_on >= starts_on)
);

create table public.workout_plan_days (
  id uuid primary key default gen_random_uuid(),
  workout_plan_id uuid not null references public.workout_plans(id) on delete cascade,
  week_number integer not null default 1 check (week_number > 0),
  weekday integer not null check (weekday between 1 and 7),
  title text not null,
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workout_plan_id, week_number, weekday)
);

create table public.workout_plan_exercises (
  id uuid primary key default gen_random_uuid(),
  workout_plan_day_id uuid not null references public.workout_plan_days(id) on delete cascade,
  catalog_exercise_id text references public.exercises(id) on delete restrict,
  custom_exercise_id uuid,
  position integer not null check (position >= 0),
  prescribed_sets integer not null default 3 check (prescribed_sets > 0),
  min_reps integer check (min_reps > 0),
  max_reps integer check (max_reps > 0),
  target_weight_grams integer not null default 0 check (target_weight_grams >= 0),
  rest_seconds integer not null default 90 check (rest_seconds >= 0),
  tempo text,
  target_rpe numeric(3,1) check (target_rpe between 0 and 10),
  target_rir integer check (target_rir between 0 and 10),
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workout_plan_day_id, position),
  check ((catalog_exercise_id is not null)::int + (custom_exercise_id is not null)::int = 1),
  check (max_reps is null or min_reps is null or max_reps >= min_reps)
);
```

- [ ] **Step 4: Add indexes, triggers, and RLS policies**

```sql
create index coach_patients_patient_active_idx on public.coach_patients(patient_id) where status = 'active';
create index workout_plans_user_active_idx on public.workout_plans(user_id) where active;

alter table public.coach_patients enable row level security;
alter table public.workout_plans enable row level security;
alter table public.workout_plan_days enable row level security;
alter table public.workout_plan_exercises enable row level security;

create policy "patient or coach can read plan" on public.workout_plans for select
using (user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp
  where cp.patient_id = workout_plans.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
));
```

Add matching insert/update/delete policies: patients can edit their own rows;
trainers can edit rows only where the matching `coach_patients` row is active.
Create `set_updated_at` triggers for all four tables using the existing function.

- [ ] **Step 5: Verify schema and policy behavior locally**

Run:

```bash
supabase db reset
supabase db lint
```

Expected: all migrations apply and lint returns no errors.

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/003_workout_plans.sql
git commit -m "feat: add patient workout plans"
```

## Task 2: Add mutable daily workout instances and advanced set metrics

**Files:**

- Create: `supabase/migrations/004_workout_instances.sql`
- Test: Supabase local SQL assertions after `supabase db reset`

**Interfaces:**

- Consumes `workout_plan_days` and `workout_plan_exercises` from Task 1.
- Produces `workouts.workout_plan_day_id`, `public.workout_exercises`, and advanced `workout_sets` fields.
- App code creates an instance by copying prescribed rows into `workout_exercises`.

- [ ] **Step 1: Create the migration**

```bash
supabase migration new workout_instances
```

- [ ] **Step 2: Extend daily workouts and create instance exercises**

```sql
alter table public.workouts
  add column workout_plan_day_id uuid references public.workout_plan_days(id) on delete set null,
  add column scheduled_for date,
  add column status text not null default 'planned' check (status in ('planned', 'in_progress', 'completed', 'skipped')),
  add column notes text not null default '';

create unique index workouts_user_scheduled_for_idx
  on public.workouts(user_id, scheduled_for)
  where scheduled_for is not null;

create table public.workout_exercises (
  id uuid primary key default gen_random_uuid(),
  workout_id uuid not null references public.workouts(id) on delete cascade,
  source_plan_exercise_id uuid references public.workout_plan_exercises(id) on delete set null,
  catalog_exercise_id text references public.exercises(id) on delete restrict,
  custom_exercise_id uuid,
  position integer not null check (position >= 0),
  prescribed_sets integer not null default 3 check (prescribed_sets > 0),
  min_reps integer check (min_reps > 0),
  max_reps integer check (max_reps > 0),
  target_weight_grams integer not null default 0 check (target_weight_grams >= 0),
  rest_seconds integer not null default 90 check (rest_seconds >= 0),
  tempo text,
  target_rpe numeric(3,1) check (target_rpe between 0 and 10),
  target_rir integer check (target_rir between 0 and 10),
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workout_id, position),
  check ((catalog_exercise_id is not null)::int + (custom_exercise_id is not null)::int = 1),
  check (max_reps is null or min_reps is null or max_reps >= min_reps)
);
```

- [ ] **Step 3: Add advanced per-set fields and migrate legacy data**

```sql
alter table public.workout_sets
  add column rpe numeric(3,1) check (rpe between 0 and 10),
  add column rir integer check (rir between 0 and 10),
  add column tempo text,
  add column rest_seconds integer check (rest_seconds >= 0),
  add column notes text not null default '';
```

Do not drop the legacy `exercise_name` or `workout_id` columns in this migration.
Backfill `workout_exercise_id` only for rows with an unambiguous matching
instance exercise; leave ambiguous legacy rows intact. New app writes must
always populate `workout_exercise_id`.

- [ ] **Step 4: Add patient/trainer policies and indexes**

```sql
create index workout_exercises_workout_position_idx on public.workout_exercises(workout_id, position);
create index workout_sets_workout_exercise_set_idx on public.workout_sets(workout_exercise_id, set_number);

alter table public.workout_exercises enable row level security;
create policy "patient or coach manages workout exercises" on public.workout_exercises for all
using (exists (
  select 1 from public.workouts w
  where w.id = workout_exercises.workout_id and (
    w.user_id = auth.uid() or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
    )
  )
));
```

Use the same parent-workout access predicate for `workout_sets`.

- [ ] **Step 5: Add a snapshot SQL function**

Create `public.create_workout_instance(plan_day_id uuid, scheduled_for date)` as
`security invoker`. It inserts one workout row and copies every ordered
`workout_plan_exercises` row to `workout_exercises` in a transaction. It must
return the new `workouts.id` and reject callers who are neither the patient nor
their active trainer.

- [ ] **Step 6: Verify snapshot isolation**

Run this SQL in a local Supabase session after creating fixture rows:

```sql
select public.create_workout_instance('<plan-day-id>', current_date);
update public.workout_exercises set prescribed_sets = 1 where workout_id = '<instance-id>';
select prescribed_sets from public.workout_plan_exercises where workout_plan_day_id = '<plan-day-id>';
```

Expected: the plan prescription is unchanged after editing the instance.

- [ ] **Step 7: Commit**

```bash
git add supabase/migrations/004_workout_instances.sql
git commit -m "feat: add editable workout instances"
```

## Task 3: Add custom exercises and private media storage

**Files:**

- Create: `supabase/migrations/005_custom_exercise_media.sql`
- Test: Supabase local Storage and RLS policy checks

**Interfaces:**

- Produces `public.custom_exercises` and private `exercise-media` Storage bucket.
- Tasks 1 and 2 reference `custom_exercises.id` using deferred foreign keys added here.

- [ ] **Step 1: Create the migration**

```bash
supabase migration new custom_exercise_media
```

- [ ] **Step 2: Create custom exercises and complete foreign keys**

```sql
create table public.custom_exercises (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  category text not null default 'custom',
  equipment text not null default 'other',
  target text not null default '',
  instructions text not null default '',
  image_path text,
  gif_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.workout_plan_exercises
  add constraint workout_plan_exercises_custom_exercise_id_fkey
  foreign key (custom_exercise_id) references public.custom_exercises(id) on delete restrict;

alter table public.workout_exercises
  add constraint workout_exercises_custom_exercise_id_fkey
  foreign key (custom_exercise_id) references public.custom_exercises(id) on delete restrict;
```

Because Tasks 1 and 2 contain `custom_exercise_id` before its table exists,
define those columns without foreign-key constraints until this task adds them.

- [ ] **Step 3: Add Storage bucket and policies**

```sql
insert into storage.buckets (id, name, public)
values ('exercise-media', 'exercise-media', false)
on conflict (id) do nothing;

create policy "owners upload exercise media" on storage.objects for insert to authenticated
with check (bucket_id = 'exercise-media' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "owners read exercise media" on storage.objects for select to authenticated
using (bucket_id = 'exercise-media' and (storage.foldername(name))[1] = auth.uid()::text);
```

Add matching policies that permit an active trainer to read a linked patient’s
folder. Use paths in the form `<owner-id>/<uuid>.<extension>`.

- [ ] **Step 4: Verify ownership isolation**

Use two local authenticated test users. Upload under user A’s folder, then
attempt a select as user B with no `coach_patients` relationship.

Expected: user A can read; user B receives an RLS denial.

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/005_custom_exercise_media.sql
git commit -m "feat: add custom exercise media"
```

## Task 4: Import and verify the shared dataset

**Files:**

- Create: `scripts/import_exercises.py`
- Modify: `supabase/README.md`
- Test: importer dry run and row-count query

**Interfaces:**

- Consumes upstream `data/exercises.json`.
- Upserts `public.exercises` by text ID.
- Requires `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` only in the local shell.

- [ ] **Step 1: Write a failing dry-run assertion**

Create `scripts/test_import_exercises.py` with:

```python
from import_exercises import to_row

def test_to_row_preserves_catalog_id_and_attribution():
    row = to_row({"id": "0001", "name": "3/4 sit-up", "category": "waist", "body_part": "waist", "equipment": "body weight", "target": "abs"})
    assert row["id"] == "0001"
    assert row["attribution"] == "© Gym visual — https://gymvisual.com/"
```

- [ ] **Step 2: Run the assertion before implementation**

Run:

```bash
python3 -m unittest scripts/test_import_exercises.py
```

Expected: FAIL because `to_row` does not exist.

- [ ] **Step 3: Implement deterministic transformation and batched upsert**

Implement `to_row(record: dict) -> dict` to map every field in
`002_exercises.sql`, including JSON objects for instructions and muscle arrays.
POST 100-row batches to `/rest/v1/exercises` with:

```text
Prefer: resolution=merge-duplicates
```

Add `--dry-run` to print the transformed count and first row without contacting
Supabase. Download the source JSON if it is absent from the selected path.

- [ ] **Step 4: Run local transformer verification**

Run:

```bash
python3 -m unittest scripts/test_import_exercises.py
python3 scripts/import_exercises.py --dry-run
```

Expected: test passes and dry run reports approximately 1,324 transformed rows.

- [ ] **Step 5: Import against the linked project**

Run:

```bash
export SUPABASE_URL="https://wwcemcbyesjqoshksnfv.supabase.co"
export SUPABASE_SERVICE_ROLE_KEY="<local-service-role-key>"
python3 scripts/import_exercises.py
supabase db execute --linked --sql "select count(*) from public.exercises;"
```

Expected: the catalog count is approximately 1,324.

- [ ] **Step 6: Document the command and key boundary**

Add the import command and the warning that service-role credentials are local
only to `supabase/README.md`.

- [ ] **Step 7: Commit**

```bash
git add scripts/import_exercises.py scripts/test_import_exercises.py supabase/README.md
git commit -m "feat: add exercise catalog importer"
```

## Task 5: Mirror active workout data locally with SQLDelight

**Files:**

- Create: `composeApp/src/commonMain/sqldelight/com/reus/nutri/db/Workout.sq`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt`
- Test: `composeApp/src/commonTest/kotlin/com/reus/nutri/WorkoutQueriesTest.kt`

**Interfaces:**

- Produces local tables for plan days, workout instances, instance exercises,
  completed sets, and pending mutations.
- The repository in Task 6 consumes generated SQLDelight queries.

- [ ] **Step 1: Write a failing SQLDelight test**

```kotlin
@Test
fun pendingSetMutationSurvivesReload() {
    database.workoutQueries.upsertPendingMutation("mutation-1", "set", "set-1", "update", "{}")
    assertEquals(1, database.workoutQueries.pendingMutations().executeAsList().size)
}
```

- [ ] **Step 2: Run the test before adding the schema**

Run:

```bash
gradle :composeApp:allTests
```

Expected: FAIL because `workoutQueries` does not exist.

- [ ] **Step 3: Add only active-workout local tables**

Create SQLDelight tables for:

```text
local_workout_plan_day
local_workout
local_workout_exercise
local_workout_set
pending_mutation
```

`pending_mutation` must include `id`, `entity_type`, `entity_id`, `operation`,
`payload_json`, `created_at`, and `attempt_count`. Use UUID/text IDs from
Supabase directly; do not generate replacement local IDs.

- [ ] **Step 4: Add generated-query wrappers only where already needed**

Expose queries through `LocalDatabase.kt` for:

```kotlin
suspend fun loadActiveWorkout(day: String): LocalWorkout?
suspend fun saveWorkoutSnapshot(snapshot: WorkoutSnapshot)
suspend fun enqueueMutation(mutation: PendingMutation)
suspend fun pendingMutations(): List<PendingMutation>
```

- [ ] **Step 5: Run the full test suite**

Run:

```bash
gradle :composeApp:allTests
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/sqldelight composeApp/src/commonMain/kotlin/com/reus/nutri/LocalDatabase.kt composeApp/src/commonTest
git commit -m "feat: cache active workouts locally"
```

## Task 6: Wire PostgREST reads and queued mutation sync

**Files:**

- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/WorkoutRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt`
- Test: `composeApp/src/commonTest/kotlin/com/reus/nutri/WorkoutRepositoryTest.kt`

**Interfaces:**

- Consumes the local query wrappers from Task 5 and the remote schema from
  Tasks 1-3.
- Produces `loadWorkoutForDay(day: LocalDate)`, `createWorkoutFromPlanDay(...)`,
  `logSet(...)`, and `syncPendingMutations()`.

- [ ] **Step 1: Write failing repository tests**

```kotlin
@Test
fun logSetUpdatesLocalStateBeforeRemoteSync() = runTest {
    repository.logSet(set)
    assertEquals(set.id, localDatabase.pendingMutations().single().entityId)
}
```

- [ ] **Step 2: Run repository tests before implementation**

Run:

```bash
gradle :composeApp:allTests
```

Expected: FAIL because `WorkoutRepository` does not exist.

- [ ] **Step 3: Define serializable DTOs and map them at the boundary**

Add `@Serializable` DTOs for plan days, workouts, workout exercises, and sets.
Keep SQLDelight generated types local to `LocalDatabase.kt`; map them to domain
types in `WorkoutRepository.kt`.

- [ ] **Step 4: Implement local-first operations**

`loadWorkoutForDay` must return local data immediately, then refresh it from
PostgREST when online. `logSet` and exercise edits must write the local rows and
enqueue a mutation in the same SQLDelight transaction before attempting remote
sync.

- [ ] **Step 5: Implement ordered sync and conflict handling**

Send queued mutations oldest-first. On success, delete the queue row. On a
network failure, retain it and increment `attempt_count`. On a server revision
conflict, retain both local payload and server response in a conflict record and
surface the conflict in the UI; do not overwrite either side.

- [ ] **Step 6: Wire the active-workout screen**

Replace static active-set values in `App.kt` with repository state. The UI must
show local values while offline and show a small sync/error status instead of
dropping a completed set.

- [ ] **Step 7: Run Android verification**

Run:

```bash
gradle :composeApp:assembleDebug
gradle :composeApp:installDebug
```

Expected: debug APK builds and installs successfully.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/reus/nutri composeApp/src/commonTest
git commit -m "feat: sync workouts offline first"
```

## Plan review

- Spec coverage: Tasks 1-3 implement the approved remote model and security;
  Task 4 imports the catalog; Tasks 5-6 implement the approved offline boundary.
- No reusable templates, recommendation engine, or social features are added.
- The plan preserves the current shared catalog and does not rewrite already
  applied migrations.
