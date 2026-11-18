-- Baseline: the schema Hibernate generated with ddl-auto before Flyway took over.
-- A database that already has these tables skips this script, because
-- spring.flyway.baseline-on-migrate marks version 1 as applied there.

create sequence email_logs_seq start with 1 increment by 50;
create sequence establishments_seq start with 1 increment by 50;
create sequence events_seq start with 1 increment by 50;
create sequence users_seq start with 1 increment by 50;
create sequence worker_events_seq start with 1 increment by 50;
create sequence worker_shifts_seq start with 1 increment by 50;
create sequence zones_seq start with 1 increment by 50;

create table email_logs (
    id bigint not null,
    created_at timestamp(6) with time zone not null,
    error_message varchar(1000),
    recipient varchar(255) not null,
    resend_id varchar(255),
    status varchar(255) not null check (status in ('SENT', 'FAILED')),
    subject varchar(255) not null,
    primary key (id)
);

create table establishments (
    id bigint not null,
    establishment_address varchar(255),
    establishment_name varchar(255),
    owner_id bigint not null,
    primary key (id)
);

create table events (
    id bigint not null,
    date_time timestamp(6),
    description varchar(255),
    people_number integer,
    title varchar(255),
    zone_id bigint not null,
    primary key (id)
);

create table users (
    id bigint not null,
    credentials_changed_at timestamp(6) with time zone,
    email varchar(255) not null,
    first_name varchar(255),
    last_name varchar(255),
    password varchar(255) not null,
    phone_number varchar(255),
    role varchar(255) not null check (role in ('PLATFORM_ADMIN', 'OWNER', 'WORKER')),
    username varchar(255) not null,
    primary key (id)
);

create table worker_events (
    id bigint not null,
    event_id bigint not null,
    worker_id bigint not null,
    primary key (id)
);

create table worker_shifts (
    id bigint not null,
    date date,
    end_time time(0),
    start_time time(0),
    worker_id bigint not null,
    zone_id bigint not null,
    primary key (id)
);

create table workers (
    user_id bigint not null,
    is_active boolean not null,
    establishment_id bigint not null,
    primary key (user_id)
);

create table zones (
    id bigint not null,
    name varchar(255) not null,
    establishment_id bigint not null,
    primary key (id)
);

alter table if exists establishments
    add constraint ukmux8y38ydcffsf32b8dmx0idl unique (establishment_address);

alter table if exists users
    add constraint uk6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table if exists users
    add constraint uk9q63snka3mdh91as4io72espi unique (phone_number);

alter table if exists users
    add constraint ukr43af9ap4edm43mmtq01oddj6 unique (username);

alter table if exists worker_events
    add constraint ukpfbclwndfgab1ybo4xj53tsga unique (worker_id, event_id);

alter table if exists zones
    add constraint ukd59bibowpnmtvjrpdajgxc1hn unique (name, establishment_id);

alter table if exists establishments
    add constraint fkj0ewetc8gapsb8a3bssvw0lfh foreign key (owner_id) references users;

alter table if exists events
    add constraint fkari3169l1m1hn8fbko11tl7x8 foreign key (zone_id) references zones;

alter table if exists worker_events
    add constraint fkocnlmcg50u208c6ygdn77w8sh foreign key (event_id) references events;

alter table if exists worker_events
    add constraint fk3bltdsepcpgeegufnfq47rruf foreign key (worker_id) references workers;

alter table if exists worker_shifts
    add constraint fkmwv4slrnqlms04jq9b6lmguj1 foreign key (worker_id) references workers;

alter table if exists worker_shifts
    add constraint fkkavbpth93ximummm4y2wvfxb1 foreign key (zone_id) references zones;

alter table if exists workers
    add constraint fkhpf71pl8yqd09682wtjpkco1s foreign key (establishment_id) references establishments;

alter table if exists workers
    add constraint fkt09tks6703k4e3idha0k7jtwm foreign key (user_id) references users;

alter table if exists zones
    add constraint fkksckkvm25lwgw4nor7bl7yns0 foreign key (establishment_id) references establishments;
