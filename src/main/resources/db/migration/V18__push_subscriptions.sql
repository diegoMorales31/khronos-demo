-- Devices that receive push notifications: one row per browser or installed app a worker turned
-- notifications on in (see PushServiceImpl). The endpoint is the push service's URL for that device and
-- p256dh and auth are the keys the message is encrypted with; all three come from the browser.
--
-- An endpoint belongs to one device, so it is unique: when someone else signs in on the same device and
-- turns notifications on, the row moves to them. Rows go with their user.
--
-- Row-level security as V17 turns on for every other table, so Supabase's Data API sees nothing here either.

create sequence push_subscriptions_seq start with 1 increment by 50;

create table push_subscriptions (
    id bigint not null,
    user_id bigint not null,
    endpoint varchar(1000) not null,
    p256dh varchar(100) not null,
    auth varchar(50) not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_push_subscriptions_user foreign key (user_id) references users (id) on delete cascade
);

create unique index ux_push_subscriptions_endpoint on push_subscriptions (endpoint);
create index ix_push_subscriptions_user on push_subscriptions (user_id);

alter table push_subscriptions enable row level security;
