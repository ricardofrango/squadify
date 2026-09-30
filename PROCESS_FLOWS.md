# Process Flows - Squadify Platform

This document maps every use case under `server/src/main/kotlin/.../domain/usecase` to a flowchart of its current, as-implemented logic. It complements [REQUIREMENTS.md](./REQUIREMENTS.md) (what the system should do) and [TECHNICAL_DECISIONS.md](./TECHNICAL_DECISIONS.md) (why it's built this way) with a process-level view: what each use case actually does today, branch by branch.

Generated from a read-through of all use cases on 2026-09-02. Per `AI_GUIDELINES.md`, this file should be revisited whenever a use case's business logic or flow changes.

## How to read these diagrams

- Rectangles are actions or steps.
- Diamonds are decision points taken directly from the code's `if`/`when` branches.
- Stadium shapes (`([...])`) are terminal outcomes: a return value or a thrown exception.
- Every use case wraps its body in `transactionManager.runInTransaction { }` (atomic commit/rollback) — omitted from each diagram for brevity, but assume it wraps the whole flow.

## Table of Contents

1. [Shared Building Blocks](#1-shared-building-blocks)
2. [Authentication & Session](#2-authentication--session)
3. [Platform & Club Administration](#3-platform--club-administration)
4. [Club Structure: Seasons, Age Groups, Teams](#4-club-structure-seasons-age-groups-teams)
5. [Player & Roster Management](#5-player--roster-management)
6. [Opponents](#6-opponents)
7. [Match Management & Call-ups](#7-match-management--call-ups)
8. [Live Match Engine](#8-live-match-engine)
9. [Dashboards](#9-dashboards)
10. [Cross-Cutting: The Invite System, End to End](#10-cross-cutting-the-invite-system-end-to-end)
11. [State of the Art: Observed Gaps & Inconsistencies](#11-state-of-the-art-observed-gaps--inconsistencies)
12. [Database Schema: Current State of the Art](#12-database-schema-current-state-of-the-art)

---

## 1. Shared Building Blocks

Almost every use case starts by fetching a parent resource for context, then delegates authorization to one of two shared use cases. These two flows are the backbone of the whole permission model described in `TECHNICAL_DECISIONS.md` §5.

### 1.1 `CheckUserPermissionUseCase` — the core authorization gate

```mermaid
graph TD
    A[Start: userId, clubId, minClubRole?, minTeamRole?, configurablePermission?, teamId?, ageGroupId?] --> B[Load user by id]
    B -- not found --> X1(["Throw ForbiddenException: User not found"])
    B -- found --> C{user.isPlatformAdmin?}
    C -- Yes --> ALLOW(["Allow — God Mode"])
    C -- No --> R["Resolve age-group context: ageGroupId, else team.ageGroupId for teamId"]
    R --> D[Get user's ClubRole for clubId]
    D --> E{Has a club role?}
    E -- No --> RESOLVE
    E -- Yes --> F{Role is CLUB_ADMIN or COORDINATOR?}
    F -- Yes --> G{minClubRole is null OR role.level >= minClubRole.level?}
    G -- Yes --> ALLOW
    G -- No --> RESOLVE
    F -- No --> I{minClubRole == MEMBER AND role.level >= MEMBER.level AND no age-group context?}
    I -- Yes --> ALLOW
    I -- No --> RESOLVE["configurablePermission given? resolve effective minTeamRole: club's override if a row exists (its minTeamRole as-is, even null) else the action's default; else use the literal minTeamRole as passed"]
    RESOLVE --> H{age-group context AND effective minTeamRole not null?}
    H -- No --> P{allowPlayerOrParentAccess?}
    H -- Yes --> J["Get user's role in the age group (age_group_staff)"]
    J --> K{role exists AND level >= effective minTeamRole.level?}
    K -- Yes --> ALLOW
    K -- No --> P
    P -- No --> DENY(["Throw ForbiddenException: errorMessage"])
    P -- Yes --> L["Linked players (user_players) in this club?"]
    L -- none --> DENY
    L -- some, no age-group context --> ALLOW
    L -- some --> M{a linked player is in the age group's pool (age_group_players)?}
    M -- Yes --> ALLOW
    M -- No --> DENY
```

Key behaviours baked into this one function: Platform Admins bypass everything ("God Mode"); a club-level `CLUB_ADMIN`/`COORDINATOR` role satisfies almost any check without needing a lower-level role; a plain `MEMBER` role is only sufficient when no age-group-specific check is requested; `COACH`/`STAFF` roles are validated per **age group** via `age_group_staff` (a `teamId` passed by a caller is resolved to its age group first); the opt-in player/parent path grants a read when a linked player sits in the relevant age group's pool. `configurablePermission` (mutually exclusive with a literal `minTeamRole` at any one call site) resolves the effective staff-role threshold from the club's own `ClubPermissionOverrides` setting instead of a hardcoded constant — see `TECHNICAL_DECISIONS.md` §35 for the full configurable-permissions design, including which actions are (and deliberately aren't) configurable, and the `ClubPermissionRepository.getOverride` row-exists-vs-null distinction that a naive `?:` chain would get wrong.

### 1.2 `CheckUserIsAdminUseCase` — platform-level gate

```mermaid
graph TD
    A[Start: userId] --> B[Load user by id]
    B -- not found --> X(["Throw ForbiddenException: User not found"])
    B -- found --> C{user.isPlatformAdmin?}
    C -- Yes --> T(["Return true"])
    C -- No --> F(["Return false"])
```

Used directly by `ProvisionClubUseCase`, `GetClubsUseCase`, and `GetSuperAdminDashboardUseCase`.

### 1.3 The standard "permission-gated read" pattern

The majority of `Get*UseCase` classes share one exact shape:

```mermaid
graph TD
    A[Start: userId, resourceId] --> B{Does the use case need a parent resource?}
    B -- Yes --> C[Fetch parent resource by id]
    C -- not found --> N(["Throw NotFoundException / ValidationException"])
    C -- found --> D
    B -- No, clubId given directly --> D[CheckUserPermissionUseCase — minClubRole=MEMBER, optional teamId]
    D -- denied --> F(["Throw ForbiddenException"])
    D -- allowed --> R[Fetch and return data from repository]
```

Use cases that follow this pattern exactly (fetch-context → require at least `MEMBER` → return): `GetClubDetailsUseCase`, `GetAgeGroupsUseCase`, `GetSeasonsUseCase`, `GetTeamsUseCase`, `GetTeamDetailsUseCase` (adds aggregation + a `canManageMatch` probe — see §4.6b), `GetTeamPlayersUseCase`, `GetAgeGroupPlayersByClubUseCase`, `GetAgeGroupStaffUseCase`, `GetPlayersUseCase`, `GetPlayerUseCase` (adds aggregation + a `canEditPlayer` probe — see §5.1b), `GetMatchesUseCase`, `GetMatchDetailsUseCase` (adds aggregation + `canManageMatch`/`canManageCallUps`/`canManageLiveMatch` probes — see §7.1b), `GetMatchEventsUseCase`, `GetMatchScoreUseCase`, `GetOpponentsUseCase`, `GetLiveMatchUpdatesUseCase` (terminal step is "subscribe to the live bus" instead of "return data" — see §8.3), and `GetClubMatchesUpdatesUseCase` (same, but with no match to fetch first and no `matchId` context — see §8.5).

Their individual diagrams are omitted below to avoid 15 near-identical copies; only the use cases with distinct branching logic get a dedicated diagram.

---

## 2. Authentication & Session

### 2.1 Register (`RegisterUseCase`)

```mermaid
graph TD
    A[Start: email, password, phone, firstName, lastName] --> B{email or password blank?}
    B -- Yes --> V(["Throw ValidationException"])
    B -- No --> C[Find user by email]
    C -- exists --> CN(["Throw ConflictException: user already exists"])
    C -- not exists --> D[Hash password with BCrypt]
    D --> E[Create User — status ACTIVE]
    E --> F[Find pending Invites for this email]
    F --> G{Any invites?}
    G -- No --> Z(["Return created User"])
    G -- Yes --> H{For each invite, by type}
    H -- CLUB_MEMBER --> I["Collect clubId + role pair (several for one club: keep the highest role)"]
    H -- PARENT_PLAYER --> J{playerId and relationship present?}
    J -- Yes --> K[Collect userId + playerId + relationship link]
    J -- No --> SKIP[Skip invite]
    I --> L[Add user as ClubMember once per club]
    K --> M[Create UserPlayer link for every collected triple]
    L --> N[Delete all processed invites]
    M --> N
    N --> Z
```

### 2.2 Login (`LoginUseCase`)

```mermaid
graph TD
    A[Start: email, password] --> B{email or password blank?}
    B -- Yes --> V(["Throw ValidationException"])
    B -- No --> C[Find user by email]
    C -- not found --> U(["Throw UnauthorizedException: invalid email or password"])
    C -- found --> D[Find password hash by email]
    D -- not found --> U
    D -- found --> E{BCrypt.checkpw matches?}
    E -- No --> U
    E -- Yes --> F[Generate Access Token — 48h]
    F --> G[Generate Refresh Token — 30d]
    G --> H[Save Refresh Token to DB]
    H --> Z(["Return accessToken, refreshToken, user"])
```

### 2.3 Refresh Token (`RefreshTokenUseCase`)

```mermaid
graph TD
    A[Start: refreshToken] --> B[Load stored refresh token record]
    B -- not found --> U(["Throw UnauthorizedException: invalid refresh token"])
    B -- found --> C{expires_at < now?}
    C -- Yes --> D[Delete the expired token] --> U2(["Throw UnauthorizedException: refresh token expired"])
    C -- No --> E[Delete the old refresh token]
    E --> F[Generate new Access Token]
    F --> G[Generate new Refresh Token]
    G --> H[Save new Refresh Token to DB — expires 30 days out]
    H --> Z(["Return new access + refresh token pair"])
```

Note: the repository (`findRefreshToken`) just retrieves the row as-is; the expiry decision is a business rule owned by the use case, which deletes an expired token as it rejects it. Rotation deletes the presented token on the success path too. Covered by `RefreshTokenUseCaseTest` and `JwtConfigTest`.

### 2.4 Platform Admin Seeding (`SeedPlatformAdminUseCase`) — startup, not request-driven

Runs once from `Application.module()` (right after Koin is installed), so a freshly-created or wiped **dev** database is immediately usable with no manual bootstrapping. Idempotent — never mutates an existing account.

```mermaid
graph TD
    A[Server startup] --> B{seeding enabled?}
    B -- No --> Z(["Skip — log and return"])
    B -- Yes --> C{email and password configured?}
    C -- No --> Z2(["Skip — warn and return"])
    C -- Yes --> D[Find user by configured email]
    D -- exists --> Z3(["Skip — admin already present"])
    D -- not exists --> E[Hash password with BCrypt]
    E --> F[Create User — isPlatformAdmin = true, status ACTIVE]
    F --> Z4(["Log 'Seeded platform admin'"])
```

Config comes from `ServerConfig` and is injected into the use case: `DEV` enables it by default with labelled fallback credentials (`admin@local.dev` / `admin1234`); `PRODUCTION` keeps it off unless `SEED_ADMIN_ENABLED=true`, which then makes `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` mandatory (no password fallback in production). Covered by `SeedPlatformAdminUseCaseTest`. See `TECHNICAL_DECISIONS.md` §5.

### 2.5 Push Notification Token Registration (`RegisterFcmTokenUseCase` / `RemoveFcmTokenUseCase`)

First piece of push notifications (Android, per the developer -- see `TECHNICAL_DECISIONS.md` §21). No
`CheckUserPermissionUseCase` call in either use case: `userId` always comes from the caller's own JWT
(`call.userId`, never a client-supplied parameter), so there's no cross-user access to guard against --
the route's `authenticate("auth-jwt")` block is the entire authorization boundary.

```mermaid
graph TD
    A[Start: userId from JWT, token] --> B{token blank?}
    B -- Yes --> V(["Throw ValidationException"])
    B -- No --> C["Upsert fcm_tokens on the token's own uniqueness -- reassigns user_id if this token was already registered to someone else, otherwise inserts"]
    C --> Z(["Return success"])
```

```mermaid
graph TD
    A2[Start: userId from JWT, token] --> B2["Delete fcm_tokens where user_id = userId AND token = token"]
    B2 --> Z2(["Return success -- always, even if zero rows matched"])
```

`POST /v1/fcm-tokens` registers the caller's device; `DELETE /v1/fcm-tokens` (token in the body, not the
path -- FCM tokens are opaque strings up to ~4096 chars, not idiomatic as a URL segment) removes it,
called by the client as part of its own logout flow. There is no server-side "logout" concept otherwise
-- the client's `LogoutUseCase` just clears its local session (see §2.2's login/refresh pair; nothing
revokes a refresh token early today) -- so token removal is deliberately its own explicit, idempotent
endpoint the client calls itself, rather than something bolted onto a logout use case that doesn't exist
yet. Removing an unknown or already-removed token, or one that belongs to a different user (e.g. called
after the upsert above already reassigned it elsewhere), is a no-op, not an error -- a client's logout
flow never has to branch on this succeeding.

Verified against a real running server, not just compilation: registered a token as one user, re-registered
the identical token value as a second user (confirmed exactly one row, reassigned), confirmed the first
user's delete attempt was a no-op (row untouched), confirmed the second (actual owner) user's delete
removed it, confirmed a repeat delete stayed a no-op success, and confirmed an unauthenticated request is
rejected (401) and a blank token is rejected (400). Covered by `RegisterFcmTokenUseCaseTest` and
`RemoveFcmTokenUseCaseTest`.

### 2.6 Get Current User (`GetCurrentUserUseCase`)

Backs the Account screen's own profile load. No `CheckUserPermissionUseCase` call, same reasoning as §2.5: `userId` is always the caller's own JWT subject, never a parameter, so there's no cross-user access to guard.

```mermaid
graph TD
    A[Start: userId from JWT] --> B[Find user by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> Z(["Return User"])
```

### 2.7 Update Profile (`UpdateProfileUseCase`)

```mermaid
graph TD
    A[Start: userId from JWT, firstName, lastName, phone?] --> B{firstName or lastName blank?}
    B -- Yes --> V(["Throw ValidationException"])
    B -- No --> C["Update firstName, lastName, phone (blank phone normalized to null)"]
    C --> D[Re-fetch and return updated User]
```

`email`, `status`, and `isPlatformAdmin` aren't fields on `UpdateProfileRequest` at all — there's no self-service path to them through this use case, by construction rather than by an extra check.

### 2.8 Change Password (`ChangePasswordUseCase`)

```mermaid
graph TD
    A[Start: userId from JWT, currentPassword, newPassword] --> B{newPassword shorter than 8 chars?}
    B -- Yes --> V(["Throw ValidationException"])
    B -- No --> C[Find user by id]
    C -- not found --> N(["Throw NotFoundException"])
    C -- found --> D[Find password hash by userId]
    D --> E{BCrypt.checkpw currentPassword matches?}
    E -- No --> U(["Throw UnauthorizedException: current password is incorrect"])
    E -- Yes --> F[Hash newPassword with BCrypt]
    F --> G[Update stored password hash]
    G --> Z(["Return success"])
```

Same `BCrypt.checkpw` gate `LoginUseCase` uses for the current-password check, so a stolen access token alone can't be used to lock the real account owner out. Both use cases covered by `UpdateProfileUseCaseTest`/`ChangePasswordUseCaseTest`. See `TECHNICAL_DECISIONS.md` §33.

---

## 3. Platform & Club Administration

### 3.1 Provision Club (`ProvisionClubUseCase`) — Super Admin only

```mermaid
graph TD
    A[Start: executorUserId, clubName, directorEmail, sport] --> B[CheckUserIsAdminUseCase]
    B -- False --> F(["Throw ForbiddenException: only platform admins"])
    B -- True --> C[Create Club — status ACTIVE, sport from request]
    C --> D[Find user by directorEmail]
    D -- exists --> E[Add existing user as ClubMember, role CLUB_ADMIN]
    D -- not exists --> G[Create Invite — type CLUB_MEMBER, role CLUB_ADMIN]
    E --> H["After commit: EmailService.sendClubMembershipAdded"]
    G --> I["After commit: EmailService.sendClubInvite"]
    H --> Z(["Return created Club"])
    I --> Z
```

Email is sent after the transaction commits, not inside it (see TECHNICAL_DECISIONS.md §14) — a failed/slow send never rolls back the club or the membership/invite.

### 3.2 Get Clubs (`GetClubsUseCase`)

```mermaid
graph TD
    A[Start: userId] --> B[CheckUserIsAdminUseCase]
    B -- True --> C(["Return ALL clubs — God Mode"])
    B -- False --> D(["Return clubs where user is a member OR a parent — single exists-subquery"])
```

### 3.3 Get Club Details (`GetClubDetailsUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId] --> B[CheckUserPermissionUseCase — minClubRole=MEMBER, allowPlayerOrParentAccess=true]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C[Fetch Club by id]
    C -- not found --> N(["Throw NotFoundException"])
    C -- found --> D["Probe CheckUserPermissionUseCase — minClubRole=COORDINATOR (non-throwing: ForbiddenException caught to false)"]
    D --> Z(["Return ClubDetailsResponse — club + canEditClub"])
```

`canEditClub` from the same single probe above — `CreateSeasonUseCase` and `CreateAgeGroupsUseCase` enforce the identical `minClubRole=COORDINATOR` gate, so one non-throwing check covers both response fields (they're separate fields, not one shared boolean, only so either could diverge later without an API shape change). The client uses them to distinguish, when a club has zero seasons or zero age groups, an admin/coordinator ("nothing here yet, create one") from a plain member ("nothing here yet") — see §4.1 and §4.3.

### 3.3b Get Club Home (`GetClubHomeUseCase`)

The aggregate behind the club screen's default **Home** tab: stat-grid counts, one "Next up" row per team with an upcoming match, and role-scoped "Needs you" alerts. `GET /v1/clubs/{clubId}/home`, readable by any club member.

```mermaid
graph TD
    A[Start: userId, clubId] --> B[CheckUserPermissionUseCase — minClubRole=MEMBER, allowPlayerOrParentAccess=true]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C[Fetch club; resolve role + season (active, else latest-ending)]
    C --> D["Counts: teams, players, coaches; matchesThisWeek = club matches whose date is in the Mon–Sun week containing today"]
    D --> E["Next up: per team, soonest SCHEDULED future match → opponent name + call-up count"]
    E --> G{role >= COORDINATOR or platform admin?}
    G -- Yes --> H["alert per season age group with no COACH in age_group_staff (AGE_GROUP_NEEDS_COACH)"]
    G -- No --> I
    H --> I{coaches any season age group?}
    I -- Yes --> J["group that age group's SCHEDULED matches within 5 days that have zero call-ups, one CALLUPS_UNSENT alert per day"]
    I -- No --> Z
    J --> Z(["Return ClubHome — club, seasonName, role, counts, nextUp, alerts"])
```

Alerts carry structured parts (`ageGroupName`, `date`, `count`) rather than rendered strings, so the client formats them in the user's language. Someone who is both a coordinator and an age-group coach gets both alert types.

### 3.4 Update Club (`UpdateClubUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId, patch fields] --> B{name is blank (non-null but empty/whitespace)?}
    B -- Yes --> V(["Throw ValidationException"])
    B -- No --> C[Fetch Club by id]
    C -- not found --> N(["Throw NotFoundException"])
    C -- found --> D[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    D -- denied --> F(["Throw ForbiddenException"])
    D -- allowed --> E["Merge patch fields over existing (null = keep current, incl. stadiumLocation)"]
    E --> Z(["Persist updated Club"])
```

(Corrected from an earlier revision of this doc that said `minClubRole=CLUB_ADMIN` — the code has required only `COORDINATOR` since admin/coordinator were made fully equivalent short of billing; see `UpdateClubUseCaseTest`.) `stadiumLocation` — where home matches are actually played, when that differs from the official `address` — is edited from the client's Edit Club Details screen (Club Details' "More" menu); see `TECHNICAL_DECISIONS.md` §32.

### 3.4b Get / Update Club Permissions (`GetClubPermissionsUseCase` / `UpdateClubPermissionsUseCase`)

The Edit Club Details screen's "Permissions" tab — see `TECHNICAL_DECISIONS.md` §35 for the full design (which actions are configurable, and why three of them gained a `minClubRole` floor they never had before as part of this change).

```mermaid
graph TD
    A[Start: userId, clubId] --> B[CheckUserPermissionUseCase — minClubRole=MEMBER]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C[Fetch this club's explicit overrides]
    C --> D["For each of the 8 ConfigurablePermission values: override's minTeamRole if a row exists, else the action's default"]
    D --> Z(["Return one ClubPermissionSetting per action, always 8"])
```

```mermaid
graph TD
    A2[Start: userId, clubId, settings list] --> B2[CheckUserPermissionUseCase — minClubRole=COORDINATOR, no configurablePermission]
    B2 -- denied --> F2(["Throw ForbiddenException"])
    B2 -- allowed --> C2{Any duplicate action in the settings list?}
    C2 -- Yes --> V2(["Throw ValidationException"])
    C2 -- No --> D2["Delete all of this club's existing overrides, then insert exactly the submitted settings"]
    D2 --> Z2(["Return success"])
```

Update is deliberately a full replace, not a per-action patch — the client always submits the whole
8-action form. `GET` is readable by any club member; `PATCH` is fixed at `minClubRole=COORDINATOR` with no
`configurablePermission` involved anywhere in its own check — changing a setting is never itself one of
the settings.

### 3.5 Update Club Member Role (`UpdateClubMemberRoleUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId, targetUserId, newRole] --> B[CheckUserPermissionUseCase — minClubRole=CLUB_ADMIN]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C["Update role directly in club_members (no existence check on targetUserId)"]
    C --> Z(["Return success"])
```

### 3.6 Add Club Members (`AddClubMembersUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId, emails list] --> B[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C[Dedupe emails]
    C --> D[Batch find existing Users by email]
    D --> E[Batch fetch existing club roles for matched users]
    E --> G{For each email}
    G -- user exists, not yet a member --> H[Queue new ClubMember, role MEMBER]
    G -- user exists, already a member --> SKIP[Skip]
    G -- user does not exist --> I[Queue CLUB_MEMBER Invite, role MEMBER]
    H --> J[Batch insert new members]
    I --> K[Batch insert invites]
    J --> L["After commit: EmailService.sendClubMembershipAdded per new member"]
    K --> M["After commit: EmailService.sendClubInvite per invited email"]
    L --> Z(["Return true"])
    M --> Z
```

Same after-commit timing as 3.1 above — see TECHNICAL_DECISIONS.md §14.

---

## 4. Club Structure: Seasons, Age Groups, Teams

### 4.1 Create Season (`CreateSeasonUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId, type, startDate, endDate, name?] --> B[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> V{startDate > endDate?}
    V -- Yes --> VN(["Throw ValidationException: start date after end date"])
    V -- No --> C[Find existing season with same club + type + dates]
    C -- exists --> CN(["Throw ConflictException: duplicate season"])
    C -- not exists --> D{name provided?}
    D -- Yes --> H[Use provided name]
    D -- No --> E{Season type?}
    E -- EUROPEAN_YEAR --> E1["Infer name as YY/YY (start/end year)"]
    E -- CALENDAR_YEAR --> E2[Infer name as start year]
    H --> I[Create Season — active=true]
    E1 --> I
    E2 --> I
    I --> Z(["Persist Season"])
```

The client's `DatePickerSheet` (`minDate`/`maxDate`) already keeps the start/end ordering from happening in normal use — picking an end date before the current start date (or a start date after the current end date) simply isn't offered in the picker — but the server validates it independently since the client can't be trusted alone. Covered by `CreateSeasonUseCaseTest`.

### 4.2 Update Season (`UpdateSeasonUseCase`)

```mermaid
graph TD
    A[Start: userId, seasonId, patch] --> B[Fetch Season by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["Merge patch fields (name, type, dates, active)"]
    D --> Z(["Persist updated Season"])
```

### 4.3 Create Age Groups (`CreateAgeGroupsUseCase`) — batch

```mermaid
graph TD
    A[Start: userId, clubId, seasonId, items list] --> B[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C{items list empty?}
    C -- Yes --> V(["Throw ValidationException"])
    C -- No --> D[Load existing age groups for this club + season]
    D --> D2[Load club — for its sport's allowed team sizes]
    D2 -- not found --> V2b(["Throw ValidationException"])
    D2 --> E[Collect existing startYear/endYear pairs as a Set of taken ranges]
    E --> G{For each item}
    G -- name blank --> V2(["Throw ValidationException"])
    G -- startYear < endYear --> V2c(["Throw ValidationException: inverted range"])
    G -- teamSize set AND not in club.sport.teamSizes --> V2(["Throw ValidationException"])
    G -- "startYear/endYear pair already taken (in the DB, or by an earlier item in this request)" --> CN(["Throw ConflictException: duplicate range"])
    G -- valid --> H[Mark range as taken; build AgeGroup entity — teamSize nullable]
    H --> I[Batch insert all AgeGroups]
    I --> Z(["Return created AgeGroups"])
```

Each item carries a `startYear`/`endYear` birth-year span rather than a single birth year: most age groups (football) are single-year, sent as `startYear == endYear`; some (typically futsal) span two consecutive birth years to have enough players for a squad -- e.g. a Benjamins group covering 2016 and 2017 is `startYear = 2017, endYear = 2016`. Eligibility and the inferred "Sub N" name are always derived from `endYear` (the oldest birth year the group allows), exactly mirroring the old single `birthYear` field's role -- see §5.3. Each item also carries an optional `teamSize` (an a-side count like 7 or 11). It's the default the age group's teams play; a team can override it (§4.5 / §4.5b). When set it must be one of the values the club's `sport` allows (`SportType.teamSizes` — a code-level constant, not a DB config). The client's Create Age Groups screen shows a per-row span-type radio (single year vs. two years) and a per-row team-size picker seeded from the sport's largest format.

### 4.4 Update Age Group (`UpdateAgeGroupUseCase`)

```mermaid
graph TD
    A[Start: userId, ageGroupId, patch] --> B[Fetch AgeGroup by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["Merge patch fields (name, startYear, endYear)"]
    D --> E{Merged startYear < endYear?}
    E -- Yes --> V(["Throw ValidationException: inverted range"])
    E -- No --> Z(["Persist updated AgeGroup"])
```

### 4.4b Get Age Group Details (`GetAgeGroupDetailsUseCase`)

The aggregate behind the age-group detail screen. Follows the standard permission-gated read (§1.3) with an aggregation step and four independent, non-throwing permission probes -- each matching the real gate its action's own use case enforces, rather than one literal check standing in for several at once (see TECHNICAL_DECISIONS.md §35/§37's "reshuffled taxonomy" notes on why bundling these caused real button-visibility bugs):

```mermaid
graph TD
    A[Start: userId, ageGroupId] --> B[Fetch AgeGroup by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=MEMBER, minTeamRole=STAFF, ageGroupId, allowPlayerOrParentAccess]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Fetch club + season]
    D --> E[teams = club teams filtered to this age group]
    E --> G["upcomingMatches = each team's matches that are either SCHEDULED with dateTime >= now, or currently LIVE/HALF_TIME, sorted by dateTime"]
    G --> G2["upcomingMatchScores = getScoreByMatch for each LIVE/HALF_TIME entry above, keyed by Match.id"]
    G2 --> H["canAddStaff = non-throwing probe: minClubRole=COORDINATOR only (no team-role path)"]
    H --> H1["canCreateTeam = non-throwing probe of configurablePermission=CREATE_TEAM on ageGroupId"]
    H1 --> H2["canManageMatch = non-throwing probe of configurablePermission=MANAGE_MATCH on ageGroupId -- schedule match"]
    H2 --> H3["canAssignPlayersToAgeGroup = non-throwing probe of configurablePermission=ASSIGN_PLAYERS_TO_AGE_GROUP on ageGroupId -- add players"]
    H3 --> Z(["Return AgeGroupDetails: ageGroup, club, season, players (pool), staff, teams, upcomingMatches, upcomingMatchScores, opponents, canAddStaff, canCreateTeam, canManageMatch, canAssignPlayersToAgeGroup"])
```

`opponents` is the club's full opponent list so the client can resolve `match.opponentId`. `canAddStaff` gates "Add staff" (permanently coordinator/admin-only, not club-configurable, the same way `ClubDetailsResponse.canEditClub` is); `canCreateTeam` gates "Create team" (`ConfigurablePermission.CREATE_TEAM`, defaults to Coach -- moved here from the fixed bucket in §37, since a coach who'd just set up an age group had no way to add a second team to it themselves); `canManageMatch` gates "Schedule match"; `canAssignPlayersToAgeGroup` gates "Add players" -- these last three are club-configurable and can each diverge from the coordinator-only default independently. A match in progress stays in `upcomingMatches` rather than disappearing between "scheduled" and "finished" -- `upcomingMatchScores` lets the client show its running score (see `MatchRow` in `TECHNICAL_DECISIONS.md`/the client's `presentation/ui/components` package).

### 4.5 Create Team (`CreateTeamUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId, ageGroupId, seasonId, name, code, teamSize?] --> C{name blank?}
    C -- Yes --> V(["Throw ValidationException"])
    C -- No --> D[Load age group by id]
    D -- not found --> V2(["Throw NotFoundException"])
    D -- found --> E{"age group's clubId == request clubId AND seasonId == request seasonId?"}
    E -- No --> V
    E -- Yes --> B["CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=CREATE_TEAM, ageGroupId"]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> E2{"teamSize set? — load club, must be in club.sport.teamSizes"}
    E2 -- invalid --> V
    E2 -- ok / not set --> G["Create Team — active=true, teamSize = request.teamSize ?: ageGroup.teamSize"]
    G --> H["Fetch every active ClubMember of clubId; flatten their FcmTokenRepository tokens"]
    H --> I["Transaction commits"]
    I --> J{"Any token found?"}
    J -- Yes --> K["PushNotificationService.sendTeamCreated(tokens, [team.name], ageGroupName, clubName) — every club member, not just this age group's staff"]
    J -- No --> Z
    K --> Z(["Return created Team"])
```

The age group is loaded and validated to actually belong to the request's `clubId`/`seasonId` **before** the permission check, not after -- that check trusts the supplied `ageGroupId` to resolve the caller's age-group-staff role, so an unvalidated one would let a coach of Club A's age group spoof permission to create a team in Club B (same guard as `CreatePlayersUseCase`). `configurablePermission=CREATE_TEAM` (§37 in TECHNICAL_DECISIONS.md, defaults to Coach) means an age-group coach can create a team in their own age group without needing a club coordinator/admin. `teamSize` is the a-side count this team plays. The request field is optional: when omitted the team inherits the age group's default (`ageGroup.teamSize`, itself nullable for age groups created before team sizes existed). When supplied it must be one of the club sport's allowed sizes. The client's Create Team screen pre-selects the age group's default and lets the user change it (e.g. an age group defined as 7-a-side with one team that plays 9-a-side). The club is now always fetched (previously only when `teamSize` was set) -- also needed for the push's club name.

### 4.5b Create Teams (`CreateTeamsUseCase`) — batch

```mermaid
graph TD
    A[Start: userId, clubId, seasonId, ageGroupId, items list] --> C{items list empty?}
    C -- Yes --> V(["Throw ValidationException"])
    C -- No --> D[Load age group by id]
    D -- not found --> V2(["Throw NotFoundException"])
    D -- found --> E{"age group belongs to this club AND this season?"}
    E -- No --> V
    E -- Yes --> B["CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=CREATE_TEAM, ageGroupId"]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> E2[Load club — for its sport's allowed team sizes]
    E2 --> G{For each item}
    G -- name blank --> V
    G -- teamSize set AND not in club.sport.teamSizes --> V
    G -- valid --> H["Build Team entity — active=true, teamSize = item.teamSize ?: ageGroup.teamSize"]
    H --> I[Batch insert all Teams]
    I --> J["Fetch every active ClubMember of clubId; flatten their FcmTokenRepository tokens"]
    J --> K["Transaction commits"]
    K --> L{"Any token found?"}
    L -- Yes --> M["PushNotificationService.sendTeamCreated(tokens, createdTeams.map{name}, ageGroupName, clubName) — one push for the whole batch, every club member"]
    L -- No --> Z
    M --> Z(["Return created Teams"])
```

Whole batch runs in one transaction (all or nothing), mirroring `CreateAgeGroupsUseCase`. Teams have no name-uniqueness constraint, so there's no conflict branch. Covered by `CreateTeamsUseCaseTest`. Same age-group-validated-before-permission-check ordering as the single-team case above, and the same `configurablePermission=CREATE_TEAM` gate. The client's Create Teams screen defaults the season to the most recent one (by end date), requires an age group to be picked, and requires a name on every row before it will submit. Each row also has a team-size picker pre-selected to the chosen age group's default (`ageGroup.teamSize`), overridable per row; an omitted `teamSize` falls back to that default server-side. The batch's push lists every created team's name in one notification rather than sending one push per team.

### 4.6 Update Team (`UpdateTeamUseCase`)

```mermaid
graph TD
    A[Start: userId, teamId, patch] --> B[Fetch Team by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["Merge patch fields (name, code, active)"]
    D --> Z(["Persist updated Team"])
```

### 4.6b Get Team Details (`GetTeamDetailsUseCase`)

The aggregate behind the team detail screen. A team is only a scheduling handle, so the payload leads with its fixtures and carries the age group's pool size + staff read-only. Standard permission-gated read (§1.3) with an aggregation step and a `canManageMatch` probe:

```mermaid
graph TD
    A[Start: userId, teamId] --> B[Fetch Team by id]
    B -- not found --> N(["Throw ValidationException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=MEMBER, minTeamRole=STAFF, teamId, allowPlayerOrParentAccess]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Fetch club + age group + season]
    D --> G["canManageMatch = non-throwing probe of configurablePermission=MANAGE_MATCH on teamId"]
    G --> H[matches = matchRepository.getMatchesByTeam]
    H --> I["upcomingMatches = SCHEDULED with dateTime >= now, OR currently LIVE/HALF_TIME, sorted ascending"]
    I --> I2["upcomingMatchScores = getScoreByMatch for each LIVE/HALF_TIME entry above, keyed by Match.id"]
    H --> J["recentResults = FINISHED, newest first, each paired with its scoreline from matchEventRepository.getScoreByMatch → WIN/DRAW/LOSS"]
    I2 --> K[siblingTeamNames = other teams in the same age group]
    J --> K
    K --> Z(["Return TeamDetails: team, club, ageGroup, season, poolSize, siblingTeamNames, staff, upcomingMatches, upcomingMatchScores, recentResults, opponents, canManageMatch"])
```

`poolSize` / `staff` come from the age group (players and staff belong to it, not the team); the screen links back to the age-group detail for both. `club` is carried so a screen opened from here (e.g. Schedule Match) has the club name without another call. `opponents` is the club's full opponent list so the client can resolve `match.opponentId`. `canManageMatch` gates the schedule-match action ("Edit team" is a decorative, unwired menu item in `TeamDetailsScreen` -- no Edit Team screen exists yet). As with the age-group screen, a live match stays in `upcomingMatches` (it hasn't reached `recentResults` yet) and `upcomingMatchScores` carries its running score.

---

## 5. Player & Roster Management

### 5.1 Create Players (`CreatePlayersUseCase`) — batch, with self & parent invites

```mermaid
graph TD
    A[Start: userId, clubId, players list, ageGroupId?] --> AG{ageGroupId supplied?}
    AG -- Yes --> AGF[Fetch AgeGroup by id]
    AGF -- not found --> N0(["Throw NotFoundException"])
    AGF -- found, wrong club --> FB0(["Throw ForbiddenException"])
    AGF -- found, right club --> B
    AG -- No --> B[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=CREATE_PLAYERS, ageGroupId]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C{Any player first/last name blank?}
    C -- Yes --> V(["Throw ValidationException"])
    C -- No --> D["Batch create Player records — active=true (position/heightCm/weightKg/emergencyContact carried through if given, all optional)"]
    D --> E["Collect all unique emails (players' own + all parents')"]
    E --> G[Batch find existing Users by email]
    G --> H{For each created player}
    H -- has own email, user exists --> I[Queue direct ClubMember, role MEMBER]
    H -- has own email, user missing --> J[Queue CLUB_MEMBER Invite]
    H -- no email --> SK[Skip self-invite]
    H --> L{For each parent of this player}
    L -- user exists --> M[Queue UserPlayer link + relationship]
    L -- user missing --> N["Queue PARENT_PLAYER Invite (linked to playerId)"]
    I --> O[Apply direct ClubMember additions]
    J --> P[Batch insert all Invites — player + parent]
    M --> Q[Batch insert UserPlayer links]
    N --> P
    O --> Z(["Return created Players"])
    Q --> Z
    P --> Z
```

`ageGroupId` is optional: the "+ Add new player" shortcut on Add Players to Age Group passes the selected age group, so that age group's coach can create players through `ConfigurablePermission.CREATE_PLAYERS` (defaults to Coach). It's checked against `clubId` before the permission check to block cross-club spoofing. The club-roster entry points send no `ageGroupId`, so they stay coordinator-only. See `TECHNICAL_DECISIONS.md` §31/§35.

### 5.1b Get Player Profile (`GetPlayerUseCase`)

The aggregate behind the player profile screen. No longer a plain fetch-and-return: it now assembles
all-time stats and a resolved recent-events list alongside the player record.

```mermaid
graph TD
    A[Start: userId, playerId] --> B[Fetch Player by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=MEMBER]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["ageGroupLabel/seasonLabel = first AgeGroup this player's pool membership resolves to (name), plus its Season's name"]
    D --> E["events = getAllEventsByPlayer, newest first"]
    E --> G["goals/assists = count of GOAL/ASSIST in events"]
    G --> H["recentEvents = first 5 of events, each resolved to its Match + Opponent name"]
    H --> I["timesCalledUp = countCallUpsByPlayer; guardiansLinked = countGuardiansByPlayer"]
    I --> J["canEditPlayer = non-throwing probe of configurablePermission=EDIT_PLAYER on this player's primary age group"]
    J --> Z(["Return PlayerProfile: player, ageGroupLabel, seasonLabel, goals, assists, timesCalledUp, guardiansLinked, recentEvents, canEditPlayer"])
```

Stats are all-time -- there's no season/last-5/career split yet (the client's player profile screen
only shows one fixed set of numbers). `ageGroupLabel`/`seasonLabel` are the player's actual pool
membership (real `AgeGroupPlayers` data) and that age group's season; this is distinct from the
"Sub N" text the client also shows next to the player's name/position, which it derives purely from
`Player.birthDate` and today's date (`AgeGroupDefaults.currentSeasonEndYear`/`inferredName`) with no
server round-trip. `canEditPlayer` gates the player profile screen's "Edit player" overflow action --
a non-throwing probe of the same club-configurable gate `UpdatePlayerUseCase` actually enforces, not a
fixed coordinator-only check (see TECHNICAL_DECISIONS.md §35).

`Player.position` is a `PlayerPosition` enum (`GOALKEEPER`/`DEFENDER`/`MIDFIELD`/`WINGER`/`STRIKER`),
and `Player` also carries `heightCm`, `weightKg`, and a free-text `emergencyContact` -- all four are
nullable and settable from Create Players (§5.1) and Update Player (§5.2); the profile screen omits
any that are absent rather than showing a placeholder.

### 5.2 Update Player (`UpdatePlayerUseCase`)

```mermaid
graph TD
    A[Start: userId, playerId, patch incl. optional ageGroupId] --> B[Fetch Player by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> AG{ageGroupId supplied?}
    AG -- Yes --> AGF[Fetch AgeGroup by id]
    AGF -- not found --> N2(["Throw NotFoundException"])
    AGF -- found, wrong club --> FB(["Throw ForbiddenException"])
    AGF -- found, right club --> C
    AG -- No --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=EDIT_PLAYER, ageGroupId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["Merge patch fields (name, birthDate, shirtNumber, photoUrl, position, heightCm, weightKg, emergencyContact)"]
    D --> E[Persist updated Player]
    E --> G{email provided in request?}
    G -- yes, user exists --> H[Add as ClubMember, role MEMBER]
    G -- yes, user missing --> I[Create CLUB_MEMBER Invite]
    G -- no --> SK[Skip]
    H --> J{parents list provided?}
    I --> J
    SK --> J
    J -- No --> Z(["Return success"])
    J -- Yes --> K["Delete ALL existing UserPlayer links for this player"]
    K --> L["Delete ALL existing pending Invites for this player"]
    L --> M[Batch find Users by new parent emails]
    M --> P{For each new parent}
    P -- user exists --> Q[Queue UserPlayer link]
    P -- user missing --> R[Queue PARENT_PLAYER Invite]
    Q --> S[Insert links]
    R --> T[Insert invites]
    S --> Z
    T --> Z
```

The `parents` list is a **total replacement**, not a merge: every existing parent link and pending parent-invite is deleted before the new list is applied.

`ageGroupId` (optional, same cross-club-validated shape as `CreatePlayersRequest.ageGroupId` — see §5.1)
lets this be authorized via `ConfigurablePermission.EDIT_PLAYER` as that age group's own coach, not just a
club-wide coordinator+ — added alongside making the action configurable (see `TECHNICAL_DECISIONS.md`
§35); before that change this use case had no age-group-staff path at all, unlike its sibling
`CreatePlayersUseCase`.

**Client screen:** `EditPlayerScreen` (design canvas "17A"/"17B") is the first client consumer of this
use case. Reached from the player profile's overflow menu (gated on `canEditPlayer`, §5.1b), it edits every
field `UpdatePlayerRequest` supports except `photoUrl`/`email`/`parents` -- name, date of birth, shirt
number, position, height/weight, and emergency contact -- reusing Create Players' field styling, and
passes the player's own `ageGroupId` (from `PlayerProfile`, resolved server-side the same way
`ageGroupLabel` already was) through on save. A green banner ("Only the name and date of birth are
required. The rest can wait.") shows when the player's optional fields were never set; the pinned Save
button (and a matching header checkmark) stay disabled until the form is both valid (name + DOB present)
and different from what was loaded. Saving flags `KEY_CLUB_STRUCTURE_CHANGED` so the profile screen
reloads on return, same cascade pattern as the other create/edit screens (`AppNavigation.kt`).

### 5.3 Add Players to Age Group (`AddPlayersToAgeGroupUseCase`) — with age eligibility

Players belong to **age-group pools**, not teams. A player can be in several age groups at once (their birth-year group plus any older one they're eligible to play up into). `(age_group_id, player_id)` is unique. Which players actually appear for a given match is decided per-match via call-ups.

```mermaid
graph TD
    A[Start: userId, ageGroupId, playerIds list] --> B[Fetch AgeGroup by id]
    B -- not found --> V0(["Throw ValidationException: Age group not found"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=ASSIGN_PLAYERS_TO_AGE_GROUP, ageGroupId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> E0{playerIds empty?}
    E0 -- Yes --> V0b(["Throw ValidationException"])
    E0 -- No --> E[Batch fetch Player data + players already in this age group]
    E --> G{For each requested player}
    G -- already in this age group --> SK[skip — idempotent]
    G -- player not found --> V2(["Throw ValidationException"])
    G -- player.clubId != ageGroup.clubId --> FB(["Throw ForbiddenException: wrong club"])
    G -- "player.birthDate.year < ageGroup.endYear" --> V3(["Throw ValidationException: too old for age group"])
    G -- eligible --> H[Build AgeGroupPlayer link]
    SK --> I
    H --> I[Batch insert AgeGroupPlayer links]
    I --> Z(["Return created AgeGroupPlayer links"])
```

`GetEligiblePlayersForAgeGroupUseCase` is the read counterpart: every club player with `birthDate.year >= ageGroup.endYear` who isn't already in the group; visible to a coordinator or any age-group staff. Playing up is unbounded — a young player is eligible for any older age group, never a younger one.

Age groups carry a `startYear`/`endYear` birth-year **span**, not a single birth year (see §4.3): most (football) are single-year, with `startYear == endYear`; some (typically futsal) span two consecutive birth years to have enough players for a squad -- e.g. a Benjamins group covering 2016 and 2017 is `startYear = 2017, endYear = 2016`. Eligibility above and the inferred "Sub N" name (client-side `AgeGroupDefaults.inferredName`) are both always keyed off `endYear` -- the oldest birth year the group allows -- so a two-year group behaves exactly like the old single-birth-year field with the threshold lowered by one year; `startYear` is otherwise informational (shown in the UI, not compared against).

The client's Add Players screen has a "New player" shortcut that pushes the Create Players (register-to-club) screen; on return it reloads the eligible list so the just-registered players are pickable without leaving, and propagates the reload down to Club Details / Age Group Details.

### 5.4 Add Staff to Age Group (`AddStaffToAgeGroupUseCase`)

Coaches/support staff attach to an **age group**, not a team (`age_group_staff`). Matches are run by whoever staffs the match's team's age group.

```mermaid
graph TD
    A[Start: userId, ageGroupId, staff list] --> B[Fetch AgeGroup by id]
    B -- not found --> V(["Throw NotFoundException: Age group not found"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Batch fetch club roles for all staff userIds]
    D --> E{For each staff item}
    E -- user has no club role --> FB(["Throw ForbiddenException: must join club first"])
    E -- user is already a club member --> G[Build AgeGroupStaff record]
    G --> H[Batch insert AgeGroupStaff records]
    H --> I["After commit: EmailService.sendAgeGroupStaffAdded per staff added"]
    I --> Z(["Return success"])
```

Every staff member added here already has an account (club membership is a precondition — see the ForbiddenException branch above), so this is always the "existing user" email, never an invite. Sent after commit, same timing as §3.1/§3.6 — see TECHNICAL_DECISIONS.md §14.

Since staff must already be a club member, the client's **Add Staff to Age Group** screen (reached from an age group's own "Add staff" button/menu entry) needs a way to list them: `GetClubMembersUseCase` (`GET /v1/clubs/{clubId}/members`, same `minClubRole=COORDINATOR` gate) returns every active `ClubMemberSummary` (name, email, club role) for the picker. The screen then works like Add Players to Age Group — pick an age group, search the list, pick who to add — except each row picks a `TeamRole` (Coach/Staff) rather than being a plain toggle, and the eligible list excludes members already staffing that age group (fetched via `GetAgeGroupStaffUseCase`, `GET /v1/age-groups/{ageGroupId}/staff`, client-side only, no new endpoint).

Someone not yet a club member can't be picked there, so that screen also has a "New staff member" shortcut to the **Create Staff** screen (Club Details overflow menu, and the shortcut above) -- this is just `AddClubMembersUseCase` (`POST /v1/clubs/{clubId}/members`, the existing club-member-invite flow, previously unused by the client) presented as a batch-of-emails form matching Create Players' shape. An email already tied to an account is added to the club immediately as a `MEMBER`; an unknown one gets an `Invite` row (`InviteType.CLUB_MEMBER`) so they land as a `MEMBER` once they register -- no new fields, no new backend behaviour, just the client screen that was missing. Either way they then show up in Add Staff to Age Group's picker.

---

## 6. Opponents

### 6.1 Create Opponent (`CreateOpponentUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId, name, shortName, logoUrl, ageGroupId?] --> AG{ageGroupId supplied?}
    AG -- Yes --> AGF[Fetch AgeGroup by id]
    AGF -- not found --> N(["Throw NotFoundException"])
    AGF -- found, wrong club --> FB(["Throw ForbiddenException"])
    AGF -- found, right club --> B
    AG -- No --> B[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=CREATE_OPPONENT, ageGroupId]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C[Create Opponent record]
    C --> Z(["Persist Opponent"])
```

Same cross-club-validated, age-group-scoped `ageGroupId` shape as `CreatePlayersUseCase` (§5.1) — a
coordinator+ can always create club-wide; an age-group coach only for that specific age group's opponents.
`CREATE_OPPONENT`'s minimum staff role is configurable per club — see `TECHNICAL_DECISIONS.md` §35.

### 6.2 Update Opponent (`UpdateOpponentUseCase`)

```mermaid
graph TD
    A[Start: userId, opponentId, patch incl. optional ageGroupId] --> B[Fetch Opponent by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> AG{ageGroupId supplied?}
    AG -- Yes --> AGF[Fetch AgeGroup by id]
    AGF -- not found --> N2(["Throw NotFoundException"])
    AGF -- found, wrong club --> FB(["Throw ForbiddenException"])
    AGF -- found, right club --> C
    AG -- No --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=EDIT_OPPONENT, ageGroupId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["Merge patch fields (name, shortName, logoUrl)"]
    D --> Z(["Persist updated Opponent"])
```

`ageGroupId`, added alongside making `EDIT_OPPONENT` configurable (`TECHNICAL_DECISIONS.md` §35), gives
this the same age-group-coach path its sibling `CreateOpponentUseCase` already had — previously
coordinator-only, no staff path at all. Not yet reachable from the client — no screen calls
`UpdateOpponentUseCase` today.

---

## 7. Match Management & Call-ups

### 7.1 Create Match (`CreateMatchUseCase`)

```mermaid
graph TD
    A[Start: userId, teamId, opponentId, location, dateTime, notes] --> B[Fetch Team by id]
    B -- not found --> N1(["Throw NotFoundException: Team not found"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_MATCH, teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Fetch Opponent by id]
    D -- not found --> N2(["Throw NotFoundException: Opponent not found"])
    D -- found --> E{opponent.clubId != team.clubId?}
    E -- Yes --> FB(["Throw ForbiddenException: opponent belongs to another club"])
    E -- No --> G[Create Match — status SCHEDULED]
    G --> H[Persist Match]
    H --> I["Transaction commits"]
    I --> J["NotifyMatchEventUseCase(match.id, SCHEDULED) — best-effort push to every club member (§8.2b)"]
    J --> Z(["Return created Match"])
```

### 7.1b Get Match Details (`GetMatchDetailsUseCase`)

The aggregate behind the match screen. Standard permission-gated read (§1.3, `MEMBER` + age-group `STAFF` + linked player/parent) plus an aggregation step and three independent, non-throwing permission probes (`canManageMatch`, `canManageCallUps`, `canManageLiveMatch`), each resolved against the exact `configurablePermission` its own action's use case enforces rather than a literal role check that can drift out of sync with a club's actual configuration (see TECHNICAL_DECISIONS.md §35's "reshuffled taxonomy" note -- this replaced an earlier two-flag `canManage`/`canManageMatchDay` design that used hardcoded thresholds and could show/hide the wrong buttons once a club overrode any of the three):

```mermaid
graph TD
    A[Start: userId, matchId] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=MEMBER, minTeamRole=STAFF, teamId=match.teamId, allowPlayerOrParentAccess]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Fetch opponent + team + age group + season]
    D --> E["callUps = call-ups for the match; poolPlayers = age group's players MINUS whoever is already called up"]
    E --> G["canManageMatch = non-throwing probe of configurablePermission=MANAGE_MATCH on the team"]
    G --> G2["canManageCallUps = non-throwing probe of configurablePermission=MANAGE_CALL_UPS on the team"]
    G2 --> G3["canManageLiveMatch = non-throwing probe of configurablePermission=MANAGE_LIVE_MATCH on the team"]
    G3 --> H["score = matchEventRepository.getScoreByMatch(matchId) — 0-0 before any goals are logged"]
    H --> Z(["Return MatchDetails: match, opponent, team, ageGroup, season, callUps, poolPlayers, canManageMatch, canManageCallUps, canManageLiveMatch, score"])
```

The use case composes `MatchDetails` itself now — the old `MatchRepository.getMatchDetails` (opponent + call-ups only) was removed. `poolPlayers` is the "Available" list on the call-up screen. `canManageMatch` gates just the edit sheet -- changing the match's own date/time/location/notes -- and the delete-match menu item. `canManageCallUps` gates managing the pre-match squad list. `canManageLiveMatch` gates entering the live match console and every action inside it (kickoff, clock, logging/deleting events, status changes) -- see §19 in `TECHNICAL_DECISIONS.md` for the original coach-vs-staff rationale and §35 for how all three are now independently club-configurable. All three are further gated client-side: the client forces the screen read-only once `match.status == FINISHED` regardless of any flag, so a finished match becomes a plain record (no editing call-ups, kickoff, location, etc.), and the live match console itself hides (not just disables) every action a viewer's `canManageLiveMatch` doesn't clear, showing a read-only notice instead. `score` feeds the FINISHED-state result banner (colored WIN/LOSS/DRAW + scoreline) on the details screen, the same rebuilt-from-events value `MatchRow` already showed in match lists.

The bottom button just opens the live match screen; starting the game and recording events all live there. If the match is already LIVE/HALF_TIME when this screen loads (tapped in from a list, or discovered on a reload), the client redirects straight to the live match screen instead of rendering the details view -- see `MatchDetailsViewModel.load()` / `AppNavigation.kt`'s `Screen.MatchDetails` route, which pops this screen off the back stack when redirecting (rather than stacking under it) so back-navigation from the live screen doesn't bounce straight back into another redirect.

### 7.1c Update Match (`UpdateMatchUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId, patch (opponentId?, dateTime?, location?, notes?)] --> B[Fetch Match by id]
    B -- not found --> N1(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D{patch.opponentId set?}
    D -- Yes --> E[Fetch Opponent; reject if it belongs to another club]
    D -- No --> G
    E --> G["Merge non-null patch fields onto the match"]
    G --> H{"opponentId, dateTime, or location actually changed value?"}
    H --> I[Persist updated Match — status and call-ups are untouched]
    I --> J["Transaction commits"]
    J --> K{"Did a schedule field change?"}
    K -- Yes --> L["NotifyMatchEventUseCase(matchId, RESCHEDULED) — best-effort push to every club member (§8.2b)"]
    K -- "No (notes-only edit)" --> Z
    L --> Z(["Return success"])
```

### 7.2 Manage Call-ups (`ManageCallUpsUseCase`) — batch replace

```mermaid
graph TD
    A[Start: userId, matchId, playerIds list] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_CALL_UPS, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D["Set call-ups for the match to exactly this playerIds list (repository owns diff/replace)"]
    D --> Z(["Return success"])
```

The same single write handles both adding and removing -- the client sends the full desired list (current call-ups minus/plus one player) and the repository diffs it against what's stored (§7.2's Exposed implementation). `MatchDetailsScreen`'s call-up rows each carry a remove ("−") button, the "Available" pool rows an add ("+") button, both wired to this one endpoint -- see `MatchDetailsViewModel.onAddToCallUp`/`onRemoveFromCallUp`.

### 7.2b Delete Match (`DeleteMatchUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> ST{match.status == FINISHED?}
    ST -- Yes --> CF(["Throw ConflictException: a played match can't be deleted"])
    ST -- No --> D["Delete all MatchEvents for this match"]
    D --> E["Clear all call-ups for this match (manageCallUps with an empty list)"]
    E --> G["Delete the Match row"]
    G --> Z(["Return success"])
```

Same `MANAGE_MATCH` gate as scheduling/editing a match -- deliberately a **separate**, coach-tier-by-default
configurable action from both the pre-match call-ups in §7.2 (`MANAGE_CALL_UPS`, also coach-tier by
default) and the live-match operations in §8.0/§8.1/§8.1b/§8.2 (`MANAGE_LIVE_MATCH`, staff-tier by
default; see TECHNICAL_DECISIONS.md §19 for the original coach-vs-staff split, and §35 for how all three are
now per-club configurable). Events and call-ups are
cleared first because neither's `matchId` foreign key has `ON DELETE CASCADE`; deleting the match row
first would fail the constraint. Exposed via `DELETE /matches/{matchId}` (`MatchRoutes.kt`), reachable
from `MatchDetailsScreen`'s top-right overflow menu (a `ConfirmDialog` -- see `presentation.ui.components`
-- gates the actual call, since this is irreversible).

**A played match can't be deleted.** Once a match is `FINISHED` it has happened: its events, player stats
(§8.2f) and report (§8.6) hang off it, so the use case rejects the delete with `ConflictException` (409),
and the client hides the overflow menu for a finished match. Any match that never ended stays deletable,
whether it's still scheduled (even past its date), cancelled, or live/at half-time. Covered by
`DeleteMatchUseCaseTest`.

**Removed: `UpdateCallUpStatusUseCase`.** A call-up used to carry a 5-value attendance status
(`CALLED_UP`/`CONFIRMED`/`DECLINED`/`NOT_AVAILABLE`/`STANDBY`), settable only by Coach-level staff (or
club Admin/Coordinator) -- there was never a path for the Parent/Player themselves to self-confirm
attendance. Since parents/players are out of scope for v1.0.0 (per the developer), that status was
never actually settable to anything other than `CALLED_UP` by anyone -- so it, its use case, and its
route (`PATCH /matches/call-ups/{callUpId}/status`) were removed outright rather than kept as unreachable
code. `CallUpStatus` is now a single-value enum (see §12's schema reference) -- being called up is
binary, no per-player status shown anywhere in the client.

---

## 8. Live Match Engine

### 8.0 Start Match (`StartMatchUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId, startingPlayerIds, halfLengthMinutes] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D{"status == SCHEDULED? halfLengthMinutes > 0? startingPlayerIds non-empty, no duplicates?"}
    D -- No --> V1(["Throw ValidationException"])
    D -- Yes --> E[Fetch Team — required count = team.teamSize ?: 11]
    E -- count mismatch --> V2(["Throw ValidationException"])
    E -- ok --> G[Fetch this match's call-ups]
    G -- a starter isn't in the call-up list --> V3(["Throw ValidationException"])
    G -- ok --> H["Set isStarting=true on the starters' call-ups (false on the rest), set Match.status=LIVE + halfLengthMinutes + kickoffAt=now"]
    H --> I["Publish LiveMatchUpdate(score, status=LIVE) via LiveMatchBus"]
    I --> J["Transaction commits"]
    J --> K["NotifyMatchEventUseCase(matchId, KICKOFF) — best-effort push to every club member (§8.2b)"]
    K --> Z(["Return success"])
```

This is the "KICK OFF" action on the live match console's pre-kickoff screen (client, see below): it locks in the Starting XI (everyone else called up becomes the bench) and the half length in one write, then flips the match live. The a-side count comes from `Team.teamSize` (falling back to 11 for teams recorded before that field existed — see the team-size flows in §4.3/§4.5). It also stamps `Match.kickoffAt` with the wall-clock instant of kickoff — the client derives the live running clock from this timestamp rather than any locally-ticking counter (see §8.4).

### 8.1 Add Match Event (`AddMatchEventUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId, type, minute, playerId?, opponentPlayerName?, isOpponent] --> B[Fetch Match by id]
    B -- not found --> V(["Throw ValidationException: Match not found"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Create MatchEvent record]
    D --> E[Persist event]
    E --> G["Recalculate score from all events for this match"]
    G --> H["Publish LiveMatchUpdate(event, score) via LiveMatchBus for matchId"]
    H --> H2["Publish ClubMatchesUpdate(score) via ClubMatchesBus for match.clubId (§8.5)"]
    H2 --> I["Transaction commits"]
    I --> J{"type == GOAL or OWN_GOAL?"}
    J -- Yes --> K["NotifyMatchEventUseCase(matchId, GOAL) — best-effort push to every club member (§8.2b)"]
    J -- No --> Z
    K --> Z(["Return created event"])
```

### 8.1b Delete Match Event (`DeleteMatchEventUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId, eventId] --> B[Fetch Match by id]
    B -- not found --> N1(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Fetch Event by id]
    D -- not found --> N2(["Throw NotFoundException"])
    D -- event.matchId != matchId --> V(["Throw ValidationException"])
    D -- ok --> E[Delete event]
    E --> G[Recalculate score]
    G --> H["Publish LiveMatchUpdate(score, deletedEventId) via LiveMatchBus"]
    H --> H2["Publish ClubMatchesUpdate(score) via ClubMatchesBus for match.clubId (§8.5)"]
    H2 --> Z(["Return success"])
```

Removes a mis-logged goal, card, or substitution row. The client always confirms with the user first (see below — "Confirms only on Full time and event deletion").

### 8.2 Update Match Status (`UpdateMatchStatusUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId, newStatus] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> T{"newStatus in VALID_MATCH_STATUS_TRANSITIONS[match.status]?"}
    T -- No --> V(["Throw ValidationException"])
    T -- Yes --> D[Update Match.status in DB]
    D --> E{Update succeeded?}
    E -- No --> R1(["Return false — no broadcast"])
    E -- Yes --> F{"newStatus == LIVE and previous status == HALF_TIME?"}
    F -- Yes --> F2["Set Match.secondHalfStartedAt = now"]
    F -- No --> G
    F2 --> G[Recalculate current score]
    G --> H["Publish LiveMatchUpdate(score, status) via LiveMatchBus"]
    H --> R2b["Transaction commits"]
    R2b --> R3{"Which transition?"}
    R3 -- "HALF_TIME→LIVE" --> R4["NotifyMatchEventUseCase(matchId, SECOND_HALF_STARTED)"]
    R3 -- "→HALF_TIME" --> R5["NotifyMatchEventUseCase(matchId, HALF_TIME)"]
    R3 -- "→FINISHED" --> R6["NotifyMatchEventUseCase(matchId, FULL_TIME)"]
    R3 -- "→CANCELLED" --> R7["NotifyMatchEventUseCase(matchId, CANCELLED)"]
    R4 --> R2
    R5 --> R2
    R6 --> R2
    R7 --> R2
```

Status values flow `SCHEDULED → LIVE → HALF_TIME → FINISHED` (or `CANCELLED`). The use case now validates the transition against `VALID_MATCH_STATUS_TRANSITIONS`: `SCHEDULED → CANCELLED` only (kickoff, i.e. `SCHEDULED → LIVE`, stays exclusive to `StartMatchUseCase`, §8.0 — this is a real HTTP route in its own right, `MatchRoutes.kt`, so without this guard a caller could set a match LIVE here directly and skip `StartMatchUseCase`'s starting-line-up/half-length validation entirely); `LIVE → {HALF_TIME, FINISHED, CANCELLED}`; `HALF_TIME → {LIVE, FINISHED, CANCELLED}`; `FINISHED`/`CANCELLED` are terminal. An invalid transition throws `ValidationException`. (Previously this list closed with "but the use case does not itself validate that the requested transition is legal — any status can currently be set from any other," per §11 — now fixed there too.)

On the specific `HALF_TIME → LIVE` transition (resuming for the second half), this use case also stamps `Match.secondHalfStartedAt` with the wall-clock instant — so the client's running clock can pick up from the first half's nominal length instead of restarting at 0 (see §8.4). That same transition is also how it distinguishes a second-half restart from a first-ever kickoff notification (which never reaches this use case at all — see §8.0) when deciding which push to send, per §8.2b. `CANCELLED` (from either `SCHEDULED`, `LIVE`, or `HALF_TIME`) also notifies now, alongside the three live-match transitions — a cancelled fixture is arguably the most notification-worthy of all of them.

### 8.2b Notify Match Event (`NotifyMatchEventUseCase`)

```mermaid
graph TD
    A["Start: matchId, event (SCHEDULED/RESCHEDULED/CANCELLED/KICKOFF/HALF_TIME/SECOND_HALF_STARTED/FULL_TIME/GOAL)"] --> B["Open a new transaction (the trigger's own already closed)"]
    B --> C[Fetch Match by id]
    C -- not found --> X(["Return — nothing to notify with"])
    C -- found --> D[Fetch Team by match.teamId]
    D -- not found --> X
    D -- found --> E[Fetch Opponent by match.opponentId]
    E -- not found --> X
    E -- found --> F["Fetch every active ClubMember of match.clubId"]
    F --> G["FcmTokenRepository.getTokensForUsers(memberIds), flatten to one token list"]
    G -- empty --> X
    G -- non-empty --> H["Recalculate current score"]
    H --> I["Transaction closes"]
    I --> J{"Which event?"}
    J -- SCHEDULED --> K0a["PushNotificationService.sendMatchScheduled"]
    J -- RESCHEDULED --> K0b["PushNotificationService.sendMatchRescheduled"]
    J -- CANCELLED --> K0c["PushNotificationService.sendMatchCancelled"]
    J -- KICKOFF --> K1["PushNotificationService.sendMatchKickedOff"]
    J -- HALF_TIME --> K2["PushNotificationService.sendHalfTime"]
    J -- SECOND_HALF_STARTED --> K3["PushNotificationService.sendSecondHalfStarted"]
    J -- FULL_TIME --> K4["PushNotificationService.sendMatchFinished"]
    J -- GOAL --> K5["PushNotificationService.sendGoalScored"]
    K0a --> Z(["Return"])
    K0b --> Z
    K0c --> Z
    K1 --> Z
    K2 --> Z
    K3 --> Z
    K4 --> Z
    K5 --> Z
```

Not a route — a shared orchestrator called by §7.1 (a match created), §7.1c (a match's schedule edited), §8.0, §8.1, and §8.2 after their own transaction has already committed, so it opens a transaction of its own purely to read the data the push needs (see `TECHNICAL_DECISIONS.md` §24). Wrapped in a top-level try/catch that logs and swallows any exception (a not-found match, a repository throwing, a push send failing) — this must never propagate back to the use case that triggered it, the same "best-effort" contract as `EmailService`/`PushNotificationService` elsewhere. Recipients are **every active club member**, not scoped to the match's own age group — deliberately broader than the view/edit permissions the rest of the match domain enforces (§7–§8.2); see the developer's note on this in `TECHNICAL_DECISIONS.md` §24. Originally scoped to just the five live-match moments (hence the class's earlier name, `NotifyMatchEventUseCase`) — renamed once the three schedule-change events made "live match" no longer an accurate description of everything it sends.

### 8.2c Pause Match (`PauseMatchUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- status != LIVE --> V1(["Throw ValidationException"])
    C -- pausedAt already set --> V2(["Throw ValidationException"])
    C -- allowed, LIVE, not already paused --> D["MatchRepository.pauseMatch(matchId, now)"]
    D --> E["Publish LiveMatchUpdate(score, match=updated) via LiveMatchBus for matchId"]
    E --> F2["Publish ClubMatchesUpdate(match=updated) via ClubMatchesBus for match.clubId (§8.5)"]
    F2 --> Z(["Return success"])
```

Freezes the running clock for a mid-half stoppage (injury, weather) without a proper HALF_TIME transition — the match stays LIVE the whole time; only `Match.pausedAt` changes. Same match-day permission gate as kicking off / changing status (§8.0/§8.2, open to staff, not just the coach). See `TECHNICAL_DECISIONS.md` §29 for why this is a timestamp field rather than a new `MatchStatus` value.

### 8.2d Resume Match (`ResumeMatchUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- status != LIVE --> V1(["Throw ValidationException"])
    C -- pausedAt is null --> V2(["Throw ValidationException"])
    C -- allowed, LIVE, paused --> D["pauseDuration = now - pausedAt"]
    D --> E{"secondHalfStartedAt set?"}
    E -- yes --> F1["Shift secondHalfStartedAt forward by pauseDuration; kickoffAt untouched"]
    E -- no --> F2b["Shift kickoffAt forward by pauseDuration"]
    F1 --> G["MatchRepository.resumeMatch(matchId, kickoffAt, secondHalfStartedAt) — pausedAt cleared"]
    F2b --> G
    G --> H["Publish LiveMatchUpdate(score, match=updated) via LiveMatchBus for matchId"]
    H --> I["Publish ClubMatchesUpdate(match=updated) via ClubMatchesBus for match.clubId (§8.5)"]
    I --> Z(["Return success"])
```

Un-freezes the clock by shifting whichever of `kickoffAt`/`secondHalfStartedAt` is currently driving `elapsedMatchSeconds` (§8.4) forward by however long the stoppage lasted, so the clock continues from where it paused instead of jumping forward by the pause duration. No separate "total paused time" field — shifting the reference timestamp bakes the pause in permanently, and clearing `pausedAt` is enough.

### 8.2e Set Match Minute (`SetMatchMinuteUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId, minute] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=COORDINATOR, configurablePermission=MANAGE_LIVE_MATCH, teamId=match.teamId]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- status != LIVE --> V1(["Throw ValidationException"])
    C -- minute < 0 --> V2(["Throw ValidationException"])
    C -- allowed --> D["at = match.pausedAt ?: now"]
    D --> E{"secondHalfStartedAt set?"}
    E -- yes --> F1["newSecondHalfStartedAt = at - (targetSeconds - halfLengthSeconds); kickoffAt untouched"]
    E -- no --> F2["newKickoffAt = at - targetSeconds; secondHalfStartedAt stays null"]
    F1 --> G["MatchRepository.adjustMatchClock(matchId, kickoffAt, secondHalfStartedAt) — pausedAt NOT touched"]
    F2 --> G
    G --> H["Publish LiveMatchUpdate(score, match=updated) via LiveMatchBus for matchId"]
    H --> I["Publish ClubMatchesUpdate(match=updated) via ClubMatchesBus for match.clubId (§8.5)"]
    I --> Z(["Return success"])
```

A manual correction to the running clock (the touchline coach nudging it to match the referee's watch) — server-persisted and broadcast like every other clock mutation above, replacing an earlier client-only version that applied the correction as a local overlay never sent to the server at all (see `TECHNICAL_DECISIONS.md` §29's addendum for the bug report and full rationale). Solves for an absolute target rather than a relative shift (§8.2d solves "shift forward by however long the pause lasted"; this solves "make `elapsedMatchSeconds` equal to `minute` right now") — using `match.pausedAt` in place of `now` when paused, so a frozen display can be corrected without accidentally resuming it. `adjustMatchClock` is a distinct repository method from `resumeMatch` specifically because it must leave `pausedAt` untouched, unlike resuming.

### 8.3 Subscribe to Live Match Updates (`GetLiveMatchUpdatesUseCase`)

```mermaid
graph TD
    A[Start: userId, matchId] --> B[Fetch Match by id]
    B -- not found --> N(["Throw NotFoundException"])
    B -- found --> C[CheckUserPermissionUseCase — minClubRole=MEMBER]
    C -- denied --> F(["Throw ForbiddenException"])
    C -- allowed --> D[Subscribe to LiveMatchBus Flow for matchId]
    D --> Z(["Return reactive Flow of LiveMatchUpdate — bridged to a WebSocket session"])
```

Together, §8.0–§8.2 (the writers) and §8.3 (the reader) implement the hybrid "REST write, WebSocket broadcast" pattern documented in `TECHNICAL_DECISIONS.md` §6 — a REST POST persists the fact, then the same use case pushes it to every subscriber of that `matchId` on the shared `LiveMatchBus`.

### 8.4 Client: the Live Match Console

`LiveMatchScreen` (design canvas artboard "16") is one screen whose layout is entirely driven by `Match.status`:

- **SCHEDULED** — pre-kickoff: a half-length picker and a Starting XI/Bench picker built from the match's called-up/confirmed players, gated on picking exactly `Team.teamSize` (fallback 11) starters. "KICK OFF" calls §8.0.
- **LIVE / HALF_TIME** — the console: a scoreline (tapping a score logs an unattributed/opponent goal via §8.1), Squad and Events tabs, and a HALF TIME / FULL TIME action bar (§8.2, confirmed with a dialog first), plus a Pause/Resume text control next to "Set minute" (§8.2c/§8.2d/§8.2e), both visible only while `status == LIVE` and neither behind a confirmation dialog -- lower-stakes, reversible actions unlike ending a half. While paused, the clock and half label freeze and tint amber, and the score boxes stop accepting taps (a goal can't be logged mid-stoppage). The Squad tab's On pitch / Bench / Sent off split is *derived client-side* from the Starting XI (`MatchCallUp.isStarting`) replayed against the event log (substitutions move a player between On pitch/Bench, a red card moves them to Sent off) — nothing extra is persisted for this. Tapping a player opens a sheet to log a goal/assist/card, or substitute (a two-step tap: pick the player, then who they swap with) — bench players can't be tapped for goals/cards, only brought on. Deleting a logged event (§8.1b) asks for confirmation first, matching "Confirms only on Full time and event deletion."
- **FINISHED / CANCELLED** — a read-only full-time summary: final score, a WIN/LOSS/DRAW/CANCELLED badge, and the same event timeline reused from the Events tab (delete disabled here).

Every mutating action applies its own REST response to local state immediately, so the coach driving the match never waits on the socket; `GetLiveMatchUpdatesUseCase`'s WebSocket (§8.3) is a second, best-effort listener that folds in changes from other devices watching the same match and is not retried if it drops. The one exception is Pause/Resume (§8.2c/§8.2d): rather than compute the new local state itself, the client refetches `MatchDetails` after either action, since the server is the sole time authority (both the pause instant and the resume-time shift math happen server-side, with no client clock trusted for either) — see `TECHNICAL_DECISIONS.md` §29.

The running clock (shown as `MM:SS`) is `elapsedMatchSeconds(match, now)`, a pure function in `common/model/Models.kt` shared by this screen and the club Matches list's live badge (`MatchRow`, via `MatchesTab`) so the two can never disagree: `now - Match.kickoffAt`, or once the second half has started, `halfLengthMinutes + (now - Match.secondHalfStartedAt)` -- and if `Match.pausedAt` is set, `now` is substituted with the frozen `pausedAt` instant so the value stops advancing. Recomputed every second (this screen) or every 15 seconds (the coarser whole-minutes list badge) and on every recomposition, so it's correct across navigation/app restarts and unaffected by the device's timezone (`Instant` subtraction is timezone-agnostic). "Set minute" (§8.2e) is a server-persisted correction, not a local overlay -- `LiveMatchViewModel.onSetMinute` refetches `MatchDetails` after the call commits, the same "server is the sole time authority" pattern as Pause/Resume. Everything that matters for the record — score, events, status, the Starting XI, and the kickoff/second-half/paused timestamps — is server-authoritative.

### 8.5 Subscribe to Club Matches Updates (`GetClubMatchesUpdatesUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId] --> B[CheckUserPermissionUseCase — minClubRole=MEMBER]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> D[Subscribe to ClubMatchesBus Flow for clubId]
    D --> Z(["Return reactive Flow of ClubMatchesUpdate — bridged to a WebSocket session"])
```

The club-scoped counterpart to §8.3, mirroring it exactly but for the whole fixture list rather than one match's own event log — see `TECHNICAL_DECISIONS.md` §28 for the full design rationale (why a second bus/route instead of extending `LiveMatchBus`, what publishes to it, and the connect/disconnect lifecycle below). Unlike §8.3, there's no match to fetch first — the gate is a plain club-membership check against `clubId` directly. `CreateMatchUseCase` (§7.1), `UpdateMatchUseCase` (§7.1c), `DeleteMatchUseCase` (§7.2b), `StartMatchUseCase` (§8.0), `UpdateMatchStatusUseCase` (§8.2), `PauseMatchUseCase` (§8.2c), `ResumeMatchUseCase` (§8.2d), and `SetMatchMinuteUseCase` (§8.2e) each publish a `ClubMatchesUpdate(match=...)` or `ClubMatchesUpdate(deletedMatchId=...)` to `ClubMatchesBus` after their own transaction commits, alongside (not instead of) the push notifications and, where applicable, the `LiveMatchBus` publish those same use cases already made. `AddMatchEventUseCase` (§8.1) and `DeleteMatchEventUseCase` (§8.1b) additionally publish `ClubMatchesUpdate(score=...)` per goal, so the list's score badges update live too, without a full `Match` in the payload since nothing else about the row changed.

**Client**: `MatchesTab` opens this socket (via `ObserveClubMatchesUseCase` → `ClubDetailsViewModel.connectToClubMatchesUpdates`) for as long as it's on screen — a `DisposableEffect(details.club.id)` connects (and triggers a REST refresh via `refreshMatches`) when the composable enters composition, and disconnects when it leaves. Because `MatchesTab` is only composed while the Matches tab is selected, Navigation-Compose's normal dispose-on-navigate-away/recompose-on-return behavior doubles as the connect/disconnect trigger: switching to a different tab, or opening a match from the list into §8.4's live console, closes the socket; returning to the Matches tab (including back from that match screen) reopens it and re-fetches the list over REST first, since the socket carries no backlog for whatever changed while it was disconnected.

---

## 9. Dashboards

### 9.1 Super Admin Dashboard (`GetSuperAdminDashboardUseCase`)

```mermaid
graph TD
    A[Start: userId] --> B[CheckUserIsAdminUseCase]
    B -- False --> F(["Throw ForbiddenException"])
    B -- True --> C[Count all Clubs]
    C --> D[Count all Users]
    D --> E["Fetch 5 most recent Clubs"]
    E --> Z(["Return totals + activeSubscriptions=0 (placeholder) + recentClubs"])
```

### 9.2 Club Admin Dashboard (`GetClubAdminDashboardUseCase`)

```mermaid
graph TD
    A[Start: userId, clubId] --> B[CheckUserPermissionUseCase — minClubRole=COORDINATOR]
    B -- denied --> F(["Throw ForbiddenException"])
    B -- allowed --> C[Count Teams by club]
    C --> D[Count Players by club]
    D --> E[Count Coaches by club]
    E --> G["Fetch next 10 upcoming Matches"]
    G --> H[Fetch active Seasons]
    H --> Z(["Return aggregated ClubAdminDashboard"])
```

### 9.3 Parent Dashboard (`GetParentDashboardUseCase`)

```mermaid
graph TD
    A[Start: userId] --> B["Fetch Players linked to userId as parent (UserPlayer)"]
    B --> C{Any linked children?}
    C -- No --> Z(["Return dashboard with empty children list"])
    C -- Yes --> D{For each child}
    D --> E["Fetch next 5 upcoming Matches for that player"]
    E --> G["Fetch last 5 recent Events for that player"]
    G --> H[Build ParentDashboardChild summary]
    H --> Z2(["Return ParentDashboard with all children"])
```

This is the **only** use case in the codebase that never calls `CheckUserPermissionUseCase` — by design, since it takes no `clubId`/`teamId` and is inherently scoped to whatever children are linked to the caller's own `userId`. Worth keeping in mind since it breaks the "permission check is always first" pattern used everywhere else.

---

## 10. Cross-Cutting: The Invite System, End to End

Four different use cases can *create* an `Invite`; exactly one use case *resolves* them. This is the actual, current shape of that system (only two `InviteType`s exist: `CLUB_MEMBER` and `PARENT_PLAYER` — a player's own self-invite is just a `CLUB_MEMBER` invite):

```mermaid
graph TD
    subgraph Creation points
    P1["ProvisionClubUseCase — unknown Director email"] --> INV[Create Invite row]
    P2["AddClubMembersUseCase — unknown Member emails"] --> INV
    P3["CreatePlayersUseCase — unknown Player / Parent emails"] --> INV
    P4["UpdatePlayerUseCase — unknown Player / Parent emails"] --> INV
    end
    INV --> DB[("Invites table: email, clubId, type, role?, playerId?, relationship?")]
    DB --> TRIG["Someone registers with a matching email"]
    TRIG --> S["RegisterUseCase: find pending invites by email"]
    S --> T{For each pending invite, by type}
    T -- CLUB_MEMBER --> U["Add ClubMember: clubId + role"]
    T -- PARENT_PLAYER --> V["Create UserPlayer link: userId + playerId + relationship"]
    U --> W[Delete all processed invites]
    V --> W
    W --> Z(["User is fully onboarded into every club / player they were invited to"])
```

The physical sending of the invite email is wired up for **P1/P2 only** (`ProvisionClubUseCase`, `AddClubMembersUseCase`) via `EmailService`/Resend, sent after the transaction commits — see `TECHNICAL_DECISIONS.md` §14. **P3/P4** (`CreatePlayersUseCase`, `UpdatePlayerUseCase` — the `PARENT_PLAYER` invite path) still only ever write a row to the `Invites` table with no email, since players/parents are explicitly out of scope for now (per the developer, v1.0.0 testing covers admin/coordinator/coach/staff only). `AddStaffToAgeGroupUseCase` (§5.4) also sends email but never creates an `Invite` — every age-group staff addition requires an existing club member, so that path is always the "already has an account" notification, never the registration-invite one.

---

## 11. State of the Art: Observed Gaps & Inconsistencies

Cross-referencing every use case above against `REQUIREMENTS.md` and `TECHNICAL_DECISIONS.md` surfaced a few concrete, current-code observations worth tracking:

- [x] **`NotFoundException` is undocumented.** It's thrown throughout the codebase (`CreateMatchUseCase`, `UpdateClubUseCase`, `UpdateSeasonUseCase`, `UpdateOpponentUseCase`, `UpdatePlayerUseCase`, `UpdateTeamUseCase`, `UpdateAgeGroupUseCase`, `ManageCallUpsUseCase`, `UpdateCallUpStatusUseCase`, `UpdateMatchStatusUseCase`, most `GetMatch*`/`GetClubDetails`/`GetPlayer` use cases) but was **not** listed in `TECHNICAL_DECISIONS.md` §4's exception hierarchy table alongside `Unauthorized/Forbidden/Validation/Conflict/Database`.
  **Fixed:** added `NotFoundException` (DATA_003 -> 404) to that table.
- [x] **"Not found" is modelled inconsistently for the same resource.** A missing `Team` threw `NotFoundException` in `UpdateTeamUseCase` and `CreateMatchUseCase`, but `ValidationException` in `GetTeamDetailsUseCase`, `GetTeamPlayersUseCase`, `GetAgeGroupStaffUseCase`, `AddPlayersToAgeGroupUseCase`, and `AddStaffToAgeGroupUseCase`. Similarly, a missing `Match` threw `ValidationException` in `AddMatchEventUseCase` but `NotFoundException` everywhere else a Match is looked up.
  **Fixed:** standardized on `NotFoundException` (404) for every "an entity looked up by ID doesn't exist" case, whether it's the primary resource or one referenced in the request (e.g. a `clubId`/`ageGroupId` on a create payload) -- 17 call sites across 11 use cases converted from `ValidationException` (400): `AddMatchEventUseCase`, `CreateAgeGroupsUseCase`, `CreateTeamsUseCase` (×2), `CreateTeamUseCase` (×2), `AddStaffToAgeGroupUseCase`, `AddPlayersToAgeGroupUseCase` (×2), `GetPlayersUseCase`, `GetTeamPlayersUseCase`, `GetAgeGroupStaffUseCase`, `GetEligiblePlayersForAgeGroupUseCase`, `GetTeamDetailsUseCase` (×4). Relationship-consistency checks on an entity that *was* found (e.g. "Age group does not belong to this club/season") correctly stayed `ValidationException` -- only genuine missing-entity lookups moved. None of these branches had test coverage before (confirmed by grepping the whole test tree for `"not found"` -- zero hits); added targeted tests for all 17, including new test files for the 5 use cases (`AddMatchEventUseCase`, `AddStaffToAgeGroupUseCase`, `GetTeamPlayersUseCase`, `GetAgeGroupStaffUseCase`, `GetEligiblePlayersForAgeGroupUseCase`) that had no test file at all.
- [ ] **Pagination is still missing.** `AI_GUIDELINES.md`, `TECHNICAL_DECISIONS.md` §5 and the `REQUIREMENTS.md` roadmap all call for `limit`/`offset` pagination on list endpoints, but none of the current list use cases (`GetTeamsUseCase`, `GetPlayersUseCase`, `GetMatchesUseCase`, `GetSeasonsUseCase`, `GetOpponentsUseCase`, `GetAgeGroupPlayersByClubUseCase`) implement it yet — this confirms the open roadmap item "API Scalability: Implement pagination for all list-based endpoints" is genuinely still pending, not just undocumented.
- [x] **`UpdateClubMemberRoleUseCase` has no existence guard.** Unlike almost every other `Update*UseCase`, it never verified `targetUserId` is a real user or an existing member of `clubId` before writing the new role.
  **Fixed:** `updateMemberRole` already reported whether it matched a row (`> 0`); the use case now checks that result and throws `NotFoundException` instead of silently returning `false` for a non-member. See `UpdateClubMemberRoleUseCaseTest`.
- [x] **`CreateTeamUseCase` foreign-key validation.** It now loads the age group and rejects the request (`ValidationException`) if it doesn't exist, or its `clubId`/`seasonId` don't match the request — see §4.5. `CreateTeamsUseCase` (batch, §4.5b) applies the same check once for the whole batch.
- [x] **No self-service call-up confirmation.** `UpdateCallUpStatusUseCase` required `COACH`+ team role — there was no path for a Parent/Player to set their own call-up to "Confirmed", even though `REQUIREMENTS.md` §5 used to list "Confirmed" as a call-up status a family member would set.
  **Resolved by removing the feature, not building it:** since parents/players are out of scope for v1.0.0 and nothing had ever set a call-up to anything other than `CALLED_UP`, the whole attendance-status concept (`UpdateCallUpStatusUseCase`, its route, `CallUpStatus`'s other 4 values) was removed rather than kept as unreachable code waiting for a parent feature that doesn't exist yet. Being called up is now binary -- see §7.2, `TECHNICAL_DECISIONS.md`.
- [x] **No match-status transition validation.** `UpdateMatchStatusUseCase` accepted any `MatchStatus` value from any current status — nothing stopped jumping straight from `SCHEDULED` to `FINISHED`, and (a sharper version of the same gap) a coach could set `SCHEDULED -> LIVE` through this use case directly, completely bypassing `StartMatchUseCase`'s starting-line-up/half-length validation, since it's a separately exposed route (`MatchRoutes.kt`), not something `StartMatchUseCase` delegates to.
  **Fixed:** added an explicit transition table (`VALID_MATCH_STATUS_TRANSITIONS`) -- `SCHEDULED -> CANCELLED` only (kickoff stays exclusive to `StartMatchUseCase`); `LIVE -> {HALF_TIME, FINISHED, CANCELLED}`; `HALF_TIME -> {LIVE, FINISHED, CANCELLED}`; `FINISHED`/`CANCELLED` are terminal. An invalid transition now throws `ValidationException`. See `UpdateMatchStatusUseCaseTest`.
- [ ] **No historical/aggregated match statistics use case exists yet.** `GetMatchScoreUseCase`/`GetMatchEventsUseCase` only expose live/raw data — this matches the open `REQUIREMENTS.md` Phase 5 item "Historical match statistics (Post-match analysis)".
- [x] **Invite emails are not sent.** Every invite-creation path used to only write to the `Invites` table (§10), with no email call anywhere.
  **Partly fixed:** `ProvisionClubUseCase`/`AddClubMembersUseCase` now send emails via Resend (`TECHNICAL_DECISIONS.md` §14). The `PARENT_PLAYER` paths (`CreatePlayersUseCase`/`UpdatePlayerUseCase`) still send nothing, on purpose, since parents/players are out of scope for now.


---

## 12. Database Schema: Current State of the Art

Source of truth: `server/src/main/kotlin/.../data/database/Tables.kt` (18 Exposed `Table` objects, describing the schema for query-building) **and** the versioned SQL files under `server/src/main/resources/db/migration/` (which actually create/alter it — see `TECHNICAL_DECISIONS.md` §34). `DatabaseFactory.init()` runs Flyway against those files on every boot, so the diagram below and the real Postgres tables are guaranteed to match *as long as every `Tables.kt` change ships with a matching new migration file* — unlike the old `SchemaUtils.createMissingTablesAndColumns` approach, Flyway does not infer schema changes from `Tables.kt` automatically.

### 12.1 Entity-Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : issues
    USERS ||--o{ FCM_TOKENS : registers
    USERS ||--o{ CLUB_MEMBERS : holds
    CLUBS ||--o{ CLUB_MEMBERS : has
    CLUBS ||--o{ CLUB_PERMISSION_OVERRIDES : configures
    CLUBS ||--o{ SEASONS : runs
    CLUBS ||--o{ AGE_GROUPS : defines
    SEASONS ||--o{ AGE_GROUPS : scopes
    CLUBS ||--o{ TEAMS : owns
    AGE_GROUPS ||--o{ TEAMS : groups
    SEASONS ||--o{ TEAMS : scopes
    CLUBS ||--o{ PLAYERS : registers
    AGE_GROUPS ||--o{ AGE_GROUP_PLAYERS : pools
    PLAYERS ||--o{ AGE_GROUP_PLAYERS : eligible_for
    AGE_GROUPS ||--o{ AGE_GROUP_STAFF : staffed_by
    USERS ||--o{ AGE_GROUP_STAFF : serves_as
    USERS ||--o{ USER_PLAYERS : guardian_of
    PLAYERS ||--o{ USER_PLAYERS : guarded_by
    CLUBS ||--o{ INVITES : issues_invite
    PLAYERS ||--o{ INVITES : optionally_linked
    CLUBS ||--o{ OPPONENTS : tracks
    CLUBS ||--o{ MATCHES : schedules
    TEAMS ||--o{ MATCHES : plays_in
    OPPONENTS ||--o{ MATCHES : faced_in
    MATCHES ||--o{ MATCH_CALL_UPS : calls_up_for
    PLAYERS ||--o{ MATCH_CALL_UPS : called_up_in
    MATCHES ||--o{ MATCH_EVENTS : logs
    PLAYERS ||--o{ MATCH_EVENTS : optionally_involved

    USERS {
        uuid id PK
        varchar email UK "255 chars"
        varchar password_hash "255 chars"
        varchar phone "50 chars, nullable"
        varchar first_name "100 chars"
        varchar last_name "100 chars"
        varchar avatar_url "500 chars, nullable"
        enum status "UserStatus, default ACTIVE"
        boolean is_platform_admin "default false"
        timestamp created_at
    }
    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token UK "500 chars"
        timestamp expires_at
    }
    FCM_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token UK "4096 chars -- unique on the token alone, not (user_id, token); see Tables.kt"
        timestamp created_at
    }
    CLUBS {
        uuid id PK
        varchar name "255 chars"
        varchar short_name "50 chars, nullable"
        varchar logo_url "500 chars, nullable"
        varchar email "255 chars, nullable"
        varchar phone "50 chars, nullable"
        varchar address "500 chars, nullable"
        varchar stadium_location "500 chars, nullable -- where home matches are played, if different from address"
        enum status "ClubStatus, default ACTIVE"
        enum sport "SportType, default FOOTBALL"
        timestamp created_at
    }
    CLUB_MEMBERS {
        uuid id PK
        uuid club_id FK
        uuid user_id FK
        enum role "ClubRole"
        boolean active "default true"
    }
    CLUB_PERMISSION_OVERRIDES {
        uuid id PK
        uuid club_id FK
        varchar permission "ConfigurablePermission, unique with club_id"
        varchar min_team_role "TeamRole, nullable -- null means coordinator-only"
    }
    SEASONS {
        uuid id PK
        uuid club_id FK
        varchar name "100 chars"
        enum type "SeasonType, default EUROPEAN_YEAR"
        date start_date
        date end_date
        boolean active "default true"
    }
    AGE_GROUPS {
        uuid id PK
        uuid club_id FK
        uuid season_id FK
        varchar name "100 chars"
        integer start_year "default 0 — younger/more recent birth year; == end_year for single-year groups"
        integer end_year "default 0 — older birth year; eligibility is always checked against this"
        integer team_size "nullable — default a-side count for this age group's teams"
    }
    TEAMS {
        uuid id PK
        uuid club_id FK
        uuid age_group_id FK
        uuid season_id FK
        varchar name "100 chars"
        varchar code "50 chars, nullable"
        boolean active "default true"
        integer team_size "nullable — a-side count this team plays"
    }
    PLAYERS {
        uuid id PK
        uuid club_id FK
        varchar first_name "100 chars"
        varchar last_name "100 chars"
        date birth_date
        integer shirt_number "nullable"
        varchar photo_url "500 chars, nullable"
        boolean active "default true"
        enum position "PlayerPosition, nullable"
        integer height_cm "nullable"
        integer weight_kg "nullable"
        varchar emergency_contact "255 chars, nullable -- free text, no structured contact model yet"
    }
    AGE_GROUP_PLAYERS {
        uuid id PK
        uuid age_group_id FK
        uuid player_id FK
    }
    AGE_GROUP_STAFF {
        uuid id PK
        uuid age_group_id FK
        uuid user_id FK
        enum role "TeamRole"
    }
    USER_PLAYERS {
        uuid id PK
        uuid user_id FK
        uuid player_id FK
        enum relationship "UserPlayerRelationship"
    }
    INVITES {
        uuid id PK
        varchar email "255 chars"
        uuid club_id FK
        enum type "InviteType"
        enum role "ClubRole, nullable"
        uuid player_id FK "nullable"
        enum relationship "UserPlayerRelationship, nullable"
        timestamp created_at
    }
    OPPONENTS {
        uuid id PK
        uuid club_id FK
        varchar name "255 chars"
        varchar short_name "50 chars, nullable"
        varchar logo_url "500 chars, nullable"
    }
    MATCHES {
        uuid id PK
        uuid club_id FK
        uuid team_id FK
        uuid opponent_id FK
        varchar location "500 chars, nullable"
        timestamp date_time
        enum status "MatchStatus, default SCHEDULED"
        text notes "nullable"
        integer half_length_minutes "nullable -- set at kickoff by StartMatchUseCase"
        timestamp kickoff_at "nullable -- wall-clock instant of kickoff, set by StartMatchUseCase"
        timestamp second_half_started_at "nullable -- wall-clock instant the 2nd half resumed, set by UpdateMatchStatusUseCase on HALF_TIME -> LIVE"
        timestamp paused_at "nullable -- wall-clock instant the clock was frozen, set by PauseMatchUseCase, cleared by ResumeMatchUseCase"
    }
    MATCH_CALL_UPS {
        uuid id PK
        uuid match_id FK
        uuid player_id FK
        enum status "CallUpStatus -- single value (CALLED_UP), see enum reference table"
        text notes "nullable"
        boolean is_starting "default false -- Starting XI at kickoff, set by StartMatchUseCase"
    }
    MATCH_EVENTS {
        uuid id PK
        uuid match_id FK
        enum type "EventType"
        integer minute
        uuid player_id FK "nullable"
        varchar opponent_player_name "255 chars, nullable"
        boolean is_opponent "default false"
        timestamp created_at
    }
```

### 12.2 Constraints & defaults worth knowing

- **Unique indexes**: `users.email`; `refresh_tokens.token`; `club_members(club_id, user_id)` — one membership per user per club (`V3__unique_club_members.sql`); both add-member repository methods insert with `ON CONFLICT DO NOTHING`, so re-adding an existing member keeps their current row and role rather than duplicating or downgrading it; `fcm_tokens.token` — deliberately on the token alone rather than `(user_id, token)`, since a token identifies one app install, not one user; a registration endpoint that gets the same token again (shared device, or a re-login without reinstalling) should reassign the existing row's `user_id` rather than insert a duplicate; `seasons(club_id, type, start_date, end_date)` — a club can't have two identical seasons; `age_groups(club_id, season_id, start_year, end_year)` — one age group per birth-year span per club per season (an overlapping-but-not-identical span isn't blocked, e.g. two ranges sharing a birth year); `age_group_players(age_group_id, player_id)` — a player is in an age group at most once (makes "same player on two teams in one age group" impossible); `match_call_ups(match_id, player_id)` — a player can only appear once in a given match's call-up list.
- **Nullable foreign keys**: `invites.player_id` (only set for `PARENT_PLAYER` invites) and `match_events.player_id` (null when `is_opponent = true`, since the event belongs to a player on the opposing team who isn't in the `players` table).
- **No `onDelete` behavior is configured on any foreign key** — every `.references(...)` call in `Tables.kt` uses the Exposed default (`NO ACTION`). Deleting a row that still has children (e.g. a `Club` with existing `Teams`) will fail at the database level rather than cascading, nulling out, or being blocked at the application layer with a friendlier error.
- Several `enum` columns are nullable independently of their parent row's lifecycle: `club_members.role`/`age_group_staff.role`/`user_players.relationship` are all non-null (required at creation), while `invites.role` and `invites.relationship` are nullable because a single `Invite` row is reused for both invite shapes (see §10) and only one of the two is populated depending on `type`.

### 12.3 Enum reference

| Enum | Values | Used by |
|---|---|---|
| `UserStatus` | `ACTIVE`, `INACTIVE`, `PENDING`, `SUSPENDED` | `users.status` (default `ACTIVE`) |
| `ClubStatus` | `ACTIVE`, `INACTIVE`, `PENDING` | `clubs.status` (default `ACTIVE`) |
| `SportType` | `FOOTBALL`, `FUTSAL` | `clubs.sport` (default `FOOTBALL`); required in `ProvisionClubRequest`. Each constant also carries a code-level `teamSizes: List<Int>` (FOOTBALL → 4/5/7/9/11, FUTSAL → 4/5) — the allowed a-side counts for `age_groups.team_size` / `teams.team_size`; not persisted, not configurable per club. |
| `ClubRole` | `CLUB_ADMIN`(5), `COORDINATOR`(4), `MEMBER`(1) | `club_members.role`, `invites.role` |
| `TeamRole` | `COACH`(3), `STAFF`(2) | `age_group_staff.role` |
| `SeasonType` | `CALENDAR_YEAR`, `EUROPEAN_YEAR` | `seasons.type` (default `EUROPEAN_YEAR`) |
| `UserPlayerRelationship` | `FATHER`, `MOTHER`, `GUARDIAN`, `OTHER` | `user_players.relationship`, `invites.relationship` |
| `InviteType` | `CLUB_MEMBER`, `PARENT_PLAYER` | `invites.type` |
| `MatchStatus` | `SCHEDULED`, `LIVE`, `HALF_TIME`, `FINISHED`, `CANCELLED` | `matches.status` (default `SCHEDULED`) |
| `CallUpStatus` | `CALLED_UP` (single value -- see §7.2/§11) | `match_call_ups.status` (default and only `CALLED_UP`) |
| `EventType` | `GOAL`, `OWN_GOAL`, `ASSIST`, `SUBSTITUTION_IN`, `SUBSTITUTION_OUT`, `YELLOW_CARD`, `RED_CARD` | `match_events.type` |
| `PlayerPosition` | `GOALKEEPER`, `DEFENDER`, `MIDFIELD`, `WINGER`, `STRIKER` | `players.position` (nullable) |

`TeamRole` is now `COACH`/`STAFF` only — the unused `PARENT`/`PLAYER` values were removed when coaches/staff moved from teams to age groups (`age_group_staff`). Players and family members are modelled entirely through `age_group_players` / `user_players` and never appear in a staff table. The enum keeps the name `TeamRole` for now; `CheckUserPermissionUseCase`'s `minTeamRole` parameter is interpreted as the minimum age-group staff role.

### 12.4 Current persistence setup

- **Engine**: PostgreSQL, both dev and production. H2 has been removed entirely. The connection is built from `ServerConfig.kt`'s `DATABASE_HOST`/`DATABASE_PORT`/`DATABASE_USER`/`DATABASE_PASSWORD`/`DATABASE_NAME` env vars — host and password are required explicitly when `APP_ENV=production` (no silent fallback), while the database name defaults per environment (`squadify_dev` / `squadify_production`), so dev and production stay isolated even against the same Postgres server. `docker-compose.yml` at the repo root provides a local Postgres matching the dev defaults.
- **Schema creation / migrations**: Flyway, run from `DatabaseFactory.init()` on every boot (`baselineOnMigrate(true)`, `baselineVersion("1")`) against `server/src/main/resources/db/migration/` — currently `V1__baseline.sql` (the 17 original tables), `V2__club_permission_overrides.sql`, and `V3__unique_club_members.sql`. An existing untracked database is baselined at V1 without re-running it; an empty one runs V1 in full. This replaced `SchemaUtils.createMissingTablesAndColumns` — see `TECHNICAL_DECISIONS.md` §34. Every schema change now needs a new `V<n>__description.sql` alongside the `Tables.kt` edit.
- **Legacy backfill**: right after Flyway, `DatabaseFactory.init()` still runs a raw, idempotent SQL block that backfills `age_groups.start_year`/`end_year` from the old single `birth_year` column (if it still exists) and then drops that column (§4.3/§5.3). It's a no-op once `birth_year` is gone. It was deliberately left outside the migration chain because there's no way to confirm every environment has already run it.
- **Connection pool**: HikariCP, `maximumPoolSize = 3` in dev / `10` in production, `autoCommit = false`, isolation level `TRANSACTION_REPEATABLE_READ`.

### 12.5 Schema-level gaps observed

- [ ] No `onDelete` cascade/set-null strategy anywhere — every FK relies on the DB's default no-action behavior (see §12.2).
- [x] The Flyway/`SchemaUtils` mismatch: **fixed**, since Flyway is now the real migration mechanism (§12.4, `TECHNICAL_DECISIONS.md` §34). There are still no down/rollback scripts, as Flyway Community only supports forward migrations.
- [ ] `AgeGroups.start_year`/`end_year` each have a `.default(0)` that has no real-world meaning and is never actually relied upon for a normal insert (`CreateAgeGroupsUseCase` always supplies explicit values) — the default exists only as the "not yet migrated" sentinel the one-off `birth_year` backfill above checks for.
