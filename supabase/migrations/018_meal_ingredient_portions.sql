alter table public.meal_ingredients
  add column grams_quantity numeric,
  add column household_portion text not null default '';

update public.meal_ingredients
set grams_quantity = case ingredient_name
  when 'Huevo entero fresco' then 100
  when 'Clara de huevo' then 100
  when 'Tortilla de maíz' then 88
  when 'Frijoles refritos sin grasa' then 100
  when 'Café negro' then 240
  when 'Bistec' then 150
  when 'Nopal cocido' then 150
  when 'Arroz cocido' then 180
  when 'Elote' then 20
  when 'Chícharo' then 20
  when 'Zanahoria' then 20
  when 'Aguacate' then 93
  when 'Sandía' then 160
  when 'Yogur griego sin grasa' then 120
  when 'Granola' then 21
  when '100 Whey Protein' then 24
  when 'Avena cruda en hojuelas' then 60
  when 'Manzana' then 100
  when 'Pez fileteado' then 120
  when 'Lechuga' then 70
  when 'Tomate cherry' then 40
  when 'Aceite de canola' then 5
  when 'Espagueti cocido' then 200
  when 'Tomate' then 10
  when 'Cebolla' then 10
  else grams_quantity
end;

update public.meal_ingredients
set household_portion = case ingredient_name
  when 'Huevo entero fresco' then '2 piezas'
  when 'Clara de huevo' then '3 piezas'
  when 'Tortilla de maíz' then '4 piezas'
  when 'Frijoles refritos sin grasa' then '½ taza'
  when 'Café negro' then '1 taza'
  when 'Nopal cocido' then '1 taza'
  when 'Arroz cocido' then '1 taza'
  when 'Elote' then '¼ taza'
  when 'Chícharo' then '0.1 taza'
  when 'Zanahoria' then '¼ taza'
  when 'Aguacate' then '1 pieza'
  when 'Sandía' then '1 taza'
  when 'Yogur griego sin grasa' then '½ taza'
  when 'Granola' then '3 cucharadas'
  when '100 Whey Protein' then '1 unidad'
  when 'Avena cruda en hojuelas' then '1½ tazas'
  when 'Manzana' then '1 pieza'
  when 'Lechuga' then '1½ tazas'
  when 'Tomate cherry' then '4 piezas'
  when 'Aceite de canola' then '1 cucharadita'
  when 'Espagueti cocido' then '1½ tazas'
  when 'Tomate' then '1 rebanada'
  when 'Cebolla' then '2 cucharadas'
  else household_portion
end;
