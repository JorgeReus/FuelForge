insert into public.exchange_foods (name, category, portion_quantity, portion_unit, household_portion)
values
  ('Frijol promedio cocido', 'legumes', 120, 'g', '⅔ taza'),
  ('Frijoles refritos sin grasa', 'legumes', 114, 'g', '⅔ taza'),
  ('Frijoles refritos enlatados, sin grasa', 'legumes', 114, 'g', '⅔ taza'),
  ('Garbanzos cocidos', 'legumes', 120, 'g', '⅔ taza'),
  ('Lentejas cocidas', 'legumes', 120, 'g', '⅔ taza'),
  ('Lenteja', 'legumes', 57, 'g', ''),
  ('Garbanzo', 'legumes', 55, 'g', ''),
  ('Soya verde, cruda', 'legumes', 80, 'g', '⅓ taza')
on conflict (name, category) do update set
  portion_quantity = excluded.portion_quantity,
  portion_unit = excluded.portion_unit,
  household_portion = excluded.household_portion,
  active = true;
