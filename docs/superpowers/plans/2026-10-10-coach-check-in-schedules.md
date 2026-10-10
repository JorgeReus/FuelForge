# Coach Check-In Schedules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static daily check-in assumption with one configurable default recurring schedule per coach, optional per-client overrides, and due-state support for push notifications.

**Architecture:** Store one schedule row per coach, keyed by `coach_id`; store client-specific overrides keyed by `(coach_id, patient_id)`. The client app calculates the next due occurrence in the schedule timezone and displays the existing check-in form when due. Push delivery is a separate backend stage using registered device tokens and scheduled notification dispatch.

**Tech Stack:** Supabase PostgreSQL migrations/RLS, Kotlin Multiplatform Compose, SQLDelight local cache, Supabase Edge Functions or scheduled worker, FCM/APNs push delivery.

**Spec:** This plan implements the approved requirements in chat: one default schedule per coach; recurrence every day, every week, or every N weeks; optional client overrides; push notification when due.

## Global Constraints

- `coach_check_in_schedules.coach_id` MUST be the primary key; there is exactly one default schedule per coach.
- The default schedule MUST NOT use `active`, `is_default`, or a generated schedule ID.
- Client overrides MUST use `(coach_id, patient_id)` as the primary key.
- Existing `patient_check_ins` records and the current check-in form MUST remain compatible.
- Schedule calculations MUST use the configured IANA timezone.
- Notification delivery MUST be idempotent per patient and scheduled occurrence.
- Supabase schema changes MUST be captured in migrations and applied with `supabase db push`.

## Review Focus

- A daily schedule crossing midnight in the configured timezone must calculate the correct due date.
- An every-N-weeks schedule must use the configured anchor date and not drift after missed check-ins.
- A client override must not mutate the coach default schedule.
- Repeated scheduler runs must not send duplicate notifications for one occurrence.
- Missing or invalid device tokens must not block check-in submission.

---

### Task 1: Schedule database and RLS

**Files:**
- Create: `supabase/migrations/<next>_coach_check_in_schedules.sql`
- Test: SQL verification queries in the migration review

**Interfaces:**
- Produces `public.coach_check_in_schedules` with `coach_id` primary key and columns `name`, `frequency`, `interval_weeks`, `weekday`, `local_time`, `timezone`, `anchor_date`, timestamps.
- Produces `public.patient_check_in_schedule_settings` with composite primary key `(coach_id, patient_id)` and nullable override fields.

- [ ] **Step 1: Create the coach schedule table**
  - `coach_id uuid primary key references profiles(id) on delete cascade`
  - `frequency` constrained to `daily`, `weekly`, `every_n_weeks`
  - `interval_weeks > 0`
  - `weekday` constrained to `1..7` for weekly modes
  - `local_time time not null`
  - `timezone text not null`
  - `anchor_date date not null`
  - No `active` column.

- [ ] **Step 2: Create the patient override table**
  - Primary key `(coach_id, patient_id)`.
  - Foreign key to the coach schedule and patient profile.
  - Overrides nullable; null means inherit the coach default.
  - Include `enabled boolean not null default true`.

- [ ] **Step 3: Add RLS policies**
  - Coaches manage schedules for their own `coach_id`.
  - Linked patients read their effective schedule and may not modify coach defaults.
  - Coaches manage overrides only for active `coach_patients` links.

- [ ] **Step 4: Apply and verify**
  - Run `supabase db push`.
  - Verify one-row-per-coach behavior, RLS access, and invalid recurrence rejection.

### Task 2: Effective schedule calculation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInSchedule.kt`
- Create: `composeApp/src/commonTest/kotlin/com/reus/nutri/CheckInScheduleTest.kt`

**Interfaces:**
- `data class CheckInSchedule(...)`
- `fun nextCheckInOccurrence(schedule: CheckInSchedule, now: Instant): Instant`
- `fun isCheckInDue(schedule: CheckInSchedule, now: Instant, lastRecordedOn: LocalDate?): Boolean`

- [ ] **Step 1: Add failing tests** for daily, weekly, every-N-weeks, timezone conversion, anchor dates, and missed occurrences.
- [ ] **Step 2: Implement the recurrence calculator** using `kotlin.time` and `kotlinx.datetime` conventions already used by the project.
- [ ] **Step 3: Add tests for invalid combinations** such as missing weekday for weekly frequency and non-positive intervals.
- [ ] **Step 4: Run the focused common test suite** and verify all recurrence cases pass.

### Task 3: Client effective schedule and check-in UI

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/CheckInRepository.kt`

**Interfaces:**
- `suspend fun fetchEffectiveCheckInSchedule(patientId: String): EffectiveCheckInSchedule?`
- `data class EffectiveCheckInSchedule(...)`

- [ ] **Step 1: Add serializable schedule DTOs and fetch the coach default plus patient override.**
- [ ] **Step 2: Resolve override fields over the coach default without mutating either record.**
- [ ] **Step 3: Replace the static check-in card copy with `Due`, `Next check-in`, or `Completed` state.**
- [ ] **Step 4: Keep the current check-in form and submission path unchanged.**
- [ ] **Step 5: Verify the client UI for no schedule, due schedule, completed occurrence, and future occurrence.**

### Task 4: Coach schedule configuration UI

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/reus/nutri/CoachCheckInScheduleScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt`

**Interfaces:**
- `suspend fun upsertCoachCheckInSchedule(schedule: CoachCheckInScheduleDto)`
- `suspend fun upsertPatientCheckInScheduleOverride(override: PatientCheckInScheduleOverrideDto)`

- [ ] **Step 1: Add coach-only schedule editing for daily, weekly, and every-N-weeks.**
- [ ] **Step 2: Validate weekday, interval, time, timezone, and anchor date before save.**
- [ ] **Step 3: Add client override controls without creating another default schedule.**
- [ ] **Step 4: Add RLS-backed error handling for unauthorized users.**
- [ ] **Step 5: Verify save, reload, and override inheritance behavior.**

### Task 5: Device tokens and idempotent push dispatch

**Files:**
- Create: `supabase/migrations/<next>_check_in_push_tokens.sql`
- Create: `supabase/functions/send-check-in-notifications/index.ts`
- Modify: Android/iOS platform notification registration files.

**Interfaces:**
- `patient_push_tokens(patient_id, platform, token, timezone, updated_at)`.
- `check_in_notification_deliveries(patient_id, occurrence_key, sent_at)` with unique `(patient_id, occurrence_key)`.

- [ ] **Step 1: Add token and delivery tables with RLS/service-role boundaries.**
- [ ] **Step 2: Register and refresh device tokens on the client.**
- [ ] **Step 3: Implement schedule occurrence lookup in the Edge Function.**
- [ ] **Step 4: Insert the delivery key before sending or use an atomic claim to prevent duplicate sends.**
- [ ] **Step 5: Send FCM/APNs notifications and tolerate invalid or missing tokens.**
- [ ] **Step 6: Add a deep link that opens the check-in form.**
- [ ] **Step 7: Verify duplicate scheduler runs produce one notification per occurrence.**

### Task 6: End-to-end verification

**Files:**
- Modify: relevant existing tests only.

- [ ] **Step 1: Run the focused recurrence tests.**
- [ ] **Step 2: Run `gradle :composeApp:compileDebugKotlinAndroid :composeApp:assembleDebug`.**
- [ ] **Step 3: Apply all Supabase migrations with `supabase db push`.**
- [ ] **Step 4: Install the Android debug build and verify the due-state/check-in flow on a device.**
- [ ] **Step 5: Exercise coach default, patient override, completed occurrence, and push deep-link scenarios.**
