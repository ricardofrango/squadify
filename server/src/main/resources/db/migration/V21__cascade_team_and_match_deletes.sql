-- Deleting a team deletes its matches, and deleting a match deletes everything that hangs off it:
-- call-ups, events, player stats and the report. Until now every FK was ON DELETE RESTRICT, so each
-- delete had to clear the children by hand, in order.

ALTER TABLE public.matches
    DROP CONSTRAINT fk_matches_team_id__id,
    ADD CONSTRAINT fk_matches_team_id__id FOREIGN KEY (team_id) REFERENCES public.teams(id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE public.match_call_ups
    DROP CONSTRAINT fk_match_call_ups_match_id__id,
    ADD CONSTRAINT fk_match_call_ups_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE public.match_events
    DROP CONSTRAINT fk_match_events_match_id__id,
    ADD CONSTRAINT fk_match_events_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE public.match_player_stats
    DROP CONSTRAINT fk_match_player_stats_match_id__id,
    ADD CONSTRAINT fk_match_player_stats_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE public.match_reports
    DROP CONSTRAINT fk_match_reports_match_id__id,
    ADD CONSTRAINT fk_match_reports_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;
