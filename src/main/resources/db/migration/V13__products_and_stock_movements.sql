-- The products an establishment keeps in stock, and the stock movements that make up that stock.
--
-- A product's stock is not a column anyone can edit: it is the sum of its movements. A movement is a
-- purchase (in), waste or consumption (out), a manual adjustment with a sign, or the difference a
-- physical count found. Movements are only ever added: a mistake is corrected with a new movement, and a
-- trigger rejects updates and deletes, so they can't be changed outside the API either.
--
-- Quantities are numeric(12, 3), so half a bottle can be counted; money is numeric(12, 2), in euros.
-- Each movement keeps the unit cost it is valued at and the product's stock right after it (stock_after),
-- which the service works out while it holds the product's row lock: two movements of one product never
-- start from the same stock.
--
-- A product's name is unique within its establishment whatever its case (unique index on lower(name)).
-- Products go with their establishment while they have no movements. A product with movements, or an
-- establishment with movements, can't be deleted (ON DELETE RESTRICT), as the service answers with a
-- 409. Deleting a supplier clears it from its products. Deleting the account that recorded a movement
-- keeps the movement with that person's name.

create sequence products_seq start with 1 increment by 50;
create sequence stock_movements_seq start with 1 increment by 50;

create table products (
    id bigint not null,
    establishment_id bigint not null,
    name varchar(100) not null,
    category varchar(16) not null,
    unit varchar(16) not null,
    min_stock numeric(12, 3),
    -- The last known cost of one unit; receiving a purchase with a cost updates it
    unit_cost numeric(12, 2),
    supplier_id bigint,
    active boolean not null default true,
    primary key (id),
    -- Lets a movement's foreign key check that the product is of the movement's establishment
    constraint uk_products_id_establishment unique (id, establishment_id),
    constraint ck_products_category check (category in ('SPIRITS', 'BEER', 'WINE', 'SOFT_DRINKS', 'COFFEE', 'FOOD',
                                                        'SUPPLIES', 'OTHER')),
    constraint ck_products_unit check (unit in ('BOTTLE', 'UNIT', 'KG', 'L', 'BOX', 'KEG')),
    constraint ck_products_min_stock check (min_stock is null or min_stock >= 0),
    constraint ck_products_unit_cost check (unit_cost is null or unit_cost >= 0),
    constraint fk_products_establishment foreign key (establishment_id) references establishments (id) on delete cascade,
    constraint fk_products_supplier foreign key (supplier_id) references suppliers (id) on delete set null
);

create unique index ux_products_establishment_name on products (establishment_id, lower(name));
create index idx_products_supplier on products (supplier_id);

create table stock_movements (
    id bigint not null,
    establishment_id bigint not null,
    product_id bigint not null,
    type varchar(16) not null,
    -- Signed: positive comes in, negative goes out
    quantity numeric(12, 3) not null,
    -- The cost of one unit this movement is valued at: the purchase's, or the product's at the time
    unit_cost numeric(12, 2),
    reason varchar(255),
    -- The business day the movement counts for
    date date not null,
    stock_after numeric(15, 3) not null,
    created_at timestamp(6) with time zone not null,
    created_by bigint,
    created_by_name varchar(511) not null,
    primary key (id),
    constraint ck_stock_movements_type check (type in ('PURCHASE', 'WASTE', 'CONSUMPTION', 'ADJUSTMENT', 'COUNT')),
    -- The type sets the sign: a purchase adds, waste and consumption take away, an adjustment is never 0.
    -- A count records the difference it found, which can be 0
    constraint ck_stock_movements_quantity_sign check (
        (type = 'PURCHASE' and quantity > 0)
        or (type in ('WASTE', 'CONSUMPTION') and quantity < 0)
        or (type = 'ADJUSTMENT' and quantity <> 0)
        or type = 'COUNT'),
    constraint ck_stock_movements_unit_cost check (unit_cost is null or unit_cost >= 0),
    constraint fk_stock_movements_establishment foreign key (establishment_id) references establishments (id) on delete restrict,
    constraint fk_stock_movements_product foreign key (product_id, establishment_id)
        references products (id, establishment_id) on delete restrict,
    constraint fk_stock_movements_created_by foreign key (created_by) references users (id) on delete set null
);

create index idx_stock_movements_establishment_date on stock_movements (establishment_id, date);
-- Adding up a product's stock reads only this index
create index idx_stock_movements_product on stock_movements (product_id) include (quantity);

-- Movements are only ever added. The one change allowed is the database itself clearing created_by when
-- the author's account is deleted. The whole row is compared (as jsonb, leaving out created_by), so
-- this still holds for columns added later.
create function stock_movements_append_only() returns trigger
    language plpgsql
as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'stock_movements rows cannot be deleted';
    end if;
    if (to_jsonb(new) - 'created_by') is distinct from (to_jsonb(old) - 'created_by')
       or new.created_by is not null and new.created_by is distinct from old.created_by then
        raise exception 'stock_movements rows cannot be modified';
    end if;
    return new;
end;
$$;

create trigger stock_movements_append_only
    before update or delete on stock_movements
    for each row execute function stock_movements_append_only();
