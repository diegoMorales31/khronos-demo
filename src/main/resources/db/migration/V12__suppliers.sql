-- Suppliers an establishment buys its stock from: a name and an optional phone, email and notes.
--
-- A supplier's name is unique within its establishment whatever its case, so "Mahou" and "MAHOU" are the
-- same supplier. The unique index on lower(name) keeps it so even when two requests race; the service
-- turns its violation into a 409.
--
-- Suppliers go with their establishment. Products and purchase orders refer to them (V13 and V14).

create sequence suppliers_seq start with 1 increment by 50;

create table suppliers (
    id bigint not null,
    establishment_id bigint not null,
    name varchar(100) not null,
    phone varchar(30),
    email varchar(255),
    notes varchar(500),
    primary key (id),
    constraint fk_suppliers_establishment foreign key (establishment_id) references establishments (id) on delete cascade
);

create unique index ux_suppliers_establishment_name on suppliers (establishment_id, lower(name));
