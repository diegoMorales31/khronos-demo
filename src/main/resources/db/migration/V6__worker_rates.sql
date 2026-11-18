-- Hourly pay rates, used to work out staff costs. A rate applies from effective_from until the next
-- one starts, so a past rate is never edited: a new one is added. Amounts are in euros.
-- Deleting a worker deletes their rates.

create sequence worker_rates_seq start with 1 increment by 50;

create table worker_rates (
    id bigint not null,
    worker_id bigint not null,
    hourly_rate numeric(10, 2) not null,
    effective_from date not null,
    created_at timestamp(6) with time zone not null,
    primary key (id),
    constraint uk_worker_rates_worker_effective_from unique (worker_id, effective_from),
    constraint ck_worker_rates_hourly_rate_positive check (hourly_rate > 0),
    constraint fk_worker_rates_worker foreign key (worker_id) references workers (user_id) on delete cascade
);
