-- What kind of fixture a match is: FRIENDLY, CHAMPIONSHIP or TOURNAMENT (MatchType), chosen when
-- it's scheduled. Nullable because matches scheduled before this have no type; every new match gets
-- one (CreateMatchRequest.type is required).

ALTER TABLE public.matches
    ADD COLUMN match_type character varying(50);
