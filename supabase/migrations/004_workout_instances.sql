alter table public.workouts
  add column workout_plan_day_id uuid references public.workout_plan_days(id) on delete set null,
  add column scheduled_for date,
  add column status text not null default 'planned' check (status in ('planned', 'in_progress', 'completed', 'skipped')),
  add column notes text not null default '';

create unique index workouts_user_scheduled_for_idx
  on public.workouts(user_id, scheduled_for)
  where scheduled_for is not null;

-- 002 already created this table. Extend it in place so existing workout rows
-- remain valid while custom exercises become possible.
alter table public.workout_exercises
  alter column exercise_id drop not null,
  add column if not exists source_plan_exercise_id uuid references public.workout_plan_exercises(id) on delete set null,
  add column if not exists catalog_exercise_id text references public.exercises(id) on delete restrict,
  add column if not exists custom_exercise_id uuid,
  add column if not exists position integer,
  add column if not exists min_reps integer,
  add column if not exists max_reps integer,
  add column if not exists tempo text,
  add column if not exists target_rpe numeric(3,1),
  add column if not exists target_rir integer,
  add column if not exists notes text not null default '';

update public.workout_exercises
set catalog_exercise_id = exercise_id,
    position = order_index,
    min_reps = target_reps,
    max_reps = target_reps
where catalog_exercise_id is null;

alter table public.workout_exercises
  alter column position set default 0,
  alter column position set not null,
  add constraint workout_exercises_source_check
    check ((catalog_exercise_id is not null)::int + (custom_exercise_id is not null)::int = 1) not valid,
  add constraint workout_exercises_rep_range_check
    check (max_reps is null or min_reps is null or max_reps >= min_reps) not valid,
  add constraint workout_exercises_rpe_check check (target_rpe between 0 and 10) not valid,
  add constraint workout_exercises_rir_check check (target_rir between 0 and 10) not valid;

alter table public.workout_exercises validate constraint workout_exercises_source_check;
alter table public.workout_exercises validate constraint workout_exercises_rep_range_check;
alter table public.workout_exercises validate constraint workout_exercises_rpe_check;
alter table public.workout_exercises validate constraint workout_exercises_rir_check;

create policy "patients and coaches manage workouts" on public.workouts for all
using (
  user_id = auth.uid()
  or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = workouts.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
  )
)
with check (
  user_id = auth.uid()
  or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = workouts.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
  )
);

alter table public.workout_sets
  add column rpe numeric(3,1) check (rpe between 0 and 10),
  add column rir integer check (rir between 0 and 10),
  add column tempo text,
  add column rest_seconds integer check (rest_seconds >= 0),
  add column notes text not null default '';

create index workout_exercises_workout_position_idx on public.workout_exercises(workout_id, position);
create index workout_sets_workout_exercise_set_idx on public.workout_sets(workout_exercise_id, set_number);

do $$
begin
  create trigger set_updated_at before update on public.workout_exercises
    for each row execute function public.set_updated_at();
exception when duplicate_object then null;
end $$;

alter table public.workout_exercises enable row level security;

create policy "patients and coaches manage workout exercises" on public.workout_exercises for all
using (exists (
  select 1 from public.workouts w
  where w.id = workout_id and (
    w.user_id = auth.uid() or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
    )
  )
))
with check (exists (
  select 1 from public.workouts w
  where w.id = workout_id and (
    w.user_id = auth.uid() or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
    )
  )
));

create policy "patients and coaches manage workout sets" on public.workout_sets for all
using (exists (
  select 1 from public.workouts w
  where w.id = workout_id and (
    w.user_id = auth.uid() or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
    )
  )
))
with check (exists (
  select 1 from public.workouts w
  where w.id = workout_id and (
    w.user_id = auth.uid() or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
    )
  )
));

create or replace function public.create_workout_instance(plan_day_id uuid, planned_date date)
returns uuid
language plpgsql
security invoker
as $$
declare
  new_workout_id uuid;
  patient_id uuid;
begin
  select p.user_id into patient_id
  from public.workout_plan_days d
  join public.workout_plans p on p.id = d.workout_plan_id
  where d.id = plan_day_id;

  if patient_id is null or not (
    patient_id = auth.uid()
    or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = patient_id and cp.coach_id = auth.uid() and cp.status = 'active'
    )
  ) then
    raise exception 'not authorized to create workout instance';
  end if;

  insert into public.workouts (user_id, workout_plan_day_id, name, scheduled_for)
  select p.user_id, d.id, d.title, planned_date
  from public.workout_plan_days d
  join public.workout_plans p on p.id = d.workout_plan_id
  where d.id = plan_day_id
  returning id into new_workout_id;

  insert into public.workout_exercises (
    workout_id, source_plan_exercise_id, catalog_exercise_id, custom_exercise_id,
    position, prescribed_sets, min_reps, max_reps, target_weight_grams,
    rest_seconds, tempo, target_rpe, target_rir, notes
  )
  select new_workout_id, id, catalog_exercise_id, custom_exercise_id,
    position, prescribed_sets, min_reps, max_reps, target_weight_grams,
    rest_seconds, tempo, target_rpe, target_rir, notes
  from public.workout_plan_exercises
  where workout_plan_day_id = plan_day_id
  order by position;

  return new_workout_id;
end;
$$;
