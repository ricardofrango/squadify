-- The coach database's starting schema (TECHNICAL_DECISIONS §72): the tables it shares with the clubs
-- database (Tables.kt's sharedTables), exactly as the clubs migrations V1..V30 leave them, so the shared
-- Exposed repositories work unchanged. Generated with pg_dump from a database migrated with the clubs
-- migrations, keeping only these tables. Left out: parents' links, invites, configurable permissions and
-- training (clubsOnlyTables). CoachSchemaTest checks this against Tables.kt.
--
-- From here on, a change to a shared table needs a migration here too, in the same PR as the clubs one.

--
-- Name: age_group_players; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.age_group_players (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    player_id uuid NOT NULL
);

--
-- Name: age_group_staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.age_group_staff (
    id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(50) NOT NULL
);

--
-- Name: age_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.age_groups (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    season_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    start_year integer DEFAULT 0 NOT NULL,
    end_year integer DEFAULT 0 NOT NULL,
    team_size integer
);

--
-- Name: club_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.club_members (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(50) NOT NULL,
    active boolean DEFAULT true NOT NULL
);

--
-- Name: clubs; Type: TABLE; Schema: public; Owner: -
--

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
    country character varying(2) DEFAULT 'PT'::character varying NOT NULL,
    theme_primary_color character varying(7),
    theme_secondary_color character varying(7),
    CONSTRAINT chk_clubs_theme_format CHECK ((((theme_primary_color IS NULL) OR ((theme_primary_color)::text ~ '^#[0-9A-F]{6}$'::text)) AND ((theme_secondary_color IS NULL) OR ((theme_secondary_color)::text ~ '^#[0-9A-F]{6}$'::text)))),
    CONSTRAINT chk_clubs_theme_pair CHECK (((theme_primary_color IS NULL) = (theme_secondary_color IS NULL)))
);

--
-- Name: email_change_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_change_codes (
    user_id uuid NOT NULL,
    new_email character varying(255) NOT NULL,
    code_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL
);

--
-- Name: fcm_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fcm_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token character varying(4096) NOT NULL,
    created_at timestamp without time zone NOT NULL
);

--
-- Name: match_call_ups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_call_ups (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    status character varying(50) DEFAULT 'CALLED_UP'::character varying NOT NULL,
    notes text,
    is_starting boolean DEFAULT false NOT NULL,
    pitch_slot integer
);

--
-- Name: match_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_events (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    type character varying(50) NOT NULL,
    minute integer NOT NULL,
    player_id uuid,
    opponent_player_name character varying(255),
    is_opponent boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    period integer,
    related_event_id uuid
);

--
-- Name: match_lineup_changes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_lineup_changes (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    minute integer NOT NULL,
    period integer NOT NULL,
    formation character varying(20) NOT NULL,
    slots text NOT NULL,
    created_at timestamp without time zone NOT NULL
);

--
-- Name: match_player_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_player_stats (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    player_id uuid NOT NULL,
    started boolean NOT NULL,
    minutes_played integer NOT NULL,
    goals integer DEFAULT 0 NOT NULL,
    assists integer DEFAULT 0 NOT NULL,
    own_goals integer DEFAULT 0 NOT NULL,
    yellow_cards integer DEFAULT 0 NOT NULL,
    red_cards integer DEFAULT 0 NOT NULL,
    rating integer,
    CONSTRAINT match_player_stats_rating_range CHECK (((rating IS NULL) OR ((rating >= 0) AND (rating <= 10))))
);

--
-- Name: match_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.match_reports (
    id uuid NOT NULL,
    match_id uuid NOT NULL,
    tactic text,
    positive_aspects text,
    less_positive_aspects text,
    aspects_to_improve text,
    team_notes text,
    opponent_strengths text,
    opponent_weaknesses text,
    opponent_best_players text,
    opponent_additional_info text,
    completed_at timestamp without time zone,
    updated_at timestamp without time zone NOT NULL,
    updated_by uuid NOT NULL,
    opponent_tactic text
);

--
-- Name: matches; Type: TABLE; Schema: public; Owner: -
--

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
    first_half_end_minute integer,
    full_time_minute integer,
    match_type character varying(50),
    halves integer DEFAULT 2 NOT NULL,
    venue character varying(20) DEFAULT 'HOME'::character varying NOT NULL,
    formation character varying(20),
    starting_formation character varying(20),
    entered_after_game boolean DEFAULT false NOT NULL,
    team_size integer NOT NULL
);

--
-- Name: opponents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.opponents (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    short_name character varying(50),
    logo_url character varying(500),
    stadium_name character varying(255),
    stadium_address character varying(500),
    contact_email character varying(255),
    contact_phone character varying(50),
    notes text
);

