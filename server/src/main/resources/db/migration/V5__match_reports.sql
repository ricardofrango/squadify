-- The coach's post-match report: one per match, all free text. completed_at stays NULL while the
-- report is pending (it shows in the Home tab's to-do until then). Per-player ratings live on
-- match_player_stats.rating (V4), not here.

CREATE TABLE public.match_reports (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    tactic text,
    positive_aspects text,
    less_positive_aspects text,
    aspects_to_improve text,
    team_notes text,
    opponent_strengths text,
    opponent_weaknesses text,
    opponent_best_players text,
    opponent_additional_info text,
    completed_at timestamp without time zone,
    updated_at timestamp without time zone NOT NULL,
    updated_by uuid NOT NULL,
    CONSTRAINT match_reports_pkey PRIMARY KEY (id),
    CONSTRAINT unique_match_report UNIQUE (match_id),
    CONSTRAINT fk_match_reports_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_match_reports_updated_by__id FOREIGN KEY (updated_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
