do $$
begin
  create type public.profile_role as enum ('patient', 'trainer');
exception when duplicate_object then null;
end $$;

alter table public.profiles
  drop constraint if exists profiles_role_check,
  alter column role drop default;

alter table public.profiles
  alter column role type public.profile_role
  using role::public.profile_role;

alter table public.profiles
  alter column role set default 'patient'::public.profile_role;
