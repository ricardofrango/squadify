-- The sport a match is played in (the developer, 2026-11-06): the live match and a finished match's
-- Timeline draw a futsal court or a football pitch from it. Shared with the other server. Each match
-- takes its club's sport (a coach space is a club row too); a new one gets it when it's created.

ALTER TABLE public.matches
    ADD COLUMN sport character varying(50);

UPDATE public.matches m
    SET sport = c.sport
    FROM public.clubs c
    WHERE c.id = m.club_id;

-- A match whose club row is missing (none should be: club_id references clubs).
UPDATE public.matches
    SET sport = 'FOOTBALL'
    WHERE sport IS NULL;

ALTER TABLE public.matches
    ALTER COLUMN sport SET NOT NULL;
