update public.meal_ingredients
set grams_quantity = case ingredient_name
  when 'Ejotes crudos' then 20
  when 'Cebolla blanca' then 10
  when 'Tomate rojo' then 10
  else grams_quantity
end
where ingredient_name in ('Ejotes crudos', 'Cebolla blanca', 'Tomate rojo');
