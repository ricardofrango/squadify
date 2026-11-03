-- Squadify for Coaches' plans (REQUIREMENTS §5c, TECHNICAL_DECISIONS §72).

-- When an age group was created (shared with the clubs server, its V31): the first ones created stay
-- in use when a space goes down a plan. Existing rows get the migration's time.
ALTER TABLE public.age_groups
    ADD COLUMN created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL;

-- A space's plan, set by a platform admin until payments; a space with no row is on FREE.
CREATE TABLE space_plans (
    club_id UUID PRIMARY KEY,
    plan VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_space_plans_club_id__id FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
