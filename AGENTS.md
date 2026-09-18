# Nutri development workflow

## Supabase database changes

Remote PostgreSQL schema changes are managed with Supabase migrations. Do not
change the remote schema manually unless the change is immediately captured in
a migration.

1. Create a migration:

   ```bash
   supabase migration new <short_change_name>
   ```

2. Edit the generated SQL file in `supabase/migrations/`.
3. Review the SQL, including constraints, indexes, RLS policies, and whether it
   is safe to run against existing data.
4. Apply pending migrations to the linked Supabase project:

   ```bash
   supabase db push
   ```

5. Verify the result in Supabase or with:

   ```bash
   supabase db pull
   ```

`supabase migration new` only creates a local migration file. `supabase db
push` applies pending migration files to the remote database.

SQLDelight migrations remain separate: they manage the on-device offline
database, while Supabase migrations manage the server database.
