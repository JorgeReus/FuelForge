alter table public.patient_check_ins
  add column height_millimeters integer check (height_millimeters > 0),
  add column body_fat_percentage numeric check (body_fat_percentage between 0 and 100),
  add column body_water_grams integer check (body_water_grams > 0),
  add column bone_mass_grams integer check (bone_mass_grams > 0),
  add column lean_body_mass_grams integer check (lean_body_mass_grams > 0),
  add column basal_metabolic_rate_kcal_per_day integer check (basal_metabolic_rate_kcal_per_day > 0);
