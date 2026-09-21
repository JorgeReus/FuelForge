create table public.workout_plan_blocks (
  id uuid primary key default gen_random_uuid(),
  workout_plan_id uuid not null references public.workout_plans(id) on delete cascade,
  block_number integer not null check (block_number > 0),
  name text not null default '',
  phase text not null default '',
  priority text not null default '',
  cardio_minutes integer check (cardio_minutes >= 0),
  extra_workout_notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workout_plan_id, block_number)
);

alter table public.workout_plan_days
  add column workout_plan_block_id uuid references public.workout_plan_blocks(id) on delete set null;

create table public.patient_check_ins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  recorded_on date not null,
  weight_grams integer check (weight_grams > 0),
  soreness integer check (soreness between 0 and 10),
  performance integer check (performance between 0 and 10),
  motivation integer check (motivation between 0 and 10),
  hunger integer check (hunger between 0 and 10),
  fatigue integer check (fatigue between 0 and 10),
  stress integer check (stress between 0 and 10),
  sleep_hours numeric(4,2) check (sleep_hours between 0 and 24),
  sleep_quality integer check (sleep_quality between 0 and 10),
  neat_minutes integer check (neat_minutes >= 0),
  comments text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, recorded_on)
);

create table public.workout_warmups (
  id uuid primary key default gen_random_uuid(),
  plan_day_id uuid references public.workout_plan_days(id) on delete cascade,
  workout_id uuid references public.workouts(id) on delete cascade,
  position integer not null default 0 check (position >= 0),
  kind text not null check (kind in ('dynamic', 'specific')),
  exercise_name text not null,
  technique text not null default '',
  notes text not null default '',
  sets integer check (sets > 0),
  reps integer check (reps > 0),
  rest_seconds integer check (rest_seconds >= 0),
  video_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((plan_day_id is not null)::int + (workout_id is not null)::int = 1)
);

create table public.workout_cardio (
  id uuid primary key default gen_random_uuid(),
  plan_day_id uuid references public.workout_plan_days(id) on delete cascade,
  workout_id uuid references public.workouts(id) on delete cascade,
  modality text not null,
  duration_minutes integer check (duration_minutes > 0),
  intensity text not null default '',
  distance_meters integer check (distance_meters >= 0),
  calories integer check (calories >= 0),
  notes text not null default '',
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((plan_day_id is not null)::int + (workout_id is not null)::int = 1)
);

create table public.workout_progression_rules (
  id uuid primary key default gen_random_uuid(),
  plan_exercise_id uuid not null references public.workout_plan_exercises(id) on delete cascade,
  rule_type text not null check (rule_type in ('double_progression', 'load_match', 'rep_match', 'top_set', 'deload', 'custom')),
  load_increment_grams integer check (load_increment_grams >= 0),
  rep_increment integer check (rep_increment >= 0),
  minimum_rir integer check (minimum_rir between 0 and 10),
  maximum_rir integer check (maximum_rir between 0 and 10),
  instructions text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (maximum_rir is null or minimum_rir is null or maximum_rir >= minimum_rir)
);

create table public.exercise_progress_snapshots (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  workout_exercise_id uuid references public.workout_exercises(id) on delete set null,
  catalog_exercise_id text references public.exercises(id) on delete set null,
  custom_exercise_id uuid references public.custom_exercises(id) on delete set null,
  recorded_at timestamptz not null default now(),
  top_load_grams integer check (top_load_grams >= 0),
  top_reps integer check (top_reps >= 0),
  total_volume_grams integer check (total_volume_grams >= 0),
  achieved_rir integer,
  notes text not null default '',
  check ((catalog_exercise_id is not null)::int + (custom_exercise_id is not null)::int = 1)
);

create index plan_blocks_plan_idx on public.workout_plan_blocks(workout_plan_id, block_number);
create index check_ins_user_date_idx on public.patient_check_ins(user_id, recorded_on desc);
create index warmups_plan_day_idx on public.workout_warmups(plan_day_id, position);
create index warmups_workout_idx on public.workout_warmups(workout_id, position);
create index cardio_plan_day_idx on public.workout_cardio(plan_day_id);
create index cardio_workout_idx on public.workout_cardio(workout_id);
create index progression_rules_exercise_idx on public.workout_progression_rules(plan_exercise_id);
create index progress_snapshots_user_exercise_idx on public.exercise_progress_snapshots(user_id, recorded_at desc);

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'workout_plan_blocks', 'patient_check_ins', 'workout_warmups',
    'workout_cardio', 'workout_progression_rules', 'exercise_progress_snapshots'
  ] loop
    execute format('create trigger set_updated_at before update on public.%I for each row execute function public.set_updated_at()', table_name);
  end loop;
end $$;

alter table public.workout_plan_blocks enable row level security;
alter table public.patient_check_ins enable row level security;
alter table public.workout_warmups enable row level security;
alter table public.workout_cardio enable row level security;
alter table public.workout_progression_rules enable row level security;
alter table public.exercise_progress_snapshots enable row level security;

create policy "patients and coaches manage plan blocks" on public.workout_plan_blocks for all
using (exists (
  select 1 from public.workout_plans p
  where p.id = workout_plan_id and (p.user_id = auth.uid() or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
  ))
)) with check (exists (
  select 1 from public.workout_plans p
  where p.id = workout_plan_id and (p.user_id = auth.uid() or exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
  ))
));

create policy "patients and coaches manage check ins" on public.patient_check_ins for all
using (user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp
  where cp.patient_id = patient_check_ins.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
)) with check (user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp
  where cp.patient_id = patient_check_ins.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
));

create policy "patients and coaches manage warmups" on public.workout_warmups for all
using (coalesce(
  exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = plan_day_id and (p.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'))),
  exists (select 1 from public.workouts w where w.id = workout_id and (w.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active')))
)) with check (coalesce(
  exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = plan_day_id and (p.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'))),
  exists (select 1 from public.workouts w where w.id = workout_id and (w.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active')))
));

create policy "patients and coaches manage cardio" on public.workout_cardio for all
using (coalesce(
  exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = plan_day_id and (p.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'))),
  exists (select 1 from public.workouts w where w.id = workout_id and (w.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active')))
)) with check (coalesce(
  exists (select 1 from public.workout_plan_days d join public.workout_plans p on p.id = d.workout_plan_id where d.id = plan_day_id and (p.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'))),
  exists (select 1 from public.workouts w where w.id = workout_id and (w.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = w.user_id and cp.coach_id = auth.uid() and cp.status = 'active')))
));

create policy "patients and coaches manage progression rules" on public.workout_progression_rules for all
using (exists (
  select 1 from public.workout_plan_exercises e join public.workout_plan_days d on d.id = e.workout_plan_day_id join public.workout_plans p on p.id = d.workout_plan_id
  where e.id = plan_exercise_id and (p.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'))
)) with check (exists (
  select 1 from public.workout_plan_exercises e join public.workout_plan_days d on d.id = e.workout_plan_day_id join public.workout_plans p on p.id = d.workout_plan_id
  where e.id = plan_exercise_id and (p.user_id = auth.uid() or exists (select 1 from public.coach_patients cp where cp.patient_id = p.user_id and cp.coach_id = auth.uid() and cp.status = 'active'))
));

create policy "patients and coaches manage progress snapshots" on public.exercise_progress_snapshots for all
using (user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = exercise_progress_snapshots.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
)) with check (user_id = auth.uid() or exists (
  select 1 from public.coach_patients cp where cp.patient_id = exercise_progress_snapshots.user_id and cp.coach_id = auth.uid() and cp.status = 'active'
));
