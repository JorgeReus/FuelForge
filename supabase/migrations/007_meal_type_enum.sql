do $$
begin
  create type public.meal_type as enum (
    'breakfast',
    'lunch',
    'snack',
    'pre_workout',
    'post_workout',
    'dinner'
  );
exception when duplicate_object then null;
end $$;

alter table public.meals
  drop constraint if exists meals_meal_type_check;

alter table public.meals
  alter column meal_type type public.meal_type
  using meal_type::public.meal_type;
