-- A game that wasn't followed live (a flat battery, no signal) can have its result entered afterwards:
-- its goals, cards and each player's minutes, typed in (SaveMatchRecordUseCase). Those games have no
-- substitutions or timeline, and their minutes stay editable; a game followed live keeps its minutes.

ALTER TABLE public.matches
    ADD COLUMN entered_after_game boolean NOT NULL DEFAULT false;
