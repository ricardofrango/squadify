-- Change email: at most one pending request per user (a new one replaces it) -- the address it would
-- switch to, and a BCrypt hash of the 6-digit code sent there; attempts counts wrong guesses (dead at 5).

CREATE TABLE public.email_change_codes (
    user_id uuid NOT NULL,
    new_email character varying(255) NOT NULL,
    code_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT email_change_codes_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_email_change_codes_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
