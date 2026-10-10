create table public.coach_check_in_schedules (
  coach_id uuid primary key references public.profiles(id) on delete cascade,
  name text not null default 'Default check-in schedule',
  frequency text not null check (frequency in ('daily', 'weekly', 'every_n_weeks')),
  interval_weeks integer not null default 1 check (interval_weeks > 0),
  weekday integer check (weekday between 1 and 7),
  local_time time not null,
  timezone text not null,
  anchor_date date not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    (frequency = 'daily' and weekday is null)
    or (frequency in ('weekly', 'every_n_weeks') and weekday is not null)
  ),
  check (frequency <> 'every_n_weeks' or interval_weeks >= 1)
);

create table public.patient_check_in_schedule_settings (
  coach_id uuid not null references public.coach_check_in_schedules(coach_id) on delete cascade,
  patient_id uuid not null references public.profiles(id) on delete cascade,
  enabled boolean not null default true,
  frequency_override text check (frequency_override is null or frequency_override in ('daily', 'weekly', 'every_n_weeks')),
  interval_weeks_override integer check (interval_weeks_override is null or interval_weeks_override > 0),
  weekday_override integer check (weekday_override is null or weekday_override between 1 and 7),
  local_time_override time,
  timezone_override text,
  anchor_date_override date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (coach_id, patient_id),
  check (frequency_override is null or frequency_override <> 'daily' or weekday_override is null),
  check (frequency_override is null or frequency_override in ('weekly', 'every_n_weeks') or weekday_override is null),
  check (frequency_override <> 'every_n_weeks' or interval_weeks_override is null or interval_weeks_override >= 1)
);

create index patient_check_in_schedule_settings_patient_idx
  on public.patient_check_in_schedule_settings(patient_id);

create trigger set_updated_at
before update on public.coach_check_in_schedules
for each row execute function public.set_updated_at();

create trigger set_updated_at
before update on public.patient_check_in_schedule_settings
for each row execute function public.set_updated_at();

alter table public.coach_check_in_schedules enable row level security;
alter table public.patient_check_in_schedule_settings enable row level security;

create policy "coaches manage own check-in schedule"
  on public.coach_check_in_schedules for all
  using (coach_id = auth.uid())
  with check (coach_id = auth.uid());

create policy "patients read coach check-in schedule"
  on public.coach_check_in_schedules for select
  using (exists (
    select 1 from public.coach_patients cp
    where cp.coach_id = coach_check_in_schedules.coach_id
      and cp.patient_id = auth.uid()
      and cp.status = 'active'
  ));

create policy "coaches manage patient check-in settings"
  on public.patient_check_in_schedule_settings for all
  using (exists (
    select 1 from public.coach_patients cp
    where cp.coach_id = auth.uid()
      and cp.patient_id = patient_check_in_schedule_settings.patient_id
      and cp.status = 'active'
  ))
  with check (exists (
    select 1 from public.coach_patients cp
    where cp.coach_id = auth.uid()
      and cp.patient_id = patient_check_in_schedule_settings.patient_id
      and cp.status = 'active'
  ));

create policy "patients read own check-in settings"
  on public.patient_check_in_schedule_settings for select
  using (patient_id = auth.uid());
