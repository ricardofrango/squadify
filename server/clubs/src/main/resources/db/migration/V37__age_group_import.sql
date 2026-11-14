-- Age group import and export (TECHNICAL_DECISIONS §81): export grants, the age groups imported from another
-- server, and the record-by-record mapping that lets an update find each copy.
CREATE TABLE export_grants (
    token_hash varchar(64) PRIMARY KEY,
    user_id uuid NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz NULL,
    CONSTRAINT fk_export_grants_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE import_links (
    target_club_id uuid NOT NULL,
    source_server varchar(255) NOT NULL,
    source_age_group_id uuid NOT NULL,
    target_age_group_id uuid NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (target_club_id, source_server, source_age_group_id),
    CONSTRAINT fk_import_links_target_club_id__id FOREIGN KEY (target_club_id) REFERENCES clubs(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_import_links_target_age_group_id__id FOREIGN KEY (target_age_group_id) REFERENCES age_groups(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE import_records (
    target_club_id uuid NOT NULL,
    source_server varchar(255) NOT NULL,
    source_age_group_id uuid NOT NULL,
    kind varchar(30) NOT NULL,
    source_id uuid NOT NULL,
    target_id uuid NOT NULL,
    PRIMARY KEY (target_club_id, source_server, source_age_group_id, kind, source_id),
    CONSTRAINT fk_import_records_target_club_id__id FOREIGN KEY (target_club_id) REFERENCES clubs(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
