alter table public.meal_ingredients
  drop constraint meal_ingredients_category_check;

delete from public.meal_ingredients
where category = 'fat';

update public.meal_ingredients
set category = case
  when category = 'protein' and ingredient_name in ('Pez fileteado', 'Yogur griego sin grasa', '100 Whey Protein')
    then 'animal_protein_low_fat'
  when category = 'protein' then 'animal_protein_moderate_fat'
  when category = 'vegetable' then 'vegetable'
  when category = 'grain' then 'cereals_tubers'
  when category = 'legume' then 'legumes'
  when category = 'dairy' then 'animal_protein_low_fat'
  when category = 'supplement' then 'animal_protein_low_fat'
  when category = 'fruit' then 'vegetable'
  when category = 'beverage' then 'other'
  else 'other'
end;

alter table public.meal_ingredients
  add constraint meal_ingredients_category_check check (
    category in (
      'animal_protein_moderate_fat',
      'animal_protein_low_fat',
      'vegetable',
      'cereals_tubers',
      'legumes',
      'other'
    )
  );
