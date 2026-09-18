create table public.exercises (
  id text primary key,
  name text not null,
  category text not null,
  body_part text not null,
  equipment text not null,
  target text not null,
  muscle_group text not null default '',
  secondary_muscles jsonb not null default '[]'::jsonb,
  instructions jsonb not null default '{}'::jsonb,
  instruction_steps jsonb not null default '{}'::jsonb,
  image_url text,
  gif_url text,
  attribution text not null default '© Gym visual — https://gymvisual.com/',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index exercises_category_idx on public.exercises (category);
create index exercises_equipment_idx on public.exercises (equipment);
create index exercises_target_idx on public.exercises (target);

create table public.workout_exercises (
  id uuid primary key default gen_random_uuid(),
  workout_id uuid not null references public.workouts(id) on delete cascade,
  exercise_id text not null references public.exercises(id) on delete restrict,
  order_index integer not null default 0 check (order_index >= 0),
  prescribed_sets integer not null default 3 check (prescribed_sets > 0),
  target_reps integer not null default 8 check (target_reps > 0),
  target_weight_grams integer not null default 0 check (target_weight_grams >= 0),
  rest_seconds integer not null default 90 check (rest_seconds >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workout_id, order_index)
);

alter table public.workout_sets
  add column workout_exercise_id uuid references public.workout_exercises(id) on delete cascade;

alter table public.exercises enable row level security;
alter table public.workout_exercises enable row level security;

create policy "anyone can read exercises"
  on public.exercises for select using (true);

create policy "users manage own workout exercises"
  on public.workout_exercises for all
  using (exists (select 1 from public.workouts w where w.id = workout_id and w.user_id = auth.uid()))
  with check (exists (select 1 from public.workouts w where w.id = workout_id and w.user_id = auth.uid()));

drop trigger if exists set_updated_at on public.exercises;
create trigger set_updated_at before update on public.exercises
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.workout_exercises;
create trigger set_updated_at before update on public.workout_exercises
  for each row execute function public.set_updated_at();
