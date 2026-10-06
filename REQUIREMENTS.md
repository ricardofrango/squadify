# Squadify Platform - Requirements

This document outlines the core requirements and architectural decisions for the Squadify platform.

## 1. User Profiles & Roles
The application supports five primary user types:
- **Platform Owner (Super Admin)**: Global owner of the platform. Can create (provision) clubs and manage platform settings.
- **Club Admin / Coordinator (Director)**: Global club management, assigned by the Platform Owner.
- **Coach**: Full management of assigned age groups -- scheduling/editing matches, plus everything Staff can do.
- **Staff**: Match-day operations for their assigned age group (call-ups, starting a match, updating its status) and specific functional tasks (Physio, Team Manager). Cannot schedule/edit matches or log match events -- coach-only.
- **Parent / User**: Consult child info, call-ups, and calendar.

*Future roles: Platform Admin, Payment Management, Document Management.*

## 2. Main Hierarchy
The domain follows a multi-tenant structure isolated by `clubId`:
`Club -> Age Group -> (Players / Coaches / Staff)`, with `Team` a scheduling handle **under** an Age Group.
- An **Age Group** (e.g., Sub-18) owns its **players** (a pool), its **coaches/staff**, and one or more **Teams** (e.g., Sub-18 A, Sub-18 B).
- Players and coaches are attached to the **Age Group**, not to a Team. Every Team in an age group draws from the same player pool; who plays a given match is decided per-match via call-ups. This supports clubs that don't run fixed squads — a player can play for Team A one month and Team B the next without being duplicated.
- A player can belong to **several** age groups at once: their own birth-year group plus any **older** group they're eligible to play up into (`birthDate.year >= ageGroup.endYear`); never a younger group.
- A **Team** exists mainly to schedule matches. **Team** and **Age Group** are tied to a **Season** to maintain historical data.

## 3. Core Entities
- **User**: Account data (email, phone, name, avatar, status).
- **Club**: Branding and contact info, plus a **sport** (`FOOTBALL` / `FUTSAL` for now) chosen when the club is provisioned. The sport also fixes the set of allowed **team sizes** (a-side counts) — football 4/5/7/9/11, futsal 4/5 — used by age groups and teams below. Everything else below the club is sport-agnostic.
- **Season**: Time-bound periods. Supports two types:
    - **Calendar Year**: January 1st to December 31st (e.g., Brazil).
    - **European Year**: July 1st to June 30th (e.g., Europe).
