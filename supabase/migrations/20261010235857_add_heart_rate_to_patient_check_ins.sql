alter table public.patient_check_ins
  add column heart_rate_bpm integer check (heart_rate_bpm > 0);
