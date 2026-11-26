-- Squadify for Coaches: the codes an owner shares so someone joins an age group as staff
-- (TECHNICAL_DECISIONS §72). Single-use; they go with their age group.
CREATE TABLE staff_invites (
    id UUID PRIMARY KEY,
    code VARCHAR(16) NOT NULL,
    age_group_id UUID NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted_by UUID NULL,
    accepted_at TIMESTAMP NULL,
    CONSTRAINT fk_staff_invites_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES age_groups(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_staff_invites_created_by__id FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_staff_invites_accepted_by__id FOREIGN KEY (accepted_by) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
ALTER TABLE staff_invites ADD CONSTRAINT staff_invites_code_unique UNIQUE (code);
