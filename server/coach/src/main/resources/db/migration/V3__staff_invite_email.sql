-- Squadify for Coaches: an invite code can be sent by email; the address it went to is shown with the
-- code and counts towards the owner's daily limit (TECHNICAL_DECISIONS §72).
ALTER TABLE staff_invites ADD COLUMN sent_to VARCHAR(255) NULL;
