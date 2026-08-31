# Football Manager Platform - Requirements

This document outlines the core requirements and architectural decisions for the Football Manager platform.

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
`Club -> Age Group -> Team -> (Players / Coaches / Staff)`
- An **Age Group** (e.g., Sub-18) can contain multiple **Teams** (e.g., Sub-18 A, Sub-18 B).
- Entities like **Team** and **Age Group** are tied to a **Season** to maintain historical data.

## 3. Core Entities
- **User**: Account data (email, phone, name, avatar, status).
- **Club**: Branding and contact info.
- **Season**: Time-bound periods. Supports two types:
    - **Calendar Year**: January 1st to December 31st (e.g., Brazil).
    - **European Year**: July 1st to June 30th (e.g., Europe).
- **Age Group**: Categorization of players by age.
- **Team**: Specific groups within age groups.
- **Player**: Personal data and stats.
- **Opponent**: Management of external clubs/teams for matches (name, short name, logo).
- **ClubMember**: Links Users to Clubs with a specific Role.
- **TeamPlayer**: Links Players to Teams (handles transfers).
- **UserPlayer**: Links Parents to Players (supports multiple guardians).

## 4. Authentication & Security
- **Authentication**: JWT (JSON Web Tokens) with Refresh Token flow.
- **Password Security**: Hashing using BCrypt.
- **Access Rights**: 
    - Authorization is checked at the API level.
    - **Super Admin**: Global "God Mode" access to all clubs and platform data.
    - **Admins/Coordinators**: Full access within their specific club context.
    - **Coaches**: Can only manage teams where they are assigned as staff.
- **Error Handling**: Standardized `ErrorResponse` JSON for all failures, using a custom `FootballManagerException` hierarchy.

## 5. Match Management
- **Match**: Details about opponent (linked to Opponent entity), location, date, and status.
- **MatchCallUp**: List of players called for a match with attendance status (Called, Confirmed, Injured, etc.).
- **Live Match**: Real-time event logging by coaches (Goals, Assists, Substitutions, Cards).
- **MatchStatus**: `SCHEDULED`, `LIVE`, `HALF_TIME`, `FINISHED`, `CANCELLED`.

## 6. Parent Features
- **Dashboard**: Quick view of children's upcoming events.
- **Calendar**: Aggregated view of all matches and training for their children.
- **Filters**: Capability to filter events by child.

## 7. Technical Stack
- **Architecture**: Clean Architecture (Domain, Data, Presentation).
- **Backend**: Kotlin, Ktor, Exposed (ORM), PostgreSQL (Production), H2 (Local Dev), Flyway (Migrations), Koin (DI).
- **Frontend**: KMP (Android, iOS, Web), Compose Multiplatform.
- **Shared**: `:core` module for models and shared logic.

## 8. Development Roadmap
- [x] **Phase 1: Club Structure**: Database schema, models, and basic CRUD for Clubs.
- [x] **Phase 2: Authentication**: Register, Login, JWT, and initial Access Rights logic.
- [x] **Phase 3: Seasons, Teams & Players**: Full management of the club hierarchy.
    - [x] Season types (Calendar vs European) and automatic naming.
    - [x] Age Group management by birth year.
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
    - [ ] Historical match statistics (Post-match analysis).
- [ ] **Phase 6: Dashboards & Aggregated Summaries**: Role-specific overview endpoints.
    - [/] **Super Admin Summary**: Global platform health (Total clubs, active users, system logs).
    - [/] **Club Admin Summary**: Club overview (Total players/teams, financial status placeholder, upcoming matches).
    - [/] **Parent/Player Summary**: Personalized view (My next matches, my recent performance/events).
    - [ ] **API Scalability**: Implement pagination for all list-based endpoints.
    - [ ] **Email Service**: Integration with an external provider for real-time invitation delivery.
    - [ ] **Production Database**: Migrate from H2 to a persistent PostgreSQL environment.
- [/] **Phase 7: Client Applications (KMP)**: Multiplatform UI development.
    - [x] Initial Project Setup (Shared, Android, iOS).
    - [x] Multi-language support (EN, PT) implementation.
    - [/] Shared UI components & Themes.
    - [ ] Dynamic Club Theming (Adaptive UI based on club colors).
    - [/] Authentication flows (Login/Register).
    - [ ] Role-specific Dashboards.
    - [ ] Match center (Live updates & History).
