-- The plan of each owner: BASIC (up to 25 active workers), PLUS (up to 100, and the cash) or ULTRA
-- (no worker limit, the cash and the inventory). Only owners have a plan, and every owner has one.
-- Owners created before plans existed could already use everything, so they start on ULTRA and lose
-- nothing; an admin can move them to another plan afterwards.

alter table if exists users
    add column plan varchar(20);

update users set plan = 'ULTRA' where role = 'OWNER';

alter table if exists users
    add constraint ck_users_plan check (plan in ('BASIC', 'PLUS', 'ULTRA'));

alter table if exists users
    add constraint ck_users_plan_only_owners check ((role = 'OWNER') = (plan is not null));
