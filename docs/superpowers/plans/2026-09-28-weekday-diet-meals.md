# Weekday Diet Meals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Store diet meals by weekday and load the signed-in user’s routine without calendar-date or timezone coupling.

**Architecture:** Add a required `weekday` value to `public.meals`, migrate existing dated meals from their scheduled timestamps, and make the Kotlin query filter by the current ISO weekday. Preserve `scheduled_at` as an optional display/order time.

**Tech Stack:** Supabase PostgreSQL migrations, Kotlin Multiplatform, Supabase Kotlin PostgREST.

**Spec:** User request to migrate diet meals to a weekday-based route.

## Global Constraints

- Remote schema changes must be captured in `supabase/migrations/`.
- Existing meal rows must remain usable after migration.
- The app must query weekday meals for the authenticated user.
- `scheduled_at` is optional and must not determine day membership.

---

### Task 1: Migrate meals to weekdays

**Files:**
- Create: `supabase/migrations/013_meals_weekday.sql`

- [ ] Add `weekday smallint` with a range constraint from 1 (Monday) through 7 (Sunday).
- [ ] Backfill existing rows from `scheduled_at` using PostgreSQL ISO weekday extraction.
- [ ] Make `weekday` non-null after backfill.
- [ ] Add an index on `(user_id, weekday, scheduled_at)`.

### Task 2: Switch app meal loading

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/Supabase.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/reus/nutri/App.kt`

- [ ] Add `weekday` to `MealDto`.
- [ ] Change `fetchMealsForDay` to accept an ISO weekday integer and filter `weekday`.
- [ ] Compute the current weekday using `kotlinx.datetime.Clock`/`LocalDate` if available; otherwise derive it from the existing ISO date string with `java.time` unavailable in common code, so use a small deterministic date helper.
- [ ] Rename the loader to `fetchMealsForWeekday` and update the dashboard call.
- [ ] Keep meal ordering by optional `scheduled_at`.

### Task 3: Verify

- [ ] Run `gradle :composeApp:compileDebugKotlinAndroid`.
- [ ] Run `gradle :composeApp:assembleDebug`.
- [ ] Confirm the migration SQL is syntactically represented and existing meal rows receive weekdays.
