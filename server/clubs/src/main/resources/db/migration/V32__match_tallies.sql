-- The live game's shots, shots on goal and fouls on each player's stats line (the developer, 2026-11-03;
-- MatchTallies.kt), counted as in football: a goal is also a shot on goal, a shot on goal also a shot.
-- Shared with the other server. Lines from before were only recorded with goals, so their goals are
-- their shots and shots on goal; no fouls were recorded then.

ALTER TABLE public.match_player_stats
    ADD COLUMN shots integer DEFAULT 0 NOT NULL,
    ADD COLUMN shots_on_goal integer DEFAULT 0 NOT NULL,
    ADD COLUMN fouls integer DEFAULT 0 NOT NULL;

UPDATE public.match_player_stats SET shots = goals, shots_on_goal = goals;
