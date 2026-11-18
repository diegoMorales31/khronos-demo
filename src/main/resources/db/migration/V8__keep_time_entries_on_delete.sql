-- Time entries and their history are the record of the hours worked. V7 deleted them together with
-- their worker or establishment; from now on the database refuses those deletes, as the service already
-- does with a 409 that suggests deactivating the worker instead. An entry with history can't be deleted
-- either. Two links keep their behaviour: deleting a shift clears the entries' shift_id, and deleting
-- the author of a change clears changed_by and keeps the change with the author's name.
--
-- Each foreign key is dropped and added again in the same statement, so the tables are never without
-- it. The existing rows already meet it, because only the ON DELETE action changes.

alter table time_entries
    drop constraint fk_time_entries_worker,
    add constraint fk_time_entries_worker
        foreign key (worker_id) references workers (user_id) on delete restrict;

alter table time_entries
    drop constraint fk_time_entries_establishment,
    add constraint fk_time_entries_establishment
        foreign key (establishment_id) references establishments (id) on delete restrict;

alter table time_entry_changes
    drop constraint fk_time_entry_changes_time_entry,
    add constraint fk_time_entry_changes_time_entry
        foreign key (time_entry_id) references time_entries (id) on delete restrict;
