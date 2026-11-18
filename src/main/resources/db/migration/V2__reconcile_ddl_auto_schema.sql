-- A database created by ddl-auto is baselined at V1 without running it. These objects
-- were added to the entities last (email_logs on 2026-11-11, credentials_changed_at on
-- 2026-11-17), so a database that has not run that code yet may be missing them.
-- On a database that already matches V1 every statement here does nothing.

alter table if exists users
    add column if not exists credentials_changed_at timestamp(6) with time zone;

create sequence if not exists email_logs_seq start with 1 increment by 50;

create table if not exists email_logs (
    id bigint not null,
    created_at timestamp(6) with time zone not null,
    error_message varchar(1000),
    recipient varchar(255) not null,
    resend_id varchar(255),
    status varchar(255) not null check (status in ('SENT', 'FAILED')),
    subject varchar(255) not null,
    primary key (id)
);
