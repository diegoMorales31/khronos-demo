-- A draft cash closing can be saved before the cash in the till is counted: counted_cash stays null until
-- then, and so does the difference the API works out from it. A day can only be closed once its cash is
-- counted, so a CLOSED closing still always has a counted cash and a stored difference (the existing
-- ck_cash_closings_closed_figures already requires the difference). The history keeps the closing's values
-- right after each change, so its counted_cash and difference can be null as well, but only together.
--
-- Dropping NOT NULL only changes the columns' definition and rewrites no row. Every existing row has a
-- counted cash and a difference, so the new checks hold for the data already there.

alter table cash_closings alter column counted_cash drop not null;

alter table cash_closings
    add constraint ck_cash_closings_counted_when_closed check (status = 'DRAFT' or counted_cash is not null);

alter table cash_closing_changes alter column counted_cash drop not null;
alter table cash_closing_changes alter column difference drop not null;

alter table cash_closing_changes
    add constraint ck_cash_closing_changes_counted_difference check ((counted_cash is null) = (difference is null));
