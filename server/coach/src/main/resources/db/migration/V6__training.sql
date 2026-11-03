-- Training on the coach server (REQUIREMENTS §5c: on Pro), the same tables as the clubs database's
-- (its V15, V17, V18, V19, V20 and V28, as they stand after them): plans, sessions, the exercise library
-- and each session's exercises, and players' ratings.

CREATE TABLE public.training_plans (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    level character varying(10) NOT NULL,
    period_start date NOT NULL,
    focus character varying(255),
    objectives text NOT NULL DEFAULT '[]',
    notes text,
    intensity character varying(10),
    phases text NOT NULL DEFAULT '[]',
    updated_at timestamp without time zone NOT NULL,
    updated_by uuid NOT NULL,
    CONSTRAINT training_plans_pkey PRIMARY KEY (id),
    CONSTRAINT unique_training_plan_period UNIQUE (age_group_id, level, period_start),
    CONSTRAINT fk_training_plans_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_plans_updated_by__id FOREIGN KEY (updated_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.training_sessions (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    date date NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    location character varying(500),
    notes text,
    cancelled_at timestamp without time zone,
    cancelled_by uuid,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT training_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT unique_training_session_time UNIQUE (age_group_id, date, start_time),
    CONSTRAINT fk_training_sessions_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_sessions_cancelled_by__id FOREIGN KEY (cancelled_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.training_exercises (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    duration_minutes integer,
    objectives text,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT training_exercises_pkey PRIMARY KEY (id),
    CONSTRAINT fk_training_exercises_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT training_exercises_duration_check CHECK (duration_minutes IS NULL OR duration_minutes > 0)
);

CREATE TABLE public.training_session_exercises (
    session_id uuid NOT NULL,
    exercise_id uuid NOT NULL,
    "position" integer NOT NULL,
    CONSTRAINT training_session_exercises_pkey PRIMARY KEY (session_id, exercise_id),
    CONSTRAINT fk_training_session_exercises_session_id__id FOREIGN KEY (session_id) REFERENCES public.training_sessions(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_session_exercises_exercise_id__id FOREIGN KEY (exercise_id) REFERENCES public.training_exercises(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.training_ratings (
    session_id uuid NOT NULL,
    player_id uuid NOT NULL,
    rating_hundredths integer NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    updated_by uuid NOT NULL,
    CONSTRAINT training_ratings_pkey PRIMARY KEY (session_id, player_id),
    CONSTRAINT training_ratings_range_check CHECK (rating_hundredths BETWEEN 0 AND 1000),
    CONSTRAINT fk_training_ratings_session_id__id FOREIGN KEY (session_id) REFERENCES public.training_sessions(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_ratings_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_ratings_updated_by__id FOREIGN KEY (updated_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
