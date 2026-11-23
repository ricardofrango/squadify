-- Birth years no longer unique per club and season in the database (the developer, 2026-11-09): the coach
-- app names an age group after the club it plays for ("Povoense - Sub 11"), and two clubs' age groups in
-- one coach's space may share birth years. Which age groups may not is the servers' rule
-- (AccessPolicy.shareBirthYears): every one of a club's on the clubs server, as before; the same club's
-- on the coach server. Shared with the other server.

ALTER TABLE public.age_groups
    DROP CONSTRAINT IF EXISTS unique_club_season_years;
