-- Purchase orders to suppliers. An order goes DRAFT -> ORDERED -> RECEIVED, or is CANCELLED from DRAFT or
-- ORDERED. Receiving it records a PURCHASE stock movement for each line that came in
-- (stock_movements.purchase_order_id) and, if asked, an expense (expense_id).
--
-- Orders are part of the inventory record. The database refuses to delete an establishment that has
-- orders, a supplier with orders, a product on an order line, or an order with movements, as the service
-- answers with a 409. Only a draft can be deleted, and its lines go with it. Deleting the expense created
-- when an order was received keeps the order and its movements and only clears expense_id. Deleting the
-- account that created an order keeps that person's name.
--
-- Adding purchase_order_id to stock_movements doesn't touch existing rows, and their append-only trigger
-- (V13) compares whole rows, so it covers the new column too.

create sequence purchase_orders_seq start with 1 increment by 50;
create sequence purchase_order_lines_seq start with 1 increment by 50;

create table purchase_orders (
    id bigint not null,
    establishment_id bigint not null,
    supplier_id bigint not null,
    status varchar(16) not null,
    -- The day it was ordered, its expected delivery and the day it was received; business days
    order_date date,
    expected_date date,
    received_date date,
    notes varchar(500),
    expense_id bigint,
    created_at timestamp(6) with time zone not null,
    created_by bigint,
    created_by_name varchar(511) not null,
    primary key (id),
    constraint ck_purchase_orders_status check (status in ('DRAFT', 'ORDERED', 'RECEIVED', 'CANCELLED')),
    constraint ck_purchase_orders_order_date check (
        (status = 'DRAFT' and order_date is null)
        or (status in ('ORDERED', 'RECEIVED') and order_date is not null)
        or status = 'CANCELLED'),
    constraint ck_purchase_orders_received_date check ((status = 'RECEIVED') = (received_date is not null)),
    constraint ck_purchase_orders_expense check (expense_id is null or status = 'RECEIVED'),
    constraint fk_purchase_orders_establishment foreign key (establishment_id) references establishments (id) on delete restrict,
    constraint fk_purchase_orders_supplier foreign key (supplier_id) references suppliers (id) on delete restrict,
    constraint fk_purchase_orders_expense foreign key (expense_id) references expenses (id) on delete set null,
    constraint fk_purchase_orders_created_by foreign key (created_by) references users (id) on delete set null
);

create index idx_purchase_orders_establishment_created_at on purchase_orders (establishment_id, created_at);
create index idx_purchase_orders_supplier on purchase_orders (supplier_id);
-- An expense records at most one order
create unique index ux_purchase_orders_expense on purchase_orders (expense_id);

create table purchase_order_lines (
    id bigint not null,
    purchase_order_id bigint not null,
    product_id bigint not null,
    quantity numeric(12, 3) not null,
    unit_cost numeric(12, 2),
    -- Null until the order is received
    received_quantity numeric(12, 3),
    primary key (id),
    constraint uk_purchase_order_lines_product unique (purchase_order_id, product_id),
    constraint ck_purchase_order_lines_quantity check (quantity > 0),
    constraint ck_purchase_order_lines_unit_cost check (unit_cost is null or unit_cost >= 0),
    constraint ck_purchase_order_lines_received_quantity check (received_quantity is null or received_quantity >= 0),
    constraint fk_purchase_order_lines_purchase_order foreign key (purchase_order_id) references purchase_orders (id) on delete cascade,
    constraint fk_purchase_order_lines_product foreign key (product_id) references products (id) on delete restrict
);

create index idx_purchase_order_lines_product on purchase_order_lines (product_id);

alter table stock_movements add column purchase_order_id bigint;
alter table stock_movements add constraint fk_stock_movements_purchase_order
    foreign key (purchase_order_id) references purchase_orders (id) on delete restrict;
create index idx_stock_movements_purchase_order on stock_movements (purchase_order_id);