- **Age Group**: Categorization of players by a birth-year span (`startYear`/`endYear`) — a single year for most (football) groups, or two consecutive birth years for groups (typically futsal) that need both cohorts to field a squad; owns the player pool and the coaching staff. Carries an optional **team size** — the a-side count its teams play by default (one of the club sport's allowed sizes).
- **Team**: A named group under an age group, used to schedule matches. Has its own **team size**, pre-filled from the age group's default but overridable (e.g. a 7-a-side age group with one team that plays 9-a-side).
- **Player**: Personal data and stats.
- **Opponent**: Management of external clubs/teams for matches (name, short name, logo).
- **ClubMember**: Links Users to Clubs with a specific Role.
- **AgeGroupPlayer**: Links a Player to an Age Group pool (many-to-many; `(ageGroupId, playerId)` unique).
- **AgeGroupStaff**: Links a User (coach/staff) to an Age Group with a role.
- **UserPlayer**: Links Parents to Players (supports multiple guardians).
- **TrainingSession** *(planned, Phase 8)*: A training session of an age group: date, time, location, duration, and its exercises.
- **TrainingExercise** *(planned)*: One exercise within a session: name and description (optionally duration and objectives).
- **TrainingEvaluation** *(planned)*: A player's rating for one session, 0–10 with decimals; 0 means the player didn't take part.
- **Macrocycle / Mesocycle / Microcycle** *(planned)*: The age group's training plan at three levels: the year (macrocycle), each month (mesocycle) and each week (microcycle).

## 4. Authentication & Security
- **Authentication**: JWT (JSON Web Tokens) with Refresh Token flow.
- **Password Security**: Hashing using BCrypt.
- **Access Rights**: 
    - Authorization is checked at the API level.
    - **Super Admin**: Global "God Mode" access to all clubs and platform data.
    - **Admins/Coordinators**: Full access within their specific club context.
    - **Coaches**: Can only manage teams where they are assigned as staff.
- **Error Handling**: Standardized `ErrorResponse` JSON for all failures, using a custom `SquadifyException` hierarchy.

## 5. Match Management
- **Match**: Details about opponent (linked to Opponent entity), location, date, status, and type (`MatchType`: `FRIENDLY`, `CHAMPIONSHIP`, `TOURNAMENT`, required when scheduling; older matches may have none). Played in 2 halves (default) or 1 -- a single period with no half time, for youth fixtures of one 20/25/30-minute period.
- **MatchCallUp**: List of players called up for a match. Binary for now (called up or not) -- the richer attendance-status model (Confirmed/Declined/Not Available/Standby) was removed since it required a parent/player self-confirmation path that doesn't exist yet; see `PROCESS_FLOWS.md` §11.
- **Live Match**: Real-time event logging by coaches (Goals, Assists, Substitutions, Cards).
- **MatchStatus**: `SCHEDULED`, `LIVE`, `HALF_TIME`, `FINISHED`, `CANCELLED`.

## 5b. Training & Periodization *(planned -- see Phase 8)*
- **Training sessions** belong to an **age group** (like the player pool and the staff), not to a team. Each session has a date, time, location and duration.
- **Exercises:** a session is made of exercises, each with a name and a description of what's done.
- **Player evaluations:** after a session, each player in the age group's pool gets a rating from **0 to 10, with decimals** (e.g. 7.5).
  - **0 means "did not take part"**, so the evaluation doubles as the attendance record. A player who trained always gets more than 0.
  - **Who can evaluate:** any staff member of the age group, coach or staff.
- **To-do:** once a session has ended, a to-do appears on the club Home ("Training of {date} needs player evaluations") until the evaluations are filled in, the same way a finished match asks for its report.
- **Periodization (planning cycles):**
  - **Macrocycle (year plan):** the season's plan for the age group: phases, main objectives and key dates.
  - **Mesocycle (month plan):** each month's focus and objectives within the year plan.
  - **Microcycle (week plan):** each week's plan, made of that week's sessions and their focus.
- **Open questions** to settle before building:
  - **Permissions:** who can create and edit sessions and plans: coach only, or staff too, and is it configurable like other actions (`ConfigurablePermission`)?
  - **Exercise library:** are exercises reusable across sessions?
  - **Visibility:** can players and parents see training ratings? Match ratings are staff-only.
  - **Session vs. weekly plan:** is a session always tied to a microcycle, or can it stand alone?
  - **Training stats:** should Player and Age Group Details show attendance and average rating?

## 6. Parent Features
- **Dashboard**: Quick view of children's upcoming events.
- **Calendar**: Aggregated view of all matches and training for their children.
- **Filters**: Capability to filter events by child.

## 7. Technical Stack
- **Architecture**: Clean Architecture (Domain, Data, Presentation).
- **Backend**: Kotlin, Ktor, Exposed (ORM), PostgreSQL (dev and production, different database names per environment), Koin (DI).
- **Frontend**: KMP (Android, iOS, Web), Compose Multiplatform.
- **Shared**: `:core` module for models and shared logic.

## 8. Development Roadmap
- [x] **Phase 1: Club Structure**: Database schema, models, and basic CRUD for Clubs.
- [x] **Phase 2: Authentication**: Register, Login, JWT, and initial Access Rights logic.
- [x] **Phase 3: Seasons, Teams & Players**: Full management of the club hierarchy.
    - [x] Season types (Calendar vs European) and automatic naming.
    - [x] Age Group management by birth-year span (single year, or two years for futsal-style groups).
    - [x] Player management (individual/batch) with eligibility checks.
    - [x] Team Staff management (Coaches/Staff assignment).
    - [x] Edit team: name and team size (the a-side format), from Team Details, for coordinators and the team's own coach.
    - [x] Edit age group: name, birth-year span and default team size, from Age Group Details, for coordinators. A span that would leave players already in the pool ineligible is refused.
- [x] **Phase 4: Match & Call-ups**: Calendar and attendance management.
    - [x] Opponent management (dedicated entity and table).
    - [x] Match creation and listing.
    - [x] Call-up management (batch player assignment).
- [x] **Phase 5: Live Match & Stats**: Real-time events and automated statistics calculation.
    - [x] Real-time broadcasting via **WebSockets**.
    - [x] Live Event logging (Goals, Assists, Cards, Subs).
    - [x] Live Score calculation.
    - [x] Live match console (client): kickoff line-up picker, live squad/events tabs, event logging & deletion, full-time summary. Pause/Resume freezes the running clock for a mid-half stoppage, and "Set minute" applies a manual correction -- both server-authoritative (persisted and broadcast), not client-clock-derived, and neither behind a confirmation dialog (lower-stakes, reversible). See `TECHNICAL_DECISIONS.md` §29.
    - [x] Club-wide Matches tab live-updates via a second, club-scoped WebSocket (`/clubs/{clubId}/matches/live`) — connects while the tab is on screen, disconnects on tab-switch or navigating into a match, reconnects + REST-refreshes on return. Fixture create/edit/delete/kickoff/status-change and per-goal score updates all broadcast live. See `TECHNICAL_DECISIONS.md` §28.
    - [x] The running clock formula is shared (`elapsedMatchSeconds` in `common/model/Models.kt`) between the live match console and the Matches list's live badge, so the two can never disagree, and both reflect a paused clock identically. See `TECHNICAL_DECISIONS.md` §29.
    - [x] Historical match statistics (Post-match analysis): per-player stats (started, minutes played including stoppage time, goals, assists, own goals, cards) are stored for every finished match, and a played match can no longer be deleted or have its events changed. The coach's post-match report (analysis plus 0–10 player ratings) has its editor, read view, Match Details card and Home to-do, and ratings are shown next to each player's stats. See `TECHNICAL_DECISIONS.md` §41. Season stats are shown outside the report too:
      - Player Details has season totals and a per-match list, with a season switcher.
      - Team Details has a season record (W/D/L, goals, form) and team leaders.
      - Team Details is split into Stats · Matches · Members tabs. The Members tab lists only the players who played for that team.
      - Age Group Details is split into Stats · Teams · Matches · Members tabs. Stats shows results by team and how minutes are spread across the pool.
      - Both can be filtered by match type (All / Competitive / Friendly), and ratings are shown to staff only (see `TECHNICAL_DECISIONS.md` §43).

    - [x] **Live minutes played** (design 36): the live console's Squad tab shows each player's minutes so far, live, with a bar against the fair share, the current spell ("on for 18'") or "off since", and amber flags for players below the share or not played yet; the bench sorts fewest minutes first. Substitutions are unlimited and in any direction (rolling subs in 7- and 9-a-side), and the substitution pickers list candidates with their minutes. Half time shows first-half totals and who's furthest below the share. The clock has a real pause button and is tapped to correct it. Full time shows a minutes table and links to the match report. See `TECHNICAL_DECISIONS.md` §45.
- [ ] **Phase 6: Dashboards & Aggregated Summaries**: Role-specific overview endpoints.
    - [/] **Super Admin Summary**: Global platform health (Total clubs, active users, system logs).
    - [/] **Club Admin Summary**: Club overview (Total players/teams, financial status placeholder, upcoming matches).
    - [/] **Parent/Player Summary**: Personalized view (My next matches, my recent performance/events).
    - [ ] **API Scalability**: Implement pagination for all list-based endpoints.
    - [x] **Email Service**: Integrated with Resend for club/age-group association notifications -- see `TECHNICAL_DECISIONS.md`'s EmailService section. `squadify.pt` is now a verified Resend sending domain (`RESEND_FROM_ADDRESS` set accordingly); no longer limited to the sandboxed `onboarding@resend.dev` address.
    - [x] **Club members list** (design 34): Club settings is split into Details · Members · Permissions tabs. Members lists everyone in the club by club role, with search, a role filter, what each person does (coach/staff age groups, parent of a player), and the pending club invites (resend, cancel). Invites can be sent as Member or Coordinator. Coordinators can change roles between Member and Coordinator; only admins can grant or remove Admin, and the last admin can't be demoted. Tapping a member opens Member details (design 35): contact (tap to mail or call, copy), the club role, and their age-group roles and linked players, which link to the age group or player. A pending invite opens with Resend and Cancel. Members can be removed from the club (their age-group staff roles go too; parent links stay).
    - [/] **Invite status visibility**:
      - **Club invites (done):** pending club invites are listed in Club settings → Members, with their intended role, how long ago they were sent, Resend and Cancel (see the Club members list item above). Age-group staff never need an invite of their own, because staff must already be club members, and a club member who hasn't registered yet shows up there as a pending invite.
      - **Still pending:** parent invites (`PARENT_PLAYER`, created from Create/Edit Player) aren't shown anywhere, so there's no way to tell a parent invite is outstanding. That belongs with the deferred parent/player work.
    - [/] **Push / In-App Notifications**: Provider chosen (Firebase Cloud Messaging, reusing the Firebase project already integrated for Crashlytics/App Distribution -- see `TECHNICAL_DECISIONS.md` §12). Android client built (token registration/removal, multiple notification channels, receiving) -- see §21/§22. **Verified working end to end on a real device.** Server-side sending covers both association events that already trigger an email (club membership added, age-group staff added -- §23), nine club-wide events sent to every club member rather than just the people directly affected (§24/§26): the five live-match moments (kickoff, goal, half-time, second half, full-time), three schedule changes (a fixture created, rescheduled, or cancelled), and a team being created under an age group. Environments (dev/staging/prod) are isolated from each other at the Firebase level too, not just by database (§25). `FIREBASE_SERVICE_ACCOUNT_JSON` is set on the Railway **staging** server, and pushes are confirmed working there. Still pending: the same credential on the production Railway server, in-app (non-push) notifications, and iOS/Desktop/Web support.
    - [ ] **Parent/Player Notifications** (deferred -- club features come first): the developer explicitly deferred these, distinct from the club-wide match pushes above since they'd need to resolve `UserPlayers` links (a player's parent), not `ClubMembers`. Candidates raised in conversation: a player called up for a match (`ManageCallUpsUseCase`), a player added to an age group's pool (`AddPlayersToAgeGroupUseCase`, the direct parallel to the club-membership/age-group-staff pushes in §23, but for players). Not started.
    - [x] **Production Database**: Migrated from H2 to PostgreSQL for both dev and production, distinguished by database name (`DATABASE_NAME`, defaulting to `squadify_dev`/`squadify_production`) — see `TECHNICAL_DECISIONS.md` §3.
