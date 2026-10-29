-- The half (1 or 2) each match event happened in, recorded by the server when the event is logged:
-- 1 while the first half is live or during the break, 2 once the second half has started.
--
-- Needed for minutes played (see MatchPlayerStatsCalculator). The minute alone can't tell the halves
-- apart, since first-half stoppage time overlaps the second half's first minutes. Timestamps can't
-- either: matches.second_half_started_at is the clock's reference point and moves with pause/resume
-- and "Set minute" corrections. Null for events logged before this existed.

ALTER TABLE public.match_events
    ADD COLUMN period integer;
