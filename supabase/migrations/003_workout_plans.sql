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

create index coach_patients_patient_active_idx on public.coach_patients(patient_id) where status = 'active';
create index workout_plans_user_active_idx on public.workout_plans(user_id) where active;
create index workout_plan_exercises_day_position_idx on public.workout_plan_exercises(workout_plan_day_id, position);

do $$
declare table_name text;
begin
  foreach table_name in array array['coach_patients', 'workout_plans', 'workout_plan_days', 'workout_plan_exercises'] loop
    execute format('create trigger set_updated_at before update on public.%I for each row execute function public.set_updated_at()', table_name);
  end loop;
end $$;

alter table public.coach_patients enable row level security;
alter table public.workout_plans enable row level security;
alter table public.workout_plan_days enable row level security;
alter table public.workout_plan_exercises enable row level security;

create policy "users read own coaching links" on public.coach_patients for select
using (coach_id = auth.uid() or patient_id = auth.uid());

create policy "coaches manage coaching links" on public.coach_patients for all
using (coach_id = auth.uid())
with check (coach_id = auth.uid());

create policy "patients and coaches read plans" on public.workout_plans for select
using (
  user_id = auth.uid()
  or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = workout_plans.user_id
      and cp.coach_id = auth.uid()
      and cp.status = 'active'
  )
);

create policy "patients and coaches manage plans" on public.workout_plans for all
using (
  user_id = auth.uid()
  or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = workout_plans.user_id
      and cp.coach_id = auth.uid()
      and cp.status = 'active'
  )
)
with check (
  user_id = auth.uid()
  or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = workout_plans.user_id
      and cp.coach_id = auth.uid()
      and cp.status = 'active'
  )
);

create policy "patients and coaches read plan days" on public.workout_plan_days for select
using (exists (select 1 from public.workout_plans p where p.id = workout_plan_id and (p.user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
))));

create policy "patients and coaches manage plan days" on public.workout_plan_days for all
using (exists (select 1 from public.workout_plans p where p.id = workout_plan_id and (p.user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
))))
with check (exists (select 1 from public.workout_plans p where p.id = workout_plan_id and (p.user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
))));

create policy "patients and coaches read plan exercises" on public.workout_plan_exercises for select
using (exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = workout_plan_day_id and (p.user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
))));

create policy "patients and coaches manage plan exercises" on public.workout_plan_exercises for all
using (exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = workout_plan_day_id and (p.user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
))))
with check (exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = workout_plan_day_id and (p.user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
))));
