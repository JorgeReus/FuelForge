create table public.custom_exercises (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  category text not null default '',
  body_part text not null default '',
  equipment text not null default '',
  target text not null default '',
  muscle_group text not null default '',
  secondary_muscles jsonb not null default '[]'::jsonb,
  instructions jsonb not null default '{}'::jsonb,
  instruction_steps jsonb not null default '{}'::jsonb,
  image_path text,
  gif_path text,
  notes text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.workout_plan_exercises
  add constraint workout_plan_exercises_catalog_fk
    foreign key (catalog_exercise_id) references public.exercises(id) on delete restrict,
  add constraint workout_plan_exercises_custom_fk
    foreign key (custom_exercise_id) references public.custom_exercises(id) on delete restrict;

alter table public.workout_exercises
  add constraint workout_exercises_custom_fk
    foreign key (custom_exercise_id) references public.custom_exercises(id) on delete restrict;

create index custom_exercises_owner_idx on public.custom_exercises(owner_id);

drop trigger if exists set_updated_at on public.custom_exercises;
create trigger set_updated_at before update on public.custom_exercises
  for each row execute function public.set_updated_at();

alter table public.custom_exercises enable row level security;

create policy "owners and coaches read custom exercises"
  on public.custom_exercises for select using (
    owner_id = auth.uid()
    or exists (
      select 1 from public.coach_patients cp
      where cp.patient_id = custom_exercises.owner_id
        and cp.coach_id = auth.uid()
        and cp.status = 'active'
    )
  );

create policy "owners manage custom exercises"
  on public.custom_exercises for all
  using (owner_id = auth.uid())
  with check (owner_id = auth.uid());

insert into storage.buckets (id, name, public)
values ('exercise-media', 'exercise-media', false)
on conflict (id) do nothing;

create policy "users read own exercise media"
  on storage.objects for select using (
    bucket_id = 'exercise-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "users upload own exercise media"
  on storage.objects for insert with check (
    bucket_id = 'exercise-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "users update own exercise media"
  on storage.objects for update using (
    bucket_id = 'exercise-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  ) with check (
    bucket_id = 'exercise-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "users delete own exercise media"
  on storage.objects for delete using (
    bucket_id = 'exercise-media'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
