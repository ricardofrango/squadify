-- Who manages a game while it's on (the developer, 2026-11-07): whoever kicked it off, or whoever took
-- it over since. During the game only they log events, change the pitch and run the clock. Shared with
-- the other server. Null before kickoff, and for the games kicked off before this (anyone with the
-- permission manages those, as before).

ALTER TABLE public.matches
    ADD COLUMN live_manager_user_id uuid;

ALTER TABLE public.matches
    ADD CONSTRAINT fk_matches_live_manager_user_id__id FOREIGN KEY (live_manager_user_id)
        REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE SET NULL;
