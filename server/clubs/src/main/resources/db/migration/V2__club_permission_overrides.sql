-- Per-club overrides for ConfigurablePermission (see Models.kt / TECHNICAL_DECISIONS.md). No row for a
-- (club_id, permission) pair means "use the action's default min team role" -- a row only exists once a
-- coordinator/admin has actually changed it. min_team_role itself being NULL is a real value, not "no
-- override": it means the age-group-staff path is disabled entirely for that action (coordinator-only).

CREATE TABLE public.club_permission_overrides (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    permission character varying(50) NOT NULL,
    min_team_role character varying(50),
    CONSTRAINT club_permission_overrides_pkey PRIMARY KEY (id),
    CONSTRAINT unique_club_permission UNIQUE (club_id, permission),
    CONSTRAINT fk_club_permission_overrides_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
