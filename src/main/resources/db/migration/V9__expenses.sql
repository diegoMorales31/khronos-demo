-- Expenses an owner enters by hand: what was paid, to whom, how, and the business day it counts for.
-- Amounts are euros with two decimals and always positive.
--
-- Expenses are part of the establishment's financial record, so the database refuses to delete an
-- establishment that has them, as the service does with a 409. Deleting the account that entered an
-- expense keeps the expense and that person's name and only clears the link.

create sequence expenses_seq start with 1 increment by 50;

create table expenses (
    id bigint not null,
    establishment_id bigint not null,
    date date not null,
    category varchar(16) not null,
    description varchar(150) not null,
    supplier varchar(100),
    amount numeric(12, 2) not null,
    payment_method varchar(16) not null,
    notes varchar(500),
    created_at timestamp(6) with time zone not null,
    created_by bigint,
    created_by_name varchar(511) not null,
    primary key (id),
    constraint ck_expenses_amount_positive check (amount > 0),
    constraint ck_expenses_category check (category in ('SUPPLIES', 'RENT', 'UTILITIES', 'PAYROLL', 'MAINTENANCE',
                                                        'MARKETING', 'TAXES', 'OTHER')),
    constraint ck_expenses_payment_method check (payment_method in ('CASH', 'CARD', 'TRANSFER', 'OTHER')),
    constraint fk_expenses_establishment foreign key (establishment_id) references establishments (id) on delete restrict,
    constraint fk_expenses_created_by foreign key (created_by) references users (id) on delete set null
);

create index idx_expenses_establishment_date on expenses (establishment_id, date);
