-- Training plans (design 40): an age group's season plan (macrocycle), month plans (mesocycles) and week
-- plans (microcycles), one row per plan. period_start is the season's start date, the 1st of the month or
-- the week's Monday. Objectives, phases and key dates are small ordered lists that are only ever read and
-- written with their plan, so they're stored as JSON text.

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
    key_dates text NOT NULL DEFAULT '[]',
    updated_at timestamp without time zone NOT NULL,
    updated_by uuid NOT NULL,
    CONSTRAINT training_plans_pkey PRIMARY KEY (id),
    CONSTRAINT unique_training_plan_period UNIQUE (age_group_id, level, period_start),
    CONSTRAINT fk_training_plans_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_training_plans_updated_by__id FOREIGN KEY (updated_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
