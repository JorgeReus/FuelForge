create table public.meal_ingredients (
  id uuid primary key default gen_random_uuid(),
  meal_id uuid not null references public.meals(id) on delete cascade,
  ingredient_name text not null,
  category text not null check (category in ('protein', 'grain', 'fruit', 'vegetable', 'legume', 'dairy', 'fat', 'supplement', 'beverage', 'other')),
  quantity numeric not null check (quantity > 0),
  unit text not null,
  sort_order smallint not null default 0,
  created_at timestamptz not null default now()
);

create index meal_ingredients_meal_id_sort_idx
  on public.meal_ingredients (meal_id, sort_order, ingredient_name);

alter table public.meal_ingredients enable row level security;

create policy "users manage own meal ingredients"
  on public.meal_ingredients
  for all
  using (
    exists (
      select 1
      from public.meals
      where meals.id = meal_ingredients.meal_id
        and meals.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1
      from public.meals
      where meals.id = meal_ingredients.meal_id
        and meals.user_id = auth.uid()
    )
  );
