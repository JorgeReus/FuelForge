-- RIR is the single effort target for both plan prescriptions and logged sets.
alter table public.workout_plan_exercises
  drop column if exists target_rpe;

alter table public.workout_exercises
  drop constraint if exists workout_exercises_rpe_check,
  drop column if exists target_rpe;

alter table public.workout_sets
  drop column if exists rpe;
