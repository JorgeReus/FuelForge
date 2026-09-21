do $$
begin
  create type public.coach_patient_status as enum ('active', 'inactive');
exception when duplicate_object then null;
end $$;

create temporary table _coach_status_policies as
select schemaname, tablename, policyname, permissive, cmd,
       array_to_string(roles, ', ') as roles, qual, with_check
from pg_policies
where schemaname = 'public'
  and (coalesce(qual, '') || coalesce(with_check, '')) like '%coach_patients%'
  and (coalesce(qual, '') || coalesce(with_check, '')) like '%status%';

do $$
declare p record;
begin
  for p in select * from _coach_status_policies loop
    execute format('drop policy if exists %I on %I.%I', p.policyname, p.schemaname, p.tablename);
  end loop;
end $$;

drop index if exists public.coach_patients_patient_active_idx;

alter table public.coach_patients
  drop constraint if exists coach_patients_status_check;

update public.coach_patients
set status = 'inactive'
where status = 'archived';

alter table public.coach_patients
  alter column status drop default;

alter table public.coach_patients
  alter column status type public.coach_patient_status
  using status::public.coach_patient_status;

alter table public.coach_patients
  alter column status set default 'active'::public.coach_patient_status;

create index coach_patients_patient_active_idx
  on public.coach_patients(patient_id)
  where status = 'active'::public.coach_patient_status;

do $$
declare p record;
begin
  for p in select * from _coach_status_policies loop
    execute format(
      'create policy %I on %I.%I as %s for %s to %s using (%s)%s',
      p.policyname,
      p.schemaname,
      p.tablename,
      case when p.permissive = 'PERMISSIVE' then 'PERMISSIVE' else 'RESTRICTIVE' end,
      p.cmd,
      p.roles,
      replace(
        p.qual,
        quote_literal('active') || '::text',
        quote_literal('active') || '::public.coach_patient_status'
      ),
      case when p.with_check is null then '' else format(
        ' with check (%s)',
        replace(
          p.with_check,
          quote_literal('active') || '::text',
          quote_literal('active') || '::public.coach_patient_status'
        )
      ) end
    );
  end loop;
end $$;
