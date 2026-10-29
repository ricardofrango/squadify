-- Training ratings take two decimals ("7.55"), not one: stored as hundredths (0–1000) instead of tenths,
-- so two decimals stay exact. Existing ratings are multiplied by 10 (7.5 = 75 tenths = 750 hundredths).

ALTER TABLE public.training_ratings DROP CONSTRAINT training_ratings_range_check;
ALTER TABLE public.training_ratings RENAME COLUMN rating_tenths TO rating_hundredths;
UPDATE public.training_ratings SET rating_hundredths = rating_hundredths * 10;
ALTER TABLE public.training_ratings ADD CONSTRAINT training_ratings_range_check CHECK (rating_hundredths BETWEEN 0 AND 1000);
