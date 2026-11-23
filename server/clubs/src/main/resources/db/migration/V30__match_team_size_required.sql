-- Every match says how many a side it's played (V29 left it optional, meaning "the team's"): a match
-- keeps the count it was played with when its team's size changes later. Each existing match takes
-- its team's count (11 for a team from before team sizes).

UPDATE public.matches m
    SET team_size = COALESCE(t.team_size, 11)
    FROM public.teams t
    WHERE t.id = m.team_id AND m.team_size IS NULL;

-- A match whose team row is missing (none should be: matches cascade with their team, V21).
UPDATE public.matches
    SET team_size = 11
    WHERE team_size IS NULL;

ALTER TABLE public.matches
    ALTER COLUMN team_size SET NOT NULL;
