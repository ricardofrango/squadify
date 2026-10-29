-- A user can hold at most one membership per club. Before this, nothing stopped ProvisionClubUseCase,
-- CreatePlayersUseCase, UpdatePlayerUseCase, or RegisterUseCase from inserting a second club_members row
-- for the same (club_id, user_id), possibly with a conflicting role.
--
-- Collapse any existing duplicates first, or the constraint below can't be created. The row kept per
-- (club_id, user_id) is the active one, then the highest role (CLUB_ADMIN > COORDINATOR > MEMBER), so no
-- one loses access they already had. id is only a deterministic tiebreaker.

DELETE FROM public.club_members
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY club_id, user_id
                   ORDER BY active DESC,
                            CASE role WHEN 'CLUB_ADMIN' THEN 3 WHEN 'COORDINATOR' THEN 2 ELSE 1 END DESC,
                            id
               ) AS rn
        FROM public.club_members
    ) ranked
    WHERE ranked.rn > 1
);

ALTER TABLE public.club_members
    ADD CONSTRAINT unique_club_member UNIQUE (club_id, user_id);
