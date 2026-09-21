# Supabase data

The exercise catalog comes from:

https://github.com/hasaneyldrm/exercises-dataset

Apply the schema with:

```bash
supabase db push
```

Import the dataset into `public.exercises` separately. Keep the dataset's
`attribution`, image paths, and GIF paths when importing media.

The importer downloads `data/exercises.json` on first use and upserts in
100-row batches. Dry-run it first:

```bash
go run ./tools/exercises-import --dry-run
```

For the remote import, use a service-role key only in your local shell; never
ship it in the app:

```bash
export SUPABASE_URL="https://wwcemcbyesjqoshksnfv.supabase.co"
export SUPABASE_SERVICE_ROLE_KEY="..."
go run ./tools/exercises-import
```
