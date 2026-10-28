-- A match can be played with a different a-side count from its team's (a 9-a-side team playing a
-- 7-a-side game). Null plays the team's (Team.teamSize). It can change before kickoff, or for a game
-- whose result was entered afterwards; never once the game was followed live.

ALTER TABLE public.matches
    ADD COLUMN team_size integer;
