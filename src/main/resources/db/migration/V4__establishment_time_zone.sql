-- Each establishment's IANA time zone. Clock-ins are stored as UTC instants and turned into local
-- dates and times with it: the business date of a time entry, the reports and the CSV exports.
-- Existing establishments get Europe/Madrid, the zone of the venues the app is sold to.

alter table if exists establishments
    add column time_zone varchar(64) not null default 'Europe/Madrid';
