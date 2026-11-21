-- The in-app list of notifications (see NotificationServiceImpl): one row per notice a user got, written
-- in the same transaction as the change it is about, so a saved change always has its notice and an undone
-- one has none. type says what happened and data holds its details as JSON (the shifts and events, before
-- and after); the app writes the text in the reader's language. read_at stays empty until it is read.
--
-- Rows go with their user, and NotificationServiceImpl deletes the ones older than app.notifications.retention.
-- Row-level security as V17 turns on for every other table.

create sequence notifications_seq start with 1 increment by 50;

create table notifications (
    id bigint not null,
    user_id bigint not null,
    type varchar(30) not null,
    data text not null,
    created_at timestamp(6) with time zone not null,
    read_at timestamp(6) with time zone,
    primary key (id),
    constraint fk_notifications_user foreign key (user_id) references users (id) on delete cascade
);

create index ix_notifications_user_created on notifications (user_id, created_at desc, id desc);
create index ix_notifications_unread on notifications (user_id) where read_at is null;
create index ix_notifications_created on notifications (created_at);

alter table notifications enable row level security;
