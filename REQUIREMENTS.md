# Squadify Platform - Requirements

This document outlines the core requirements and architectural decisions for the Squadify platform.

## 1. User Profiles & Roles
The application supports five primary user types:
- **Platform Owner (Super Admin)**: Global owner of the platform. Can create (provision) clubs and manage platform settings.
- **Club Admin / Coordinator (Director)**: Global club management, assigned by the Platform Owner.
- **Coach**: Management of assigned teams, matches, and call-ups.
- **Staff**: Specific functional tasks (Physio, Team Manager).
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
- **Match**: Details about opponent (linked to Opponent entity), location, date, and status.
- **MatchCallUp**: List of players called up for a match. Binary for now (called up or not) -- the richer attendance-status model (Confirmed/Declined/Not Available/Standby) was removed since it required a parent/player self-confirmation path that doesn't exist yet; see `PROCESS_FLOWS.md` §11.
- **Live Match**: Real-time event logging by coaches (Goals, Assists, Substitutions, Cards).
- **MatchStatus**: `SCHEDULED`, `LIVE`, `HALF_TIME`, `FINISHED`, `CANCELLED`.

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
- [x] **Phase 4: Match & Call-ups**: Calendar and attendance management.
    - [x] Opponent management (dedicated entity and table).
    - [x] Match creation and listing.
    - [x] Call-up management (batch player assignment).
- [/] **Phase 5: Live Match & Stats**: Real-time events and automated statistics calculation.
    - [x] Real-time broadcasting via **WebSockets**.
    - [x] Live Event logging (Goals, Assists, Cards, Subs).
    - [x] Live Score calculation.
    - [x] Live match console (client): kickoff line-up picker, live squad/events tabs, event logging & deletion, full-time summary.
    - [ ] Historical match statistics (Post-match analysis) — the summary screen's "Minutes played" row is a placeholder, no per-player breakdown yet.
- [ ] **Phase 6: Dashboards & Aggregated Summaries**: Role-specific overview endpoints.
    - [/] **Super Admin Summary**: Global platform health (Total clubs, active users, system logs).
    - [/] **Club Admin Summary**: Club overview (Total players/teams, financial status placeholder, upcoming matches).
    - [/] **Parent/Player Summary**: Personalized view (My next matches, my recent performance/events).
    - [ ] **API Scalability**: Implement pagination for all list-based endpoints.
    - [x] **Email Service**: Integrated with Resend for club/age-group association notifications -- see `TECHNICAL_DECISIONS.md`'s EmailService section. `squadify.pt` is now a verified Resend sending domain (`RESEND_FROM_ADDRESS` set accordingly); no longer limited to the sandboxed `onboarding@resend.dev` address.
    - [ ] **Invite status visibility**: `AgeGroupDetailsScreen`'s Staff (and equivalent Players) list only shows users who have actually registered (`AgeGroupStaffMember`) -- someone invited by email (`Invite` model / `InviteRepository.findByEmail`, `UserStatus.PENDING`) but who hasn't created an account yet doesn't appear anywhere in the UI, so there's no way to tell an invite was ever sent or whether it's still outstanding. Needs: surface pending invites alongside registered members (e.g. an "Invited" row/badge distinct from active members), and a resend-invite action per pending row.
    - [ ] **Push / In-App Notifications**: Deliberately deferred, per the developer -- the same association events that now trigger an email (club membership added, age-group staff added) should eventually also fire a push/in-app notification. Not started; no provider chosen yet (candidates: Firebase Cloud Messaging, given Firebase is already integrated for Crashlytics/App Distribution -- see `TECHNICAL_DECISIONS.md` §12).
    - [x] **Production Database**: Migrated from H2 to PostgreSQL for both dev and production, distinguished by database name (`DATABASE_NAME`, defaulting to `squadify_dev`/`squadify_production`) — see `TECHNICAL_DECISIONS.md` §3.
- [/] **Phase 7: Client Applications (KMP)**: Multiplatform UI development.
    - [x] Initial Project Setup (Shared, Android, iOS).
    - [x] Multi-language support (EN, PT) implementation.
    - [x] Shared UI components & Themes.
    - [ ] Dynamic Club Theming (Adaptive UI based on club colors).
    - [/] Authentication flows (Login/Register).
    - [ ] Account Management: User profile viewing and editing.
    - [x] Client-side Architecture (MVVM, Koin, UseCases).
    - [ ] API Integration (Ktor Client).
    - [ ] Role-specific Dashboards.
    - [ ] Match center (Live updates & History).
