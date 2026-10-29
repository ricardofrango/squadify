-- Where a match is played from our side: HOME, AWAY or NEUTRAL (MatchVenue). It decides which side
-- is shown first (home first, our club first when neutral) and prefills the location when the match
-- is scheduled. Every match scheduled before this is taken as a home match (the developer's call).

ALTER TABLE public.matches
    ADD COLUMN venue character varying(20) NOT NULL DEFAULT 'HOME';
