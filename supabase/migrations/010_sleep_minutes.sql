alter table public.patient_check_ins
  add column sleep_minutes integer check (sleep_minutes between 0 and 1440);

update public.patient_check_ins
set sleep_minutes = round(sleep_hours * 60)::integer
where sleep_hours is not null;

alter table public.patient_check_ins
  drop column sleep_hours;
