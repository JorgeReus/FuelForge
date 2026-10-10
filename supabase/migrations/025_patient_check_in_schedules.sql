create table public.patient_check_in_schedules (
  patient_id uuid primary key references public.profiles(id) on delete cascade,
  name text not null default 'Daily check-in',
  frequency text not null default 'daily' check (frequency in ('daily', 'weekly', 'every_n_weeks')),
  interval_weeks integer not null default 1 check (interval_weeks > 0),
  weekday integer check (weekday between 1 and 7),
  local_time time not null default '09:00:00',
  timezone text not null default 'UTC',
  anchor_date date not null default current_date,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    (frequency = 'daily' and weekday is null)
    or (frequency in ('weekly', 'every_n_weeks') and weekday is not null)
  )
);

create trigger set_updated_at
before update on public.patient_check_in_schedules
for each row execute function public.set_updated_at();

alter table public.patient_check_in_schedules enable row level security;

create policy "patients manage own check-in schedule"
  on public.patient_check_in_schedules for all
  using (patient_id = auth.uid())
  with check (patient_id = auth.uid());

create policy "coaches read patient check-in schedule"
  on public.patient_check_in_schedules for select
  using (exists (
    select 1 from public.coach_patients cp
    where cp.patient_id = patient_check_in_schedules.patient_id
      and cp.coach_id = auth.uid()
      and cp.status = 'active'
  ));
