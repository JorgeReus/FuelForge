update public.meal_ingredients
set household_portion = case ingredient_name
  when 'Ejotes crudos' then '⅕ taza'
  when 'Tomate rojo' then '0.1 pieza'
  when 'Pez fileteado' then '1.1 piezas'
  else household_portion
end
where ingredient_name in ('Ejotes crudos', 'Tomate rojo', 'Pez fileteado');
