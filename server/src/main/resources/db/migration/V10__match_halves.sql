-- How many halves a match is played in: 2 (the default) or 1 -- some youth fixtures are a single
-- 20/25/30-minute period with no interval.

ALTER TABLE public.matches
    ADD COLUMN halves integer;

-- Every match recorded before this was played in two halves.
UPDATE public.matches
    SET halves = 2;

ALTER TABLE public.matches
    ALTER COLUMN halves SET DEFAULT 2,
    ALTER COLUMN halves SET NOT NULL;
