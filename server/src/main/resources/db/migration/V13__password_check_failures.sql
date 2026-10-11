-- Wrong current-password attempts in a row on Change password / Change email. At 3 the user is signed
-- out everywhere and the count starts again; a correct password resets it.

ALTER TABLE public.users
    ADD COLUMN password_check_failures integer DEFAULT 0 NOT NULL;
