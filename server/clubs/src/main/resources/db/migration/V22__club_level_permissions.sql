-- Club-level configurable permissions (ConfigurablePermission.isClubLevel, e.g. MANAGE_PLAYER_STATUS):
-- their threshold is a club role, not an age-group staff role. COORDINATOR (admins and coordinators) or
-- CLUB_ADMIN (admins only); NULL on every age-group action's row. No row still means the default.
ALTER TABLE public.club_permission_overrides ADD COLUMN min_club_role character varying(50);
