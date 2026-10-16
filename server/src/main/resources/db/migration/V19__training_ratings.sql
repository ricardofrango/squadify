-- Player ratings for a training session (design 39g), staff-only. 0 means "did not take part" (attendance);
-- a player who trained gets 0.1–10.0. Stored as tenths (0–100) so one decimal is exact.

CREATE TABLE public.training_ratings (
    session_id uuid NOT NULL,
    player_id uuid NOT NULL,
    rating_tenths integer NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    updated_by uuid NOT NULL,
    CONSTRAINT training_ratings_pkey PRIMARY KEY (session_id, player_id),
    CONSTRAINT training_ratings_range_check CHECK (rating_tenths BETWEEN 0 AND 100),
    CONSTRAINT fk_training_ratings_session_id__id FOREIGN KEY (session_id) REFERENCES public.training_sessions(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_ratings_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_ratings_updated_by__id FOREIGN KEY (updated_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
