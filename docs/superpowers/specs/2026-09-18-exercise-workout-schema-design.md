# Exercise and Workout Schema Design

## Goal

Store the shared exercise catalog, patient-specific weekly workout plans, mutable
daily workout instances, and advanced set logging for the Nutri screens.

## Scope

This design covers the Supabase PostgreSQL schema, RLS, exercise-dataset import,
media storage references, and the SQLDelight boundary for offline-first clients.
It does not add workout templates reusable across patients, social features, or
automatic exercise recommendations.

## Shared catalog

`public.exercises` remains the read-only shared catalog imported from
`hasaneyldrm/exercises-dataset`. Its existing text ID is retained so dataset
records can be upserted deterministically. Clients may select from the catalog,
but cannot insert, update, or delete it.

`public.custom_exercises` stores patient or trainer-created movements. It has a
UUID primary key, an `owner_id`, name, optional catalog-like metadata, and
optional `image_path` and `gif_path` values. Paths reference a private Supabase
Storage bucket named `exercise-media`; database rows never store media bytes.

Both catalog and custom exercises are referenced from prescribed and performed
workout exercises through mutually exclusive foreign keys: exactly one of
`catalog_exercise_id` or `custom_exercise_id` is non-null.

## Patient workout plans

`public.workout_plans` belongs to one patient and records its trainer creator,
name, optional description, active state, and effective dates. There are no
cross-patient reusable templates.

`public.workout_plan_days` belongs to a plan and represents a recurring weekly
slot:

- `week_number` starts at 1 for multi-week plans.
- `weekday` uses ISO weekday numbers 1-7.
- `title` is the patient-facing day name.

`public.workout_plan_exercises` belongs to a plan day. It defines the ordered
prescription: exercise reference, prescribed set count, rep range, target load,
rest seconds, tempo, target RIR, and coach notes.

## Daily workout instances

`public.workouts` is the mutable instance for a patient on `scheduled_for`. It
optionally references the source `workout_plan_day`, but retains its own name,
description, status, timestamps, and estimated calories.

Creating an instance copies every `workout_plan_exercises` row into
`public.workout_exercises`. The copied rows are then independent. A patient may
add, remove, reorder, or edit them without changing future plan days. A unique
constraint on `(user_id, scheduled_for)` prevents duplicate daily instances.

`public.workout_exercises` stores the instance-level exercise reference,
position, prescribed metrics, and notes. It replaces free-text exercise names
as the authoritative link for workout logging.

## Per-set logging

`public.workout_sets` references `workout_exercises`. It stores:

- set number
- load in grams
- repetitions
- RIR (0-10, nullable)
- tempo text (nullable)
- rest duration in seconds (nullable)
- completed timestamp
- notes

The existing legacy `exercise_name` and direct `workout_id` fields remain only
until a data migration/backfill is complete. New app writes must use
`workout_exercise_id`.

## Trainer access and RLS

`public.coach_patients` relates `coach_id` to `patient_id`, with a status of
`active` or `archived`. A trainer can read and edit a patient’s plans, plan days,
plan exercises, daily workouts, workout exercises, and sets only if an active
relationship exists. Patients can manage their own records.

Policies use `auth.uid()` and `coach_patients`; client apps never use a service
role key. The shared catalog allows authenticated reads only. Custom exercises
allow their owner and linked trainer access.

## Dataset import

The dataset import is separate from schema migrations:

1. Download `data/exercises.json`.
2. Transform each record into the existing `public.exercises` shape.
3. Upsert batches by text ID using a local script and service-role credentials.
4. Preserve the source attribution and media paths.

The service-role key is local-only and never placed in the KMP app or committed.

## Offline boundary

SQLDelight mirrors only data needed for current and recent workouts:

- exercise summaries and selected exercise details
- active plan days
- pending/current daily workout instances
- workout exercises and workout sets

The sync queue records patient mutations to daily instances, exercises, and
sets. Last-write-wins is acceptable for individual patient edits. Trainer and
patient concurrent edits are surfaced as a revision conflict; the server row is
not silently discarded.

## Migration sequence

1. Add `coach_patients` and `custom_exercises`.
2. Add `workout_plans`, `workout_plan_days`, and `workout_plan_exercises`.
3. Extend `workouts` and add `workout_exercises`.
4. Extend `workout_sets` with advanced metrics and migrate legacy references.
5. Add RLS policies and indexes.
6. Create the Storage bucket and policies for custom exercise media.
7. Run the dataset importer and verify catalog counts.

## Acceptance criteria

- A trainer can directly edit one linked patient’s weekly plan.
- A patient can generate and freely edit today’s workout without changing the
  source plan day.
- A completed set records RIR and related advanced metrics.
- Catalog exercise search works from the imported dataset.
- Custom exercises can reference a user-uploaded GIF or image.
- Unauthorized users cannot read or modify another patient’s workout data.
