-- A club's two colours (ClubTheme), as "#RRGGBB". Both or neither: a club either has its own colours
-- or uses Squadify's. The contrast rules (a strong primary, a clearly lighter secondary) are checked in
-- UpdateClubUseCase, since they need maths the database shouldn't own.

ALTER TABLE public.clubs
    ADD COLUMN theme_primary_color character varying(7),
    ADD COLUMN theme_secondary_color character varying(7),
    ADD CONSTRAINT chk_clubs_theme_pair CHECK ((theme_primary_color IS NULL) = (theme_secondary_color IS NULL)),
    ADD CONSTRAINT chk_clubs_theme_format CHECK (
        (theme_primary_color IS NULL OR theme_primary_color ~ '^#[0-9A-F]{6}$')
        AND (theme_secondary_color IS NULL OR theme_secondary_color ~ '^#[0-9A-F]{6}$')
    );
