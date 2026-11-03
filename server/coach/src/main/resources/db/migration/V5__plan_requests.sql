-- The plan a space's owner asked for from the app (REQUIREMENTS §5c), until payments: a platform admin
-- is emailed and sets it, which deletes the row. One a space, the latest.
CREATE TABLE plan_requests (
    club_id UUID PRIMARY KEY,
    plan VARCHAR(20) NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_plan_requests_club_id__id FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_plan_requests_requested_by__id FOREIGN KEY (requested_by) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
