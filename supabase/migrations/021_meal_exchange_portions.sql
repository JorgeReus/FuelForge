alter table public.exchange_foods
  add constraint exchange_foods_name_category_key unique (name, category);

insert into public.exchange_foods (name, category, portion_quantity, portion_unit, household_portion)
values
  ('Nopales cocidos', 'vegetable', 91, 'g', '⅔ taza'),
  ('Ejotes crudos', 'vegetable', 38, 'g', '⅓ taza'),
  ('Elote amarillo cocido', 'vegetable', 184, 'g', '1 taza'),
  ('Chícharos verdes crudos', 'vegetable', 18, 'g', '0.1 taza'),
  ('Zanahoria', 'vegetable', 24, 'g', '⅓ taza'),
  ('Cebolla blanca', 'vegetable', 32, 'g', ''),
  ('Tomate rojo', 'vegetable', 62, 'g', '⅔ pieza'),
  ('Lechuga', 'vegetable', 82, 'g', '1¾ tazas'),
  ('Tomate cherry', 'vegetable', 63, 'g', ''),
  ('Arroz blanco cocido', 'cereals_tubers', 200, 'g', '1 taza'),
  ('Avena cruda en hojuelas', 'cereals_tubers', 80, 'g', '2 tazas'),
  ('Espaguetti cocido', 'cereals_tubers', 188, 'g', '1⅓ tazas'),
  ('Tortilla de maíz', 'cereals_tubers', 120, 'g', '4 piezas')
on conflict (name, category) do update set
  portion_quantity = excluded.portion_quantity,
  portion_unit = excluded.portion_unit,
  household_portion = excluded.household_portion,
  active = true;
