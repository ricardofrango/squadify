-- Opponent details (design 38): the home stadium, a contact and free notes. All optional.
ALTER TABLE public.opponents ADD COLUMN stadium_name VARCHAR(255);
ALTER TABLE public.opponents ADD COLUMN stadium_address VARCHAR(500);
ALTER TABLE public.opponents ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE public.opponents ADD COLUMN contact_phone VARCHAR(50);
ALTER TABLE public.opponents ADD COLUMN notes TEXT;
