create table public.exchange_foods (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  category text not null check (category in (
    'animal_protein_moderate_fat',
    'animal_protein_very_low_fat',
    'vegetable',
    'cereals_tubers',
    'legumes'
  )),
  portion_quantity numeric not null check (portion_quantity > 0),
  portion_unit text not null,
  household_portion text not null default '',
  calories numeric not null default 0,
  protein_grams numeric not null default 0,
  carbs_grams numeric not null default 0,
  fats_grams numeric not null default 0,
  fiber_grams numeric not null default 0,
  active boolean not null default true,
  unique (id, category)
);

alter table public.meal_ingredients
  add column exchange_food_id uuid,
  add column exchange_count numeric check (exchange_count is null or exchange_count > 0);

alter table public.meal_ingredients
  add constraint meal_ingredients_exchange_category_fk
  foreign key (exchange_food_id, category)
  references public.exchange_foods (id, category);

create index exchange_foods_category_idx on public.exchange_foods (category) where active;

alter table public.exchange_foods enable row level security;
create policy "users read exchange foods"
  on public.exchange_foods for select
  using (auth.uid() is not null);
