-- Baseline: the schema as it stood right before adopting Flyway, generated from the live Exposed
-- `Tables.kt` definitions (via `SchemaUtils.createMissingTablesAndColumns` against an empty database,
-- then `pg_dump --schema-only`) -- not hand-written. Every database this project already has (dev,
-- staging, production) is baselined AT this version, not migrated through it (see
-- `Flyway.configure().baselineOnMigrate(true).baselineVersion("1")` in `DatabaseFactory.kt`), so this
-- script only actually runs end-to-end against a brand-new, empty database. Never edit this file --
-- schema changes from here on are new, higher-numbered migration files. See TECHNICAL_DECISIONS.md.

CREATE TABLE public.users (
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    phone character varying(50),
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    avatar_url character varying(500),
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    is_platform_admin boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE public.clubs (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    short_name character varying(50),
    logo_url character varying(500),
    email character varying(255),
    phone character varying(50),
    address character varying(500),
    stadium_location character varying(500),
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    sport character varying(50) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT clubs_pkey PRIMARY KEY (id)
);

CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token character varying(500) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_unique UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.fcm_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    -- Unique on the token alone, not (user_id, token) -- see Tables.kt.
    token character varying(4096) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT fcm_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT fcm_tokens_token_unique UNIQUE (token),
    CONSTRAINT fk_fcm_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.club_members (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(50) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    CONSTRAINT club_members_pkey PRIMARY KEY (id),
    CONSTRAINT fk_club_members_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_club_members_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.seasons (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(20) DEFAULT 'EUROPEAN_YEAR'::character varying NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    active boolean DEFAULT true NOT NULL,
    CONSTRAINT seasons_pkey PRIMARY KEY (id),
    CONSTRAINT unique_club_season UNIQUE (club_id, type, start_date, end_date),
    CONSTRAINT fk_seasons_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.age_groups (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    season_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    start_year integer DEFAULT 0 NOT NULL,
    end_year integer DEFAULT 0 NOT NULL,
    team_size integer,
    CONSTRAINT age_groups_pkey PRIMARY KEY (id),
    CONSTRAINT unique_club_season_years UNIQUE (club_id, season_id, start_year, end_year),
    CONSTRAINT fk_age_groups_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_age_groups_season_id__id FOREIGN KEY (season_id) REFERENCES public.seasons(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.teams (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    season_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    code character varying(50),
    active boolean DEFAULT true NOT NULL,
    team_size integer,
    CONSTRAINT teams_pkey PRIMARY KEY (id),
    CONSTRAINT fk_teams_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_teams_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_teams_season_id__id FOREIGN KEY (season_id) REFERENCES public.seasons(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.players (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    birth_date date NOT NULL,
    shirt_number integer,
    photo_url character varying(500),
    active boolean DEFAULT true NOT NULL,
    "position" character varying(20),
    height_cm integer,
    weight_kg integer,
    emergency_contact character varying(255),
    CONSTRAINT players_pkey PRIMARY KEY (id),
    CONSTRAINT fk_players_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.age_group_players (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    player_id uuid NOT NULL,
    CONSTRAINT age_group_players_pkey PRIMARY KEY (id),
    CONSTRAINT unique_age_group_player UNIQUE (age_group_id, player_id),
    CONSTRAINT fk_age_group_players_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_age_group_players_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.age_group_staff (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(50) NOT NULL,
    CONSTRAINT age_group_staff_pkey PRIMARY KEY (id),
    CONSTRAINT fk_age_group_staff_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_age_group_staff_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.user_players (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    player_id uuid NOT NULL,
    relationship character varying(50) NOT NULL,
    CONSTRAINT user_players_pkey PRIMARY KEY (id),
    CONSTRAINT fk_user_players_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_user_players_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.invites (
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    club_id uuid NOT NULL,
    type character varying(50) NOT NULL,
    role character varying(50),
    player_id uuid,
    relationship character varying(50),
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT invites_pkey PRIMARY KEY (id),
    CONSTRAINT fk_invites_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_invites_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.opponents (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    short_name character varying(50),
    logo_url character varying(500),
    CONSTRAINT opponents_pkey PRIMARY KEY (id),
    CONSTRAINT fk_opponents_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.matches (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    team_id uuid NOT NULL,
    opponent_id uuid NOT NULL,
    location character varying(500),
    date_time timestamp without time zone NOT NULL,
    status character varying(50) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    notes text,
    half_length_minutes integer,
    kickoff_at timestamp without time zone,
    second_half_started_at timestamp without time zone,
    paused_at timestamp without time zone,
    CONSTRAINT matches_pkey PRIMARY KEY (id),
    CONSTRAINT fk_matches_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_matches_team_id__id FOREIGN KEY (team_id) REFERENCES public.teams(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_matches_opponent_id__id FOREIGN KEY (opponent_id) REFERENCES public.opponents(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.match_call_ups (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    status character varying(50) DEFAULT 'CALLED_UP'::character varying NOT NULL,
    notes text,
    is_starting boolean DEFAULT false NOT NULL,
    CONSTRAINT match_call_ups_pkey PRIMARY KEY (id),
    CONSTRAINT unique_match_player UNIQUE (match_id, player_id),
    CONSTRAINT fk_match_call_ups_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_match_call_ups_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE public.match_events (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    type character varying(50) NOT NULL,
    minute integer NOT NULL,
    player_id uuid,
    opponent_player_name character varying(255),
    is_opponent boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    CONSTRAINT match_events_pkey PRIMARY KEY (id),
    CONSTRAINT fk_match_events_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_match_events_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT
);
