# Friendly Patient Check-ins Design

## Goal

Let a patient complete a daily recovery check-in in under 20 seconds, using
human labels instead of numeric scores, while persisting normalized values for
coach review and trend analysis.

## Scope

This design replaces the current Todo demonstration path with a local-first
daily check-in flow. It adds optional health-data imports on Android through
Health Connect and on iOS through HealthKit. It does not infer subjective
recovery values from wearable data or write workout/nutrition data back to a
health platform.

## Data model

`public.patient_check_ins` is the remote source of truth. Each patient has at
most one check-in for `recorded_on` and contains:

- `weight_grams` and `sleep_minutes`, optionally imported and always editable.
- `soreness`, `performance`, `motivation`, `hunger`, `fatigue`, `stress`, and
  `sleep_quality` as nullable normalized integer scores from 0 to 10.
- `neat_minutes`, optionally derived from activity data.
- `comments` as optional patient text.

The score remains numeric in storage. The application renders four fixed
friendly choices and maps them to scores:

| Metric | Choices and scores |
| --- | --- |
| Soreness | None 0, Light 3, Moderate 6, Very sore 9 |
| Performance | Struggled 2, Okay 5, Strong 8, Best yet 10 |
| Motivation | Low 2, Neutral 5, Ready 8, Locked in 10 |
| Hunger | Not hungry 2, Normal 5, Hungry 8, Ravenous 10 |
| Fatigue | Fresh 1, A little tired 4, Tired 7, Drained 10 |
| Stress | Calm 1, Manageable 4, High 7, Overwhelmed 10 |
| Sleep quality | Poor 2, Fair 5, Good 8, Great 10 |

## Local-first behavior

SQLDelight stores one local check-in per day and a mutation queue. A user save
updates local state and queues an upsert in one transaction before any network
attempt. Sync sends queued upserts oldest first. A successful remote write
removes the queue row. Network failures retain the row and increment its
attempt count. The UI never loses saved check-in input when offline.

The queue uses a check-in ID generated on-device so a later upsert is
idempotent. `recorded_on` remains the unique business key in Supabase.

## User experience

The Today screen provides a `Daily check-in` card. Opening it shows the seven
subjective metrics as four-choice chips, imported sleep/weight rows with a
source label, optional editable fallback values, an optional note, and one
Save button. No numeric score is shown to the patient.

Imported values are suggestions. A patient can edit or clear them before
saving. Missing permissions, unavailable health stores, or no readings show a
neutral `Not connected`/`No data yet` state and do not block manual completion.

## Health platform boundary

`commonMain` declares `HealthDataProvider` with a single `readDailySnapshot`
operation. It returns nullable values for weight, sleep minutes, steps, and
active minutes plus a source label. Android uses Health Connect; iOS uses
HealthKit. The platform implementation owns permission requests and read
availability checks. The shared repository turns steps/active minutes into a
suggested NEAT value only; it does not claim this is a clinical measurement.

The first release requests read access only for weight, sleep, steps, and
activity. It does not request heart-rate data or background/historical reads.

## Authentication precondition

Supabase RLS requires an authenticated patient session. The current prototype
does not provide login UI, so this feature must not invent a patient ID or use
anonymous writes. Until authentication exists, the check-in card shows `Sign
in required` and disables remote save. The repository receives a user ID only
from the Supabase Auth session.

## Security and privacy

The app uses the publishable Supabase key only. Health permission is explicit,
per data type, and optional. No health credentials or raw health records are
sent to Supabase; only the final patient-approved check-in fields are stored.

## Acceptance criteria

- A patient can complete and save a friendly check-in offline.
- The persisted values use the defined score mappings.
- Existing check-ins load before a remote refresh.
- Android imports available Health Connect sleep, weight, steps, and activity
  only after consent; iOS does the HealthKit equivalent.
- A denied or unavailable health source leaves manual entry usable.
- The trainer-readable Supabase row is created or updated without a service
  role key.
