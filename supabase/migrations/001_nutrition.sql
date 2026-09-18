create extension if not exists pgcrypto;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  role text not null default 'patient' check (role in ('patient', 'trainer')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.daily_targets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  day date not null,
  calories integer not null check (calories > 0),
  protein_grams integer not null default 0 check (protein_grams >= 0),
  carbs_grams integer not null default 0 check (carbs_grams >= 0),
  fats_grams integer not null default 0 check (fats_grams >= 0),
  water_ml integer not null default 0 check (water_ml >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, day)
);

create table public.meals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  target_id uuid references public.daily_targets(id) on delete set null,
  name text not null,
  description text not null default '',
  meal_type text not null check (meal_type in ('breakfast', 'lunch', 'snack', 'pre_workout', 'post_workout', 'dinner')),
  scheduled_at timestamptz,
  logged_at timestamptz,
  calories integer not null default 0 check (calories >= 0),
  protein_grams integer not null default 0 check (protein_grams >= 0),
  carbs_grams integer not null default 0 check (carbs_grams >= 0),
  fats_grams integer not null default 0 check (fats_grams >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.hydration_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  amount_ml integer not null check (amount_ml > 0),
  consumed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.workouts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  description text not null default '',
  scheduled_at timestamptz,
  started_at timestamptz,
  completed_at timestamptz,
  estimated_calories integer not null default 0 check (estimated_calories >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.workout_sets (
  id uuid primary key default gen_random_uuid(),
  workout_id uuid not null references public.workouts(id) on delete cascade,
  exercise_name text not null,
  set_number integer not null check (set_number > 0),
  weight_grams integer not null default 0 check (weight_grams >= 0),
  reps integer not null default 0 check (reps >= 0),
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (workout_id, set_number)
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

do $$
declare table_name text;
begin
  foreach table_name in array array['profiles', 'daily_targets', 'meals', 'hydration_logs', 'workouts', 'workout_sets'] loop
    execute format('drop trigger if exists set_updated_at on public.%I', table_name);
    execute format('create trigger set_updated_at before update on public.%I for each row execute function public.set_updated_at()', table_name);
  end loop;
end $$;

alter table public.profiles enable row level security;
alter table public.daily_targets enable row level security;
alter table public.meals enable row level security;
alter table public.hydration_logs enable row level security;
alter table public.workouts enable row level security;
alter table public.workout_sets enable row level security;

create policy "users manage own profile" on public.profiles for all using (auth.uid() = id) with check (auth.uid() = id);
create policy "users manage own targets" on public.daily_targets for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "users manage own meals" on public.meals for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "users manage own hydration" on public.hydration_logs for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "users manage own workouts" on public.workouts for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "users manage own sets" on public.workout_sets for all
  using (exists (select 1 from public.workouts w where w.id = workout_id and w.user_id = auth.uid()))
  with check (exists (select 1 from public.workouts w where w.id = workout_id and w.user_id = auth.uid()));
