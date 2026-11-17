-- The goalkeeper who suffered a goal by the opponent (TECHNICAL_DECISIONS §86): set from the pitch when the goal is
-- logged live, or picked in the match record. Null for every other event, and when it isn't known.
ALTER TABLE public.match_events
    ADD COLUMN goalkeeper_player_id uuid,
    ADD CONSTRAINT fk_match_events_goalkeeper_player_id__id FOREIGN KEY (goalkeeper_player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
