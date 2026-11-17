-- The goals the opponent scored with this player in goal, on each player's stats line (TECHNICAL_DECISIONS §86):
-- counted from the goalkeeper on the opponent's goals when the line is worked out. Lines from before have none,
-- since no goalkeeper was recorded then.
ALTER TABLE public.match_player_stats
    ADD COLUMN goals_conceded integer DEFAULT 0 NOT NULL;
