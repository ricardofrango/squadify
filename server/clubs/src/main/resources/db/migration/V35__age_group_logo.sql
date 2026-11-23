-- An age group's own crest (the developer, 2026-11-09): the coach app names its age groups after the club
-- they play for ("Povoense - Sub 11") and shows that club's crest. Shared with the other server, where it
-- stays null. Null: no crest, the initials are shown.

ALTER TABLE public.age_groups
    ADD COLUMN logo_url character varying(500);
