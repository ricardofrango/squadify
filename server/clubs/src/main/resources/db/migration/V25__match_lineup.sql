-- The live pitch view: a match's formation ("D-M-A", e.g. "2-3-1") and who stands in each position.
-- formation is the current one (it can change during the game); starting_formation is the one at
-- kickoff, which prefills the match report's tactic. pitch_slot is the position a called-up player
-- holds on the pitch: 0 is the goalkeeper, then defence, midfield and attack, each zone left to right
-- (see Formation.kt). Null for the bench, and for every match played before this.

ALTER TABLE public.matches
    ADD COLUMN formation character varying(20),
    ADD COLUMN starting_formation character varying(20);

ALTER TABLE public.match_call_ups
    ADD COLUMN pitch_slot integer;

-- One player per position in a match.
CREATE UNIQUE INDEX unique_match_pitch_slot ON public.match_call_ups (match_id, pitch_slot) WHERE pitch_slot IS NOT NULL;
