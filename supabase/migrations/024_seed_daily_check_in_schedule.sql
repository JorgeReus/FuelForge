insert into public.coach_check_in_schedules (
  coach_id,
  name,
  frequency,
  interval_weeks,
  weekday,
  local_time,
  timezone,
  anchor_date
)
select
  p.id,
  'Daily check-in (test)',
  'daily',
  1,
  null,
  '09:00:00'::time,
  'UTC',
  current_date
from public.profiles p
where p.role = 'trainer'
on conflict (coach_id) do update set
  name = excluded.name,
  frequency = excluded.frequency,
  interval_weeks = excluded.interval_weeks,
  weekday = excluded.weekday,
  local_time = excluded.local_time,
  timezone = excluded.timezone,
  anchor_date = excluded.anchor_date;
