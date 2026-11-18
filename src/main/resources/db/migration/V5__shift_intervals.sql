-- A shift whose end time is not after its start time ends on the next day: date 2026-11-20 from
-- 18:00 to 02:00 runs until 02:00 on the 21st. starts_at and ends_at hold that real interval as local
-- date-times, so the overlap and coverage checks can compare shifts on neighbouring days.
-- PostgreSQL computes both columns from date, start_time and end_time, so they can never disagree
-- with them, and existing rows are filled in when the columns are added.

alter table if exists worker_shifts
    add column starts_at timestamp(0) generated always as ("date" + start_time) stored;

alter table if exists worker_shifts
    add column ends_at timestamp(0) generated always as (
        case when end_time <= start_time then ("date" + 1) + end_time else "date" + end_time end
    ) stored;

create index idx_worker_shifts_worker_starts_at on worker_shifts (worker_id, starts_at);
