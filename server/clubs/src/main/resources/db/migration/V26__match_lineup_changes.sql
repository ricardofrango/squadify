-- Every arrangement of the pitch during a game, so a finished match can be replayed (its Timeline
-- tab): the starting one at kickoff, then each formation change or position swap the coach saves.
-- slots is the arrangement as JSON ({"0": "<player id>", ...}, slot -> player; see Formation.kt).
-- minute and period are the match clock and half when it was saved, like an event's. Substitutions
-- and red cards aren't stored here: they're the match's events.

CREATE TABLE public.match_lineup_changes (
    id uuid PRIMARY KEY,
    match_id uuid NOT NULL REFERENCES public.matches (id) ON DELETE CASCADE,
    minute integer NOT NULL,
    period integer NOT NULL,
    formation character varying(20) NOT NULL,
    slots text NOT NULL,
    created_at timestamp without time zone NOT NULL
);

CREATE INDEX idx_match_lineup_changes_match ON public.match_lineup_changes (match_id);