--
-- Name: password_reset_codes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_codes (
    user_id uuid NOT NULL,
    code_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone NOT NULL
);

--
-- Name: players; Type: TABLE; Schema: public; Owner: -
--

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
    emergency_contact character varying(255)
);

--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token character varying(500) NOT NULL,
    expires_at timestamp without time zone NOT NULL
);

--
-- Name: seasons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seasons (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(20) DEFAULT 'EUROPEAN_YEAR'::character varying NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    active boolean DEFAULT true NOT NULL
);

--
-- Name: teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teams (
    id uuid NOT NULL,
    club_id uuid NOT NULL,
    age_group_id uuid NOT NULL,
    season_id uuid NOT NULL,
    name character varying(100) NOT NULL,
    code character varying(50),
    active boolean DEFAULT true NOT NULL,
    team_size integer
);

--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

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
    password_check_failures integer DEFAULT 0 NOT NULL
);

--
-- Name: age_group_players age_group_players_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_players
    ADD CONSTRAINT age_group_players_pkey PRIMARY KEY (id);

--
-- Name: age_group_staff age_group_staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_staff
    ADD CONSTRAINT age_group_staff_pkey PRIMARY KEY (id);

--
-- Name: age_groups age_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_groups
    ADD CONSTRAINT age_groups_pkey PRIMARY KEY (id);

--
-- Name: club_members club_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.club_members
    ADD CONSTRAINT club_members_pkey PRIMARY KEY (id);

--
-- Name: clubs clubs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clubs
    ADD CONSTRAINT clubs_pkey PRIMARY KEY (id);

--
-- Name: email_change_codes email_change_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_change_codes
    ADD CONSTRAINT email_change_codes_pkey PRIMARY KEY (user_id);

--
-- Name: fcm_tokens fcm_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_tokens
    ADD CONSTRAINT fcm_tokens_pkey PRIMARY KEY (id);

--
-- Name: fcm_tokens fcm_tokens_token_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_tokens
    ADD CONSTRAINT fcm_tokens_token_unique UNIQUE (token);

--
-- Name: match_call_ups match_call_ups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_call_ups
    ADD CONSTRAINT match_call_ups_pkey PRIMARY KEY (id);

--
-- Name: match_events match_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_events
    ADD CONSTRAINT match_events_pkey PRIMARY KEY (id);

--
-- Name: match_lineup_changes match_lineup_changes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_lineup_changes
    ADD CONSTRAINT match_lineup_changes_pkey PRIMARY KEY (id);

--
-- Name: match_player_stats match_player_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_player_stats
    ADD CONSTRAINT match_player_stats_pkey PRIMARY KEY (id);

--
-- Name: match_reports match_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_reports
    ADD CONSTRAINT match_reports_pkey PRIMARY KEY (id);

--
-- Name: matches matches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT matches_pkey PRIMARY KEY (id);

--
-- Name: opponents opponents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opponents
    ADD CONSTRAINT opponents_pkey PRIMARY KEY (id);

--
-- Name: password_reset_codes password_reset_codes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_codes
    ADD CONSTRAINT password_reset_codes_pkey PRIMARY KEY (user_id);

--
-- Name: players players_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_pkey PRIMARY KEY (id);

--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

--
-- Name: refresh_tokens refresh_tokens_token_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_unique UNIQUE (token);

--
-- Name: seasons seasons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seasons
    ADD CONSTRAINT seasons_pkey PRIMARY KEY (id);

--
-- Name: teams teams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);

--
-- Name: age_group_players unique_age_group_player; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_players
    ADD CONSTRAINT unique_age_group_player UNIQUE (age_group_id, player_id);

--
-- Name: club_members unique_club_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.club_members
    ADD CONSTRAINT unique_club_member UNIQUE (club_id, user_id);

--
-- Name: seasons unique_club_season; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seasons
    ADD CONSTRAINT unique_club_season UNIQUE (club_id, type, start_date, end_date);

--
-- Name: age_groups unique_club_season_years; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_groups
    ADD CONSTRAINT unique_club_season_years UNIQUE (club_id, season_id, start_year, end_year);

--
-- Name: match_call_ups unique_match_player; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_call_ups
    ADD CONSTRAINT unique_match_player UNIQUE (match_id, player_id);

--
-- Name: match_player_stats unique_match_player_stats; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_player_stats
    ADD CONSTRAINT unique_match_player_stats UNIQUE (match_id, player_id);

--
-- Name: match_reports unique_match_report; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_reports
    ADD CONSTRAINT unique_match_report UNIQUE (match_id);

