-- Training sessions (design 39): one row per training of an age group. A cancelled session keeps its row
-- (cancelled_at / cancelled_by set) and its content; deleting removes it. One session per age group, date
-- and start time: creating another at the same time replaces it.

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
