-- The exercise library of an age group (design 39h) and the exercises of each session, in order.
-- An exercise is written once and reused; editing it changes it in every session that uses it.

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
