-- An index for adding up the stock of every product of an establishment, which every product listing and
-- the inventory report do (StockMovementRepository.sumByEstablishment: the sum of quantity for each
-- product_id of one establishment_id). The index on (establishment_id, date) finds the movements but not
-- their quantity, so the sum had to visit each movement's row in the table. This one holds all three
-- columns, in product order, so PostgreSQL can read the sums from the index alone (an index-only scan,
-- once vacuum has marked the table's pages visible) and group them without sorting.
--
-- Known limit: the stock is still added up from the whole history of movements on every listing, so its
-- cost grows with that history, only more slowly. If it ever becomes too slow, the next step is to keep each
-- product's stock in a column updated in the same transaction and under the same row lock as each movement
-- (see StockLedger); that is not done here.
--
-- It only adds an index, so it runs on a database that already has data. Building it blocks writes to
-- stock_movements, not reads, while it runs, which for a bar's history is a moment.

create index idx_stock_movements_establishment_product
    on stock_movements (establishment_id, product_id) include (quantity);
