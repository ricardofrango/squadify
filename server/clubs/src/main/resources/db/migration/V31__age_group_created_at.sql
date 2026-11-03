-- When an age group was created, set by the database. Shared with the coach server, where the first
-- age groups created stay in use when a space goes down a plan (REQUIREMENTS §5c). Existing rows get
-- the migration's time.

ALTER TABLE public.age_groups
    ADD COLUMN created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL;
