-- The opponent's tactic (free text, e.g. "4-4-2"), the counterpart of match_reports.tactic for our team.
-- A new migration rather than an edit of V5, which is already applied in some environments.

ALTER TABLE public.match_reports
    ADD COLUMN opponent_tactic text;
