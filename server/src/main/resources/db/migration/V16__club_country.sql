-- A club's country (ISO code), for its national public holidays when creating training sessions
-- (design 39c). Every existing club is Portuguese.
ALTER TABLE public.clubs ADD COLUMN country character varying(2) DEFAULT 'PT' NOT NULL;
