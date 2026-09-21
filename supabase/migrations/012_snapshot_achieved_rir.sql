-- A snapshot records the RIR actually achieved in its representative set.
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public'
      and table_name = 'exercise_progress_snapshots'
      and column_name = 'intensity'
  ) then
    alter table public.exercise_progress_snapshots
      rename column intensity to achieved_rir;
  end if;
end $$;

alter table public.exercise_progress_snapshots
  add constraint exercise_progress_snapshots_achieved_rir_check
  check (achieved_rir between 0 and 10);