- [/] **Phase 7: Client Applications (KMP)**: Multiplatform UI development.
    - [x] Initial Project Setup (Shared, Android, iOS).
    - [x] Multi-language support (EN, PT) implementation.
    - [x] Shared UI components & Themes.
    - [ ] Dynamic Club Theming (Adaptive UI based on club colors).
    - [/] Authentication flows (Login/Register): login, register, session check on launch, automatic token refresh on 401, and logout are done. Still pending: "Forgot password?" is shown but has no flow (and no reset endpoint).
    - [x] Account Management: User profile viewing and editing. The Account screen edits name and phone and changes the password (`TECHNICAL_DECISIONS.md` §33); "Change email" is shown but not functional yet (next item).
    - [ ] **Change email**: change the account's email, confirmed through a link sent to the new address. It can reuse the Forgot-password token-and-email mechanism once that exists.
    - [ ] **Leave club**: a member leaves a club themselves -- the counterpart of Remove from club, which never applies to yourself. Same rules: the last admin can't leave, and parent links to players stay.
    - [ ] **Club logo upload**: "Add logo" in Club settings does nothing yet. This is the app's first image upload, so it needs storage, resizing and serving images. It goes together with Dynamic Club Theming.
    - [x] Client-side Architecture (MVVM, Koin, UseCases).
    - [x] API Integration (Ktor Client): every screen talks to the server through repositories over a shared authenticated Ktor client (bearer tokens, refresh on 401), plus WebSockets for the live match and the club's Matches tab.
    - [/] Role-specific Dashboards:
      - **Done:** "My clubs" lists the user's clubs (opening straight into the only one when there's just one). Each club opens on a Home tab with the season's headline numbers (teams, players, coaches, matches this week), what's next up, and alerts for the people who manage it (e.g. a match missing call-ups or a report).
      - **Still pending:** the server's super-admin, club-admin and parent dashboard endpoints (Phase 6) have no client screens yet.
    - [x] Club "More" panel (design 37): a full-screen panel that slides in from the right, replacing the old "More" dropdown and the header's account icon.
      - **Account header:** avatar, name, email, role chip and club name; tapping it opens Account.
      - **Grouped rows by permission:** coordinators see every create action. A coach sees Create team and Create players for their own age groups, as far as the club's Permissions allow.
      - **Account rows:** Account settings, Switch club (2+ clubs) and Log out (with a confirmation).
      - **Players:** the club's player list opens as its own screen, with season, age-group and search filters.
    - [x] Match center (Live updates & History):
      - **Matches tab:** the club's Matches tab updates live.
      - **Match Details:** line-ups, call-ups, events and score.
      - **Live match console:** for coaches and staff.
      - **After the match:** finished results stay as history, with the post-match report and per-player stats. Season stats live on Player, Team and Age Group Details (see Phase 5).
- [ ] **Phase 8: Training & Periodization** (see §5b).
    - [ ] Training sessions per age group: create, edit, list and calendar (date, time, location, duration).
    - [ ] Exercises with descriptions within a session.
    - [ ] Player evaluations per session, 0–10 with decimals (0 = did not take part), by any staff member of the age group.
    - [ ] Home to-do when an ended session has players still to evaluate.
    - [ ] Macrocycle (year plan), mesocycles (month plans) and microcycles (week plans) for an age group.
    - [ ] Training stats (attendance, average rating) on Player and Age Group Details, if wanted.
