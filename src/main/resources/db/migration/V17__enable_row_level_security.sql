-- Supabase serves every table in the public schema over its own REST API (the Data API), to the anon
-- and authenticated roles, with the project's anon key. This application never uses that API: it
-- connects as the role that owns the tables, and AccessGuard decides who sees what. So the API must
-- see nothing at all.
--
-- 1. Row-level security on every table in public but Flyway's history, with no policies. For the API roles that means no
--    row can be read or written. The owner of a table is not subject to its RLS (as long as it is not
--    FORCEd), so the application, which owns them, works as before.
--    ALTER TABLE needs the table's owner: if a table belongs to another role, this migration fails
--    and changes nothing, instead of leaving the application unable to read that table.
--    If the application ever connects with a role that does not own the tables (the README suggests
--    one to stop TRUNCATE), that role needs BYPASSRLS or policies of its own, or it will see empty tables.
-- 2. The privileges Supabase grants to anon and authenticated by default are taken away, on the
--    existing tables and sequences and on the ones created from now on, so a table added later
--    without RLS is still out of the API's reach. On a database without those roles (plain
--    PostgreSQL, as in the tests and CI) this step does nothing.

do $$
declare
    table_name text;
begin
    -- Not Flyway's own history table: Flyway holds it locked from another connection while this runs,
    -- so altering it here would wait forever. Step 2 keeps the API roles out of it.
    for table_name in select tablename from pg_tables
                      where schemaname = 'public' and tablename <> 'flyway_schema_history' loop
        execute format('alter table public.%I enable row level security', table_name);
    end loop;
end
$$;

do $$
declare
    api_role text;
begin
    foreach api_role in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = api_role) then
            execute format('revoke all on all tables in schema public from %I', api_role);
            execute format('revoke all on all sequences in schema public from %I', api_role);
            execute format('alter default privileges in schema public revoke all on tables from %I', api_role);
            execute format('alter default privileges in schema public revoke all on sequences from %I', api_role);
        end if;
    end loop;
end
$$;
