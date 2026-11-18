-- Time tracking. A time entry is one clock-in and its clock-out, stored as UTC instants. Entries are
-- never deleted through the API: they are corrected or voided, and every change leaves a row in
-- time_entry_changes with who made it, when, why and the values it left.
--
-- What happens to entries when related rows are deleted:
--   * a shift: the entry stays and only loses its link (shift_id becomes null);
--   * a worker, or an establishment (which deletes its workers): the entries and their history go
--     with them. Deactivate a worker instead of deleting them to keep their records.
--   * the user who made a change: the change stays, keeps the author's name and loses the link.

create sequence time_entries_seq start with 1 increment by 50;
create sequence time_entry_changes_seq start with 1 increment by 50;

create table time_entries (
    id bigint not null,
    worker_id bigint not null,
    establishment_id bigint not null,
    shift_id bigint,
    business_date date not null,
    clock_in timestamp(6) with time zone not null,
    clock_out timestamp(6) with time zone,
    break_minutes integer not null default 0,
    source varchar(16) not null,
    edited boolean not null default false,
    voided_at timestamp(6) with time zone,
    note varchar(255),
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint ck_time_entries_source check (source in ('WORKER', 'MANAGER')),
    constraint ck_time_entries_break_minutes check (break_minutes >= 0),
    constraint ck_time_entries_clock_out_after_clock_in check (clock_out is null or clock_out > clock_in),
    constraint fk_time_entries_worker foreign key (worker_id) references workers (user_id) on delete cascade,
    constraint fk_time_entries_establishment foreign key (establishment_id) references establishments (id) on delete cascade,
    constraint fk_time_entries_shift foreign key (shift_id) references worker_shifts (id) on delete set null
);

-- A worker has at most one open entry (no clock-out, not voided), even if two clock-ins race
create unique index ux_time_entries_one_open_per_worker on time_entries (worker_id)
    where clock_out is null and voided_at is null;

create index idx_time_entries_worker_clock_in on time_entries (worker_id, clock_in);
create index idx_time_entries_establishment_business_date on time_entries (establishment_id, business_date);
create index idx_time_entries_shift on time_entries (shift_id);

create table time_entry_changes (
    id bigint not null,
    time_entry_id bigint not null,
    action varchar(16) not null,
    changed_at timestamp(6) with time zone not null,
    changed_by bigint,
    changed_by_name varchar(511) not null,
    reason varchar(255),
    clock_in timestamp(6) with time zone not null,
    clock_out timestamp(6) with time zone,
    break_minutes integer not null,
    primary key (id),
    constraint ck_time_entry_changes_action check (action in ('CREATED', 'CLOCKED_OUT', 'CORRECTED', 'VOIDED')),
    constraint fk_time_entry_changes_time_entry foreign key (time_entry_id) references time_entries (id) on delete cascade,
    constraint fk_time_entry_changes_changed_by foreign key (changed_by) references users (id) on delete set null
);

create index idx_time_entry_changes_time_entry on time_entry_changes (time_entry_id, changed_at);

-- The history is append-only. The one update allowed is the database itself clearing changed_by
-- when the author's account is deleted.
create function time_entry_changes_append_only() returns trigger
    language plpgsql
as $$
begin
    if (new.id, new.time_entry_id, new.action, new.changed_at, new.changed_by_name, new.reason,
        new.clock_in, new.clock_out, new.break_minutes)
            is distinct from
       (old.id, old.time_entry_id, old.action, old.changed_at, old.changed_by_name, old.reason,
        old.clock_in, old.clock_out, old.break_minutes)
       or new.changed_by is not null and new.changed_by is distinct from old.changed_by then
        raise exception 'time_entry_changes rows cannot be modified';
    end if;
    return new;
end;
$$;

create trigger time_entry_changes_append_only
    before update on time_entry_changes
    for each row execute function time_entry_changes_append_only();
