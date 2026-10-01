-- Links an ASSIST to the GOAL it set up. A goal and its (optional) assist are now logged together
-- from the live console; related_event_id on the assist points at the goal. Null for every other
-- event, and for assists logged on their own before this existed.
--
-- The default NO ACTION (not RESTRICT) on delete is deliberate: it's checked at the end of the
-- statement, so deleting all of a match's events in one statement (DeleteMatchUseCase) still works.
-- Deleting a single goal removes its assist first (DeleteMatchEventUseCase).

ALTER TABLE public.match_events
    ADD COLUMN related_event_id uuid,
    ADD CONSTRAINT fk_match_events_related_event_id__id FOREIGN KEY (related_event_id) REFERENCES public.match_events(id);
