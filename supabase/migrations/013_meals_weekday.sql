alter table public.meals
  add column weekday smallint;

update public.meals
set weekday = extract(isodow from scheduled_at)::smallint
where weekday is null
  and scheduled_at is not null;

alter table public.meals
  alter column weekday set not null,
  add constraint meals_weekday_check check (weekday between 1 and 7);

create index meals_user_weekday_scheduled_at_idx
  on public.meals (user_id, weekday, scheduled_at);
