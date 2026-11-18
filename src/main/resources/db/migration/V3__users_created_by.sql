-- Who created each account. An owner may only link a worker account they created themselves.
-- Accounts that existed before this column have no creator, so only a platform admin can link them.
-- Deleting the creator keeps the accounts they created and just forgets who that was.

alter table if exists users
    add column created_by bigint;

alter table if exists users
    add constraint fk_users_created_by foreign key (created_by) references users (id) on delete set null;

create index idx_users_created_by on users (created_by);