--
-- Name: users users_email_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_unique UNIQUE (email);

--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

--
-- Name: idx_match_lineup_changes_match; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_match_lineup_changes_match ON public.match_lineup_changes USING btree (match_id);

--
-- Name: unique_match_pitch_slot; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX unique_match_pitch_slot ON public.match_call_ups USING btree (match_id, pitch_slot) WHERE (pitch_slot IS NOT NULL);

--
-- Name: age_group_players fk_age_group_players_age_group_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_players
    ADD CONSTRAINT fk_age_group_players_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: age_group_players fk_age_group_players_player_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_players
    ADD CONSTRAINT fk_age_group_players_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: age_group_staff fk_age_group_staff_age_group_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_staff
    ADD CONSTRAINT fk_age_group_staff_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: age_group_staff fk_age_group_staff_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_group_staff
    ADD CONSTRAINT fk_age_group_staff_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: age_groups fk_age_groups_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_groups
    ADD CONSTRAINT fk_age_groups_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: age_groups fk_age_groups_season_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.age_groups
    ADD CONSTRAINT fk_age_groups_season_id__id FOREIGN KEY (season_id) REFERENCES public.seasons(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: club_members fk_club_members_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.club_members
    ADD CONSTRAINT fk_club_members_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: club_members fk_club_members_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.club_members
    ADD CONSTRAINT fk_club_members_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: email_change_codes fk_email_change_codes_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_change_codes
    ADD CONSTRAINT fk_email_change_codes_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: fcm_tokens fk_fcm_tokens_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fcm_tokens
    ADD CONSTRAINT fk_fcm_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: match_call_ups fk_match_call_ups_match_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_call_ups
    ADD CONSTRAINT fk_match_call_ups_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

--
-- Name: match_call_ups fk_match_call_ups_player_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_call_ups
    ADD CONSTRAINT fk_match_call_ups_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: match_events fk_match_events_match_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_events
    ADD CONSTRAINT fk_match_events_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

--
-- Name: match_events fk_match_events_player_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_events
    ADD CONSTRAINT fk_match_events_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: match_events fk_match_events_related_event_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_events
    ADD CONSTRAINT fk_match_events_related_event_id__id FOREIGN KEY (related_event_id) REFERENCES public.match_events(id);

--
-- Name: match_player_stats fk_match_player_stats_match_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_player_stats
    ADD CONSTRAINT fk_match_player_stats_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

--
-- Name: match_player_stats fk_match_player_stats_player_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_player_stats
    ADD CONSTRAINT fk_match_player_stats_player_id__id FOREIGN KEY (player_id) REFERENCES public.players(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: match_reports fk_match_reports_match_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_reports
    ADD CONSTRAINT fk_match_reports_match_id__id FOREIGN KEY (match_id) REFERENCES public.matches(id) ON UPDATE RESTRICT ON DELETE CASCADE;

--
-- Name: match_reports fk_match_reports_updated_by__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_reports
    ADD CONSTRAINT fk_match_reports_updated_by__id FOREIGN KEY (updated_by) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: matches fk_matches_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk_matches_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: matches fk_matches_opponent_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk_matches_opponent_id__id FOREIGN KEY (opponent_id) REFERENCES public.opponents(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: matches fk_matches_team_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk_matches_team_id__id FOREIGN KEY (team_id) REFERENCES public.teams(id) ON UPDATE RESTRICT ON DELETE CASCADE;

--
-- Name: opponents fk_opponents_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opponents
    ADD CONSTRAINT fk_opponents_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: password_reset_codes fk_password_reset_codes_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_codes
    ADD CONSTRAINT fk_password_reset_codes_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: players fk_players_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.players
    ADD CONSTRAINT fk_players_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: refresh_tokens fk_refresh_tokens_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: seasons fk_seasons_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seasons
    ADD CONSTRAINT fk_seasons_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: teams fk_teams_age_group_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT fk_teams_age_group_id__id FOREIGN KEY (age_group_id) REFERENCES public.age_groups(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: teams fk_teams_club_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT fk_teams_club_id__id FOREIGN KEY (club_id) REFERENCES public.clubs(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: teams fk_teams_season_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT fk_teams_season_id__id FOREIGN KEY (season_id) REFERENCES public.seasons(id) ON UPDATE RESTRICT ON DELETE RESTRICT;

--
-- Name: match_lineup_changes match_lineup_changes_match_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.match_lineup_changes
    ADD CONSTRAINT match_lineup_changes_match_id_fkey FOREIGN KEY (match_id) REFERENCES public.matches(id) ON DELETE CASCADE;
