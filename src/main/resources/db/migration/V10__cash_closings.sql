-- Daily cash closings. The manager enters the day's takings by payment method, the cash float and the
-- cash counted at the end; the API works out the cash expected in the till and the difference. A closing
-- is a DRAFT until it is closed. Closing it stores the figures it was closed with, so they never change
-- afterwards; reopening it (with a reason) clears them. Amounts are euros with two decimals.
--
-- Every creation, edit, close and reopen leaves a row in cash_closing_changes with who did it, when, why
-- and the figures right after. Those rows are never updated.
--
-- Closings and their history are part of the establishment's financial record: the database refuses to
-- delete an establishment that has closings, or a closing that has history, as the service does with a
-- 409. Deleting the account that closed a day or made a change keeps that person's name.

create sequence cash_closings_seq start with 1 increment by 50;
create sequence cash_closing_changes_seq start with 1 increment by 50;

create table cash_closings (
    id bigint not null,
    establishment_id bigint not null,
    business_date date not null,
    status varchar(16) not null,
    opening_float numeric(12, 2) not null,
    cash_sales numeric(12, 2) not null,
    card_sales numeric(12, 2) not null,
    other_sales numeric(12, 2) not null,
    cash_withdrawals numeric(12, 2) not null,
    counted_cash numeric(12, 2) not null,
    tips numeric(12, 2),
    notes varchar(500),
    -- The figures the day was closed with; null while it is a draft, whose figures follow its expenses
    cash_expenses numeric(12, 2),
    total_sales numeric(12, 2),
    expected_cash numeric(12, 2),
    difference numeric(12, 2),
    difference_reason varchar(255),
    closed_at timestamp(6) with time zone,
    closed_by bigint,
    closed_by_name varchar(511),
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    primary key (id),
    -- One closing per establishment and business day, even if two requests race
    constraint uk_cash_closings_establishment_business_date unique (establishment_id, business_date),
    constraint ck_cash_closings_status check (status in ('DRAFT', 'CLOSED')),
    constraint ck_cash_closings_amounts check (opening_float >= 0 and cash_sales >= 0 and card_sales >= 0
        and other_sales >= 0 and cash_withdrawals >= 0 and counted_cash >= 0 and (tips is null or tips >= 0)),
    constraint ck_cash_closings_closed_figures check (
        (status = 'DRAFT' and cash_expenses is null and total_sales is null and expected_cash is null
            and difference is null and closed_at is null and closed_by_name is null)
        or (status = 'CLOSED' and cash_expenses is not null and total_sales is not null
            and expected_cash is not null and difference is not null and closed_at is not null
            and closed_by_name is not null)),
    constraint fk_cash_closings_establishment foreign key (establishment_id) references establishments (id) on delete restrict,
    constraint fk_cash_closings_closed_by foreign key (closed_by) references users (id) on delete set null
);

create table cash_closing_changes (
    id bigint not null,
    cash_closing_id bigint not null,
    action varchar(16) not null,
    changed_at timestamp(6) with time zone not null,
    changed_by bigint,
    changed_by_name varchar(511) not null,
    reason varchar(255),
    -- The closing's figures right after the change
    opening_float numeric(12, 2) not null,
    cash_sales numeric(12, 2) not null,
    card_sales numeric(12, 2) not null,
    other_sales numeric(12, 2) not null,
    cash_withdrawals numeric(12, 2) not null,
    counted_cash numeric(12, 2) not null,
    tips numeric(12, 2),
    cash_expenses numeric(12, 2) not null,
    total_sales numeric(12, 2) not null,
    expected_cash numeric(12, 2) not null,
    difference numeric(12, 2) not null,
    primary key (id),
    constraint ck_cash_closing_changes_action check (action in ('CREATED', 'UPDATED', 'CLOSED', 'REOPENED')),
    constraint fk_cash_closing_changes_cash_closing foreign key (cash_closing_id) references cash_closings (id) on delete restrict,
    constraint fk_cash_closing_changes_changed_by foreign key (changed_by) references users (id) on delete set null
);

create index idx_cash_closing_changes_cash_closing on cash_closing_changes (cash_closing_id, changed_at);

-- The history is append-only, as for time entries (V7). The one update allowed is the database itself
-- clearing changed_by when the author's account is deleted.
create function cash_closing_changes_append_only() returns trigger
    language plpgsql
as $$
begin
    if (new.id, new.cash_closing_id, new.action, new.changed_at, new.changed_by_name, new.reason,
        new.opening_float, new.cash_sales, new.card_sales, new.other_sales, new.cash_withdrawals,
        new.counted_cash, new.tips, new.cash_expenses, new.total_sales, new.expected_cash, new.difference)
            is distinct from
       (old.id, old.cash_closing_id, old.action, old.changed_at, old.changed_by_name, old.reason,
        old.opening_float, old.cash_sales, old.card_sales, old.other_sales, old.cash_withdrawals,
        old.counted_cash, old.tips, old.cash_expenses, old.total_sales, old.expected_cash, old.difference)
       or new.changed_by is not null and new.changed_by is distinct from old.changed_by then
        raise exception 'cash_closing_changes rows cannot be modified';
    end if;
    return new;
end;
$$;

create trigger cash_closing_changes_append_only
    before update on cash_closing_changes
    for each row execute function cash_closing_changes_append_only();
