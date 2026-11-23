-- Post-match stats. Two pieces:
--
-- 1. The match-clock minute at which the first half and the match ended (same floor(seconds / 60)
--    unit as match_events.minute), recorded on the LIVE -> HALF_TIME and -> FINISHED transitions, so
--    minutes played can include stoppage time. Null for matches that ended before this existed.
--
-- 2. match_player_stats: one row per called-up player of a FINISHED match, computed once at full time
--    from the call-ups and events. A finished match's events can no longer change, so this never goes
--    stale. rating (0-10) is filled in later by the coach, as part of the match report.

ALTER TABLE public.matches
    ADD COLUMN first_half_end_minute integer,
    ADD COLUMN full_time_minute integer;

CREATE TABLE public.match_player_stats (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    started boolean NOT NULL,
    minutes_played integer NOT NULL,
    goals integer DEFAULT 0 NOT NULL,
    assists integer DEFAULT 0 NOT NULL,
    own_goals integer DEFAULT 0 NOT NULL,
    yellow_cards integer DEFAULT 0 NOT NULL,
    red_cards integer DEFAULT 0 NOT NULL,
    rating integer,
    CONSTRAINT match_player_stats_pkey PRIMARY KEY (id),
    CONSTRAINT unique_match_player_stats UNIQUE (match_id, player_id),
    CONSTRAINT match_player_stats_rating_range CHECK (rating IS NULL OR (rating >= 0 AND rating <= 10)),
    CONSTRAINT fk_match_player_stats_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_match_player_stats_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
