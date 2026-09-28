with ingredient_data(meal_name, ingredient_name, category, quantity, unit, sort_order) as (
  values
    ('Huevo con ejotes a la mexicana', 'Huevo entero fresco', 'animal_protein_moderate_fat', 2, 'piezas', 1),
    ('Huevo con ejotes a la mexicana', 'Clara de huevo', 'animal_protein_low_fat', 3, 'piezas', 2),
    ('Huevo con ejotes a la mexicana', 'Ejotes crudos', 'vegetable', 20, 'g', 3),
    ('Huevo con ejotes a la mexicana', 'Cebolla blanca', 'vegetable', 10, 'g', 4),
    ('Huevo con ejotes a la mexicana', 'Tomate rojo', 'vegetable', 10, 'g', 5),
    ('Huevo con ejotes a la mexicana', 'Tortilla de maíz', 'cereals_tubers', 4, 'piezas', 6),
    ('Huevo con ejotes a la mexicana', 'Frijoles refritos sin grasa', 'legumes', 100, 'g', 7),
    ('Bistec con nopales y arroz con vegetales', 'Bistec', 'animal_protein_moderate_fat', 150, 'g', 1),
    ('Bistec con nopales y arroz con vegetales', 'Nopal cocido', 'vegetable', 150, 'g', 2),
    ('Bistec con nopales y arroz con vegetales', 'Arroz cocido', 'cereals_tubers', 180, 'g', 3),
    ('Bistec con nopales y arroz con vegetales', 'Elote', 'cereals_tubers', 20, 'g', 4),
    ('Bistec con nopales y arroz con vegetales', 'Chícharo', 'vegetable', 20, 'g', 5),
    ('Bistec con nopales y arroz con vegetales', 'Zanahoria', 'vegetable', 20, 'g', 6),
    ('Bistec con nopales y arroz con vegetales', 'Sandía', 'vegetable', 160, 'g', 7),
    ('Yogurt con granola y proteína', 'Yogur griego sin grasa', 'animal_protein_low_fat', 120, 'g', 1),
    ('Yogurt con granola y proteína', 'Granola', 'cereals_tubers', 21, 'g', 2),
    ('Yogurt con granola y proteína', '100 Whey Protein', 'animal_protein_low_fat', 24, 'g', 3),
    ('Yogurt con granola y proteína', 'Avena cruda en hojuelas', 'cereals_tubers', 60, 'g', 4),
    ('Yogurt con granola y proteína', 'Manzana', 'vegetable', 100, 'g', 5),
    ('Pescado en filete con espagueti y guacamole', 'Pez fileteado', 'animal_protein_low_fat', 120, 'g', 1),
    ('Pescado en filete con espagueti y guacamole', 'Lechuga', 'vegetable', 70, 'g', 2),
    ('Pescado en filete con espagueti y guacamole', 'Tomate cherry', 'vegetable', 40, 'g', 3),
    ('Pescado en filete con espagueti y guacamole', 'Espagueti cocido', 'cereals_tubers', 200, 'g', 4),
    ('Pescado en filete con espagueti y guacamole', 'Tomate', 'vegetable', 10, 'g', 5),
    ('Pescado en filete con espagueti y guacamole', 'Cebolla', 'vegetable', 10, 'g', 6),
    ('Pescado en filete con espagueti y guacamole', 'Sandía', 'vegetable', 160, 'g', 7)
)
insert into public.meal_ingredients (meal_id, ingredient_name, category, quantity, unit, sort_order)
select m.id, d.ingredient_name, d.category, d.quantity, d.unit, d.sort_order
from ingredient_data d
join public.meals m
  on m.user_id = '1cb8d7dc-7d47-4337-8a49-c033715efb05'
 and m.name = d.meal_name
where not exists (
  select 1 from public.meal_ingredients x
  where x.meal_id = m.id and x.ingredient_name = d.ingredient_name
);
