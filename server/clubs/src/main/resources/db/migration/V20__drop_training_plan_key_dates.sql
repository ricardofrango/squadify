-- The season plan no longer has key dates (dropped from the design), so their column goes.

ALTER TABLE public.training_plans DROP COLUMN key_dates;
