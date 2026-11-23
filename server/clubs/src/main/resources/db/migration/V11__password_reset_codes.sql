-- Forgot password: at most one pending reset code per user (a new request replaces it). Only a BCrypt
-- hash of the 6-digit code is stored; attempts counts wrong guesses (the code dies after 5).

CREATE TABLE public.password_reset_codes (
    user_id uuid NOT NULL,
    code_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT password_reset_codes_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_password_reset_codes_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
