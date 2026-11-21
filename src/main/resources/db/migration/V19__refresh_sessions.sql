-- Sessions that outlive the one-hour access token: one row per signed-in device (see RefreshTokenServiceImpl).
-- The client holds a random refresh token; only its SHA-256 is stored, so a copy of this table can't be
-- used to sign in. Each use swaps the token for a new one: token_hash is the current one and previous_hash
-- the one it replaced, so a replaced token showing up again (a stolen copy) is recognised and ends the session.
--
-- Rows go with their user. Row-level security as V17 turns on for every other table.

create sequence refresh_sessions_seq start with 1 increment by 50;

create table refresh_sessions (
    id bigint not null,
    user_id bigint not null,
    token_hash varchar(64) not null,
    previous_hash varchar(64),
    rotated_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone not null,
    primary key (id),
    constraint fk_refresh_sessions_user foreign key (user_id) references users (id) on delete cascade
);

create unique index ux_refresh_sessions_token_hash on refresh_sessions (token_hash);
create index ix_refresh_sessions_previous_hash on refresh_sessions (previous_hash);
create index ix_refresh_sessions_user on refresh_sessions (user_id);

alter table refresh_sessions enable row level security;
