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
- **Club**: Branding and contact info, a **country** (for public holidays when creating training sessions; *planned*, Portugal by default), plus a **sport** (`FOOTBALL` / `FUTSAL` for now) chosen when the club is provisioned. The sport also fixes the set of allowed **team sizes** (a-side counts) — football 4/5/7/9/11, futsal 4/5 — used by age groups and teams below. Everything else below the club is sport-agnostic.
- **Season**: Time-bound periods. Supports two types:
    - **Calendar Year**: January 1st to December 31st (e.g., Brazil).
    - **European Year**: July 1st to June 30th (e.g., Europe).
- **Age Group**: Categorization of players by a birth-year span (`startYear`/`endYear`) — a single year for most (football) groups, or two consecutive birth years for groups (typically futsal) that need both cohorts to field a squad; owns the player pool and the coaching staff. Carries an optional **team size** — the a-side count its teams play by default (one of the club sport's allowed sizes).
- **Team**: A named group under an age group, used to schedule matches. Has its own **team size**, pre-filled from the age group's default but overridable (e.g. a 7-a-side age group with one team that plays 9-a-side).
- **Player**: Personal data and stats.
- **Opponent**: Management of external clubs/teams for matches (name, short name, logo, home stadium and address, contact, notes).
- **ClubMember**: Links Users to Clubs with a specific Role.
- **AgeGroupPlayer**: Links a Player to an Age Group pool (many-to-many; `(ageGroupId, playerId)` unique).
- **AgeGroupStaff**: Links a User (coach/staff) to an Age Group with a role.
- **UserPlayer**: Links Parents to Players (supports multiple guardians).
- **TrainingSession** *(planned, Phase 8)*: One training of an age group on a given date: time, location, duration, status (scheduled or cancelled) and its exercises.
- **TrainingExercise** *(planned)*: An exercise in the age group's library: name and description (optionally duration and objectives). Reusable across sessions.
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
- **Match**: Details about opponent (linked to Opponent entity), location, date, status, and type (`MatchType`: `FRIENDLY`, `CHAMPIONSHIP`, `TOURNAMENT`, required when scheduling; older matches may have none). Played in 2 halves (default) or 1 -- a single period with no half time, for youth fixtures of one 20/25/30-minute period. Played with its team's size unless it says otherwise (a 9-a-side team playing a 7-a-side game); see the roadmap item below.
- **MatchCallUp**: List of players called up for a match. Binary for now (called up or not) -- the richer attendance-status model (Confirmed/Declined/Not Available/Standby) was removed since it required a parent/player self-confirmation path that doesn't exist yet; see `PROCESS_FLOWS.md` §11.
- **Live Match**: Real-time event logging by coaches (Goals, Assists, Substitutions, Cards).
- **MatchStatus**: `SCHEDULED`, `LIVE`, `HALF_TIME`, `FINISHED`, `CANCELLED`.

## 5b. Training & Periodization *(planned -- see Phase 8)*
- **Training sessions** belong to an **age group** (like the player pool and the staff), never to a team.
- **Creating sessions** (a generator, nothing about it is saved):
  - **Days and times:** the training days and hours, e.g. Monday 18:00–19:00 and Thursday 18:30–20:00. They aren't remembered, so the form starts empty each time.
  - **Period:** a start and an end date. Not saved either; it only says which dates to create sessions on.
    - **Not bound by the season:** the season's dates are the football association's official calendar, but clubs set their own; training often runs into June or July past the season's end. So the period (and a one-off session's date) can fall outside the season. The form opens on today to the season's end, as a starting point.
  - **Location:** defaults to the club's stadium location.
  - **Holidays:** an option "Also on public holidays", off by default.
    - **Which holidays:** the national ones of the club's country (a new country field on the club, Portugal by default), worked out on the server, including the moving ones (Good Friday, Corpus Christi).
    - **Municipal holidays are ignored:** they're edge cases, and clubs usually train on those days.
  - **Preview:** before creating, the app lists every date it will create, one session per matching day in the period.
    - **Holidays:** when holidays are left out, they're shown as skipped.
    - **Overlaps:** a date and time that already has a session gets a warning, because the new session overrides it entirely. The old session's exercises and player ratings are deleted. When it has any, the warning says what will be lost (e.g. "Has 4 exercises and 18 ratings, which will be deleted").
    - **Confirmation:** if any session with exercises or ratings will be overridden, "Create" asks for confirmation first.
  - **Create:** "Create N sessions" creates them all at once.
- **Sessions:**
  - **Contents:** each session has a date, time, location and duration.
  - **One-off sessions:** a single extra session can be added outside the weekly days. It follows the same permission as creating sessions.
  - **Cancelling:** a single session can be cancelled (rain, a holiday, etc.). It stays visible as cancelled and asks for no evaluations.
- **Exercises:**
  - **Contents:** a session is made of exercises, each with a name and a description of what's done.
  - **Library:** exercises are reusable across sessions. Each one is written once and picked again in later sessions.
- **Player evaluations:**
  - **Rating:** after a session, each player in the age group's pool gets a rating from **0 to 10, with up to two decimals** (e.g. 7.5 or 7.55; the developer, 2026-10-27).
  - **Input:** a plain text box per player, where the rating is typed (two decimals at most). Empty means not rated yet. The list is sorted by name (the default) or position.
  - **0 means "did not take part"**, so the evaluation doubles as the attendance record. A player who trained always gets more than 0.
  - **Who can evaluate:** any staff member of the age group, coach or staff.
  - **Visibility:** training ratings are **staff-only**. Players and parents never see them, as with match ratings.
- **Periodization (planning cycles):** each cycle covers a fixed period of the season. The plans come first: the coach sets the objectives of the year, the month and the week before preparing that week's sessions, so the plans are built before the sessions, and a session shows its week's and month's objectives.
  - **Macrocycle (year plan):** the season's plan for the age group: phases and main objectives.
  - **Mesocycle (month plan):** each month's focus and objectives within the year plan.
  - **Microcycle (week plan):** each week's plan, made of that week's sessions and their focus.
  - **Sessions don't depend on them:** a session exists whether or not its week has a plan.
- **Permissions:**
  - **Configurable:** creating and editing training sessions (in bulk or one-off) and plans (macro, meso and micro cycles) is a club setting (`ConfigurablePermission`), like the other actions.
  - **Who decides:** coordinators and admins can always do it. In Club settings → Permissions, the coordinator decides whether the age group's coaches, or coaches and staff, can do it too.
  - **Evaluating players:** this isn't part of that setting. It's open to any coach or staff member of the age group (above).
- **Home alerts and to-dos** (club Home tab, for the people who manage the age group):
  - **Evaluations to-do:** when a session has ended (e.g. after 19:00 on a Monday 18:00–19:00), a to-do appears ("Training of {date} needs player evaluations") until its evaluations are filled in, the same way a finished match asks for its report. Cancelled sessions don't count.
  - **Missing plan alerts:** when a cycle's period starts without its plan, an alert says so. A season starting with no macrocycle, a month with no mesocycle, and a week with no microcycle each raise one.
- **Training stats:**
  - **Age Group Details:** the Stats tab shows each player's attendance and average training rating.
  - **Player Details:** shows the player's own attendance and average training rating.
  - **Visibility:** staff only, like the ratings.

## 5c. Squadify for Coaches *(planned -- see Phase 9)*
The developer, 2026-10-27: a lighter, separate product for a coach on their own, outside any club. See `TECHNICAL_DECISIONS.md` §72 for how it's built.
- **A separate app**, Android and iOS only (no web or desktop), with its own store listing. "Squadify for Clubs" (this app) and "Squadify for Coaches" don't share accounts or data: a coach's personal teams are in the coach app, their club's in the clubs app.
- **What it has:** everything about games, since stats for the games are the point of the app: the coach's age groups and teams, players, seasons, opponents, the games calendar, call-ups, the live game, the record after the game, the match report and ratings, stats (Full stats included) and the games history, built from the same screens as the clubs app.
- **What it leaves out:** training (§5b), a club concept (the developer: "more for the clubs"), and parents and players as users. The app is for staff organising their games, so every member is staff.
- **No club and no roles.** Everything belongs to the coach's own space. The coach can invite other staff, and every member has full access.
- **The owner:** whoever created the space. Only they pay, invite and remove members.
- **Plans** (pricing still to be decided), for the whole space, not per person:

    | Plan | Features | Age groups | Members |
    |---|---|---|---|
    | **Free**, with ads | Live game, calendar, history | 1 | 1 (the owner) |
    | **Basic** | + stats | Up to 3 | Up to 3 per age group |
    | **Pro** | + staff following the game live | No limit | No limit |

- **Going down a plan** (payments stopped, or a change of plan): nothing is deleted, and stats keep being calculated, so going back up brings everything back.
    - **Members over the limit:** the first ones added to each age group keep access (the first three on Basic; only the owner on Free). The others lose access until the plan allows them again.
    - **Age groups over the limit:** the first ones created stay in use (three on Basic, one on Free). The others are kept but locked until the plan allows them again.
- **Store requirements:** coaches sign up in the app, so the app must let them delete their account (App Store and Google Play rules). Ads on Free need the EU consent form first (Google's UMP). Each app has its own privacy policy and data-safety form.
- **Moving to a club:** not for now. Later, possibly moving one age group (nothing more) from the coach app to a club.

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
    - [x] Remove a player from an age group (✕ on Members, for whoever can add players to it): they stay in the club, their matches still show them, and the age group's season stats stop counting them. Disable a player from the club's Players screen (admins and coordinators by default; a club admin can make it admins only with the "Disable and reactivate players" permission, which only admins can change): they stay in their age groups and nothing is deleted, but they leave upcoming call-ups and can't be called up while disabled; listed as disabled, and can be reactivated from the same screen. See `TECHNICAL_DECISIONS.md` §54.
    - [x] Team Staff management (Coaches/Staff assignment).
    - [x] Edit team: name and team size (the a-side format), from Team Details, for coordinators and the team's own coach.
    - [x] Delete team: from Edit team, for whoever can create teams in the age group. All its matches go with it, played ones included (with their call-ups, events, stats and reports); the players stay in the age group. See `TECHNICAL_DECISIONS.md` §53.
    - [x] Edit age group: name, birth-year span and default team size, from Age Group Details, for coordinators. A span that would leave players already in the pool ineligible is refused.
- [x] **Phase 4: Match & Call-ups**: Calendar and attendance management.
    - [x] Opponent management (dedicated entity and table).
    - [x] Match creation and listing. A new match must say where it's played: Home, Away or Neutral, with no default. That prefills the location (our ground, the opponent's, or none), which stays editable, and can be changed in Edit match. The home side is shown first everywhere, and our club on neutral ground. See `TECHNICAL_DECISIONS.md` §63.
    - [x] Call-up management (batch player assignment). On Match details the call-up is a draft saved with "Save call-up" (one list of the pool, a tap selects or unselects a player), available once it has the team's minimum (its size). Saved and unchanged, the button becomes "Start match". Who sees what depends on their rights: the editor, the ready call-up and "Start match", a "not ready yet" message, or the details only. See `TECHNICAL_DECISIONS.md` §62.
- [x] **Phase 5: Live Match & Stats**: Real-time events and automated statistics calculation.
    - [x] Real-time broadcasting via **WebSockets**.
    - [x] Live Event logging (Goals, Assists, Cards, Subs).
    - [x] Live Score calculation.
    - [x] Live match console (client): kickoff line-up picker, live squad/events tabs, event logging & deletion, full-time summary. Pause/Resume freezes the running clock for a mid-half stoppage, and "Set minute" applies a manual correction -- both server-authoritative (persisted and broadcast), not client-clock-derived, and neither behind a confirmation dialog (lower-stakes, reversible). See `TECHNICAL_DECISIONS.md` §29.
    - [x] Club-wide Matches tab live-updates via a second, club-scoped WebSocket (`/clubs/{clubId}/matches/live`) — connects while the tab is on screen, disconnects on tab-switch or navigating into a match, reconnects + REST-refreshes on return. Fixture create/edit/delete/kickoff/status-change and per-goal score updates all broadcast live. See `TECHNICAL_DECISIONS.md` §28.
    - [x] The running clock formula is shared (`elapsedMatchSeconds` in `common/model/Models.kt`) between the live match console and the Matches list's live badge, so the two can never disagree, and both reflect a paused clock identically. See `TECHNICAL_DECISIONS.md` §29.
    - [x] Historical match statistics (Post-match analysis): per-player stats (started, minutes played including stoppage time, goals, assists, own goals, cards) are stored for every finished match, and a played match can no longer be deleted. Its events change only through the match record (see "Editing the match record after the game" below). The coach's post-match report (analysis plus 0–10 player ratings) has its editor (players sorted by name or position), read view, Match Details card and Home to-do, and ratings are shown next to each player's stats. See `TECHNICAL_DECISIONS.md` §41. Season stats are shown outside the report too:
      - Player Details has season totals and a per-match list, with a season switcher.
      - Team Details has a season record (W/D/L, goals, form) and team leaders: one list under the other, Ranking (staff only: the final ranking, games and trainings together, a missed training counting as 0, as in Full stats), Goals, Assists, Time, each the top five, every row with the player's photo or a placeholder.
      - [x] **Full stats screen** (design: the "Squadify Full Stats" canvas; see TECHNICAL_DECISIONS §65): every player's stats in one table, for a team and for an age group, opened from Team leaders and from the age group's leaders card (the age group's Stats tab ends with the same leaders card as a team, ranking on games and trainings together). Team leaders only shows the top five of four categories.
        - **Who's listed** (the developer, 2026-10-24): the screen always offers the same team filter, the age group (by its name) and each of its teams. From an age group, the age group is selected: its whole pool. From a team, that team is selected: only the players who played for it. Either can be changed. The title is "<age group> stats".
        - **Columns**, in this order: final ranking, training ranking (average session rating), games ranking (average match rating), games won, games drawn, games lost, goals, assists, minutes, yellow cards, red cards. A long press on a header shows its full name. Ratings and rankings are for staff only, as everywhere.
        - **Final ranking:** the sum of every game and training rating in the period, divided by how many there are. For example, a week with one game and three trainings is (game + training 1 + training 2 + training 3) ÷ 4. Only ratings the player actually has count, and an absent training counts as a 0: 8, 8 and Absent is (8 + 8 + 0) ÷ 3.
        - **Won / lost / drawn:** the games the player took part in.
        - **Show and hide columns:** the coach picks which stats are shown, e.g. hiding goals. The choice is remembered on the device.
        - **Filters:** a date range (from / to, e.g. last Friday to today, for "this week's ranking"), as well as the season and the match type (All / Official / Tournament / Friendly).
        - **Sorting** by any column, the player's name included, and tapping a row opens the player.
      - [x] **Editing the match record after the game** (the developer, 2026-10-26; see TECHNICAL_DECISIONS §70), by any of the age group's staff, always editable:
        - **A game followed live:** its goals (scorer, assist), own goals and cards can be added, changed or deleted afterwards. Who started, the substitutions and the minutes played stay as they were.
        - **A game not followed live** (no battery, no signal): once its kickoff has passed, "Enter result" records its goals (our scorer can be left unknown), cards and each player's minutes, typed in (sorted by name or position; Next moves between players), and finishes it. Halves default to 30 minutes, and an event's half follows its minute. It has no substitutions or timeline. One push announces the final result.
        - Every save recalculates the stats, keeping the report's ratings.
      - [x] **A match's own team size** (the developer, 2026-10-28; see TECHNICAL_DECISIONS §74): a team's size is only its usual format, so each match can be played with another of the sport's sizes, e.g. 7-a-side for a 9-a-side team. It's picked when scheduling (starting on the team's), and can change in Edit match and on the pitch before kickoff, and when entering the result of a game not followed live. Once a game has been followed live its size is fixed. The call-up's minimum, the pitch and kickoff all use the match's size. Every match stores its size (existing ones took their team's), so changing a team's size never changes its matches.
      - [x] **Games not played** (the developer, 2026-10-26; see TECHNICAL_DECISIONS §71): a game still not started 2 hours after its kickoff moves from Upcoming to its own "Not played" section, between Upcoming and Recent results in every games list (club, team, age group): the order things happen in. Home shows a "Result missing" alert for it to whoever can enter the result, until it's started, entered or cancelled.
      - [x] **A finished match's record on Match details** (the developer, 2026-10-25; see TECHNICAL_DECISIONS §66): once the game is played, the call-up is no longer shown. Its tabs are in the bottom bar, as elsewhere in the app. The staff (coordinators, admins and the age group's staff) get four:
        - **General:** the match report card and the match's details.
        - **Stats:** each player who played, with their ranking (the report's rating, once completed), minutes, goals, assists, yellow and red cards. Tapping a header sorts by it, a long press names it. Players called up who never came on are listed under the table.
        - **Events:** every event of the game, by half, filtered by All / Goals / Substitutions / Cards.
        - **Timeline:** the pitch at every change of the team on it: the side that kicked off, then each substitution, red card, new formation or swap of positions. A slider at the bottom goes from the start of the game to the end.
        - Other club members (players, parents) get two tabs: General with the match's details (no report) and Events with only the goals and their scorers.
      - Team Details is split into Stats · Matches · Members tabs. The Members tab lists only the players who played for that team.
      - Age Group Details is split into Stats · Teams · Matches · Members tabs. Stats shows results by team and how minutes are spread across the pool.
      - Both can be filtered by match type (All / Official / Tournament / Friendly; unselected segments show 5 letters and a ".", the selected one its full name, animated), and ratings are shown to staff only (see `TECHNICAL_DECISIONS.md` §43).

    - [x] **Live minutes played** (design 36): the live console's Squad tab shows each player's minutes so far, live, with a bar against the fair share, the current spell ("on for 18'") or "off since", and amber flags for players below the share or not played yet; the bench sorts fewest minutes first. Substitutions are unlimited and in any direction (rolling subs in 7- and 9-a-side), and the substitution pickers list candidates with their minutes. Half time shows first-half totals and who's furthest below the share. The clock has a real pause button and is tapped to correct it. Full time shows a minutes table and links to the match report. See `TECHNICAL_DECISIONS.md` §45.
    - [x] **Live pitch view** (the developer's spec, 2026-10-23; design: the "Squadify Pitch View" canvas; see TECHNICAL_DECISIONS §64): the live console shows our team on a pitch, not only as a list.
      - **No separate setup screen.** Opening a game that hasn't started goes straight to the live console. The game can only kick off once every position on the pitch has a player.
        - Before kick-off the pitch starts **empty**: every slot is a dashed "+".
        - Tapping an empty slot opens a bottom sheet of the called-up players who aren't placed yet; picking one puts them there.
        - Tapping a placed player before kick-off lets the coach replace them or take them off the pitch.
        - Players not placed are the bench.
        - Next to the formation chip, a **half-length chip** ("30 min"; options 20 / 25 / 30 / 40 / 45, or the match length for a one-half game) replaces the setup screen's picker. It's gone once the game starts.
      - **Tabs:** Pitch (opened first), Squad (the minutes list, as today) and Events (as today).
      - **Layout:** the goalkeeper in the penalty area. The rest of the pitch is three equal zones, defence, midfield and attack. Each zone is split into as many equal slots as the formation has players there, and each player stands in the centre of their slot. For example, 2-3-1 in 7-a-side: two defenders, each centred in half of the defensive zone; three midfielders in thirds of the middle zone; one striker in the centre of the attacking zone.
      - **Formation:** written "D-M-A" (defenders-midfielders-attackers). The three numbers plus the goalkeeper must make the team size, so 2-3-1 (6 + 1) for 7-a-side.
      - **Default formation** for every team, from its size: 5-a-side 1-2-1, 7-a-side 2-3-1, 9-a-side 2-4-2, 11-a-side 4-4-2.
      - **Changing formation:** the "Change" sheet has a text field, the sum as a hint, quick picks and an inline error. The new formation is refused unless the numbers add up. Saving a team's own formations comes later.
      - **Re-shaping keeps the slot order.** Slots are numbered goalkeeper, defence, midfield, attack, and each player keeps their slot number when the formation changes. For example, going from 2-3-1 to 1-3-2: slot 3 moves from defence to midfield, slot 6 from midfield to attack, and everyone else stays put. Moving players to their right positions is up to the coach.
      - **Saved and synced:** the formation and who is in each slot are saved with the game. Every staff device shows the same pitch live, and the starting formation fills the match report's tactic.
      - **Player tokens:** each player on the pitch shows their photo (or the placeholder), the shirt number, the name under it, a small bar for their minutes so far and a booking mark. Tapping one opens today's player sheet: goal (then the assister), own goal, yellow, red, substitution. The sheet gets a header with the photo, number, position and minutes on the pitch.
      - **Drag and drop:**
        - Drag a bench player onto an empty slot to place them (before kick-off).
        - Drag a bench player onto a player on the pitch to substitute them (during the game); the incoming player takes the outgoing player's slot.
        - Drag one player on the pitch onto another to swap their positions.
      - **Bench and sent off:** the bench is a row under the pitch. A sent-off player leaves the pitch, is shown apart, and leaves an empty slot.
      - **Match buttons:** Kick-off, then Half-time, then Start 2nd half, then Full-time, as a full-width bar at the bottom, as today.
      - **Direction:** the scoreboard keeps the app's rule (home team on the left, our club on the left on neutral ground). The pitch puts our goal on our scoreboard side: the left in landscape, the bottom in portrait. In landscape only, a flip button mirrors the pitch on that device. It never changes the scoreboard.
      - **As built, beyond the spec:**
        - Tapping a bench player before kick-off puts them in the first empty position.
        - During the game, the player sheet gains "Change position" (swap with someone, or move into an empty position), and tapping an empty position asks who moves there.
        - The report's tactic shows the starting formation only until the report is first saved.
- [ ] **Phase 6: Dashboards & Aggregated Summaries**: Role-specific overview endpoints.
    - [/] **Super Admin Summary**: Global platform health (Total clubs, active users, system logs).
    - [/] **Club Admin Summary**: Club overview (Total players/teams, financial status placeholder, upcoming matches).
    - [/] **Parent/Player Summary**: Personalized view (My next matches, my recent performance/events).
    - [x] **API Scalability**: the lists that grow without bound come a page at a time, filtered on the server: the club's matches (Matches tab), players (Players screen), members (Club settings → Members), opponents (Opponents screen) and the players who can join an age group (Add players). The next page loads on scrolling. Lists held to a season, an age group, a team or a match stay whole on purpose. See `TECHNICAL_DECISIONS.md` §68.
    - [x] **Email Service**: Integrated with Resend for club/age-group association notifications -- see `TECHNICAL_DECISIONS.md`'s EmailService section. `squadify.pt` is now a verified Resend sending domain (`RESEND_FROM_ADDRESS` set accordingly); no longer limited to the sandboxed `onboarding@resend.dev` address.
    - [x] **Club members list** (design 34): Club settings is split into Details · Members · Permissions tabs. Members lists everyone in the club by club role, with search, a role filter, what each person does (coach/staff age groups, parent of a player), and the pending club invites (resend, cancel). Invites can be sent as Member or Coordinator. Coordinators can change roles between Member and Coordinator; only admins can grant or remove Admin, and the last admin can't be demoted. Tapping a member opens Member details (design 35): contact (tap to mail or call, copy), the club role, and their age-group roles and linked players, which link to the age group or player. A pending invite opens with Resend and Cancel. Members can be removed from the club (their age-group staff roles go too; parent links stay). Only admins and coordinators remove members. There is deliberately no "Leave club" for members themselves (product decision).
    - [/] **Invite status visibility**:
      - **Club invites (done):** pending club invites are listed in Club settings → Members, with their intended role, how long ago they were sent, Resend and Cancel (see the Club members list item above). Age-group staff never need an invite of their own, because staff must already be club members, and a club member who hasn't registered yet shows up there as a pending invite.
      - **Still pending:** parent invites (`PARENT_PLAYER`, created from Create/Edit Player) aren't shown anywhere, so there's no way to tell a parent invite is outstanding. That belongs with the deferred parent/player work.
    - [/] **Push / In-App Notifications**: Provider chosen (Firebase Cloud Messaging, reusing the Firebase project already integrated for Crashlytics/App Distribution -- see `TECHNICAL_DECISIONS.md` §12). Android client built (token registration/removal, multiple notification channels, receiving) -- see §21/§22. **Verified working end to end on a real device.** Server-side sending covers both association events that already trigger an email (club membership added, age-group staff added -- §23), nine club-wide events sent to every club member rather than just the people directly affected (§24/§26): the five live-match moments (kickoff, goal, half-time, second half, full-time), three schedule changes (a fixture created, rescheduled, or cancelled), and a team being created under an age group. Environments (dev/staging/prod) are isolated from each other at the Firebase level too, not just by database (§25). `FIREBASE_SERVICE_ACCOUNT_JSON` is set on the Railway **staging** server, and pushes are confirmed working there. Tapping a notification opens what it's about (the match, age group, team or club) inside its club, after sign-in if needed (§56). Still pending: the same credential on the production Railway server, in-app (non-push) notifications, and iOS/Desktop/Web support.
    - [ ] **Parent/Player Notifications** (deferred -- club features come first): the developer explicitly deferred these, distinct from the club-wide match pushes above since they'd need to resolve `UserPlayers` links (a player's parent), not `ClubMembers`. Candidates raised in conversation: a player called up for a match (`ManageCallUpsUseCase`), a player added to an age group's pool (`AddPlayersToAgeGroupUseCase`, the direct parallel to the club-membership/age-group-staff pushes in §23, but for players). Not started.
    - [x] **Production Database**: Migrated from H2 to PostgreSQL for both dev and production, distinguished by database name (`DATABASE_NAME`, defaulting to `squadify_dev`/`squadify_production`) — see `TECHNICAL_DECISIONS.md` §3.
- [/] **Phase 7: Client Applications (KMP)**: Multiplatform UI development.
    - [x] Initial Project Setup (Shared, Android, iOS).
    - [x] Multi-language support (EN, PT) implementation.
    - [x] Shared UI components & Themes.
    - [x] Dynamic Club Theming: a club sets two colours in Club settings → Details (both or neither, a strong main colour and a lighter second one, checked for contrast). Every screen opened from the club uses them for its bars, buttons and selected states; wins, ratings and other "good" greens stay green. Splash, sign-in, register, forgot password and My clubs always use Squadify's own colours, and so does the app logo. See `TECHNICAL_DECISIONS.md` §58.
    - [x] Authentication flows (Login/Register): login, register, session check on launch, automatic token refresh on 401, logout, and **Forgot password**. Forgot password emails a 6-digit code (15 minutes, 5 tries, one per minute) that sets a new password and signs the account out everywhere. An email with no account is reported straight away. See `TECHNICAL_DECISIONS.md` §48.
    - [x] Account Management: User profile viewing and editing. The Account screen edits name and phone and changes the password (`TECHNICAL_DECISIONS.md` §33), and changes the email (next item).
    - [x] **Change email**: from Account, enter the new address and the current password. A 6-digit code goes to the new address, and entering it switches the account. It reuses the Forgot-password code rules: 15 minutes, 5 tries, one per minute. The address can't belong to another account. Every other session is signed out, and the old address is told about the change. See `TECHNICAL_DECISIONS.md` §49.
    - [x] **Logo and photo upload** (clubs, opponents, players, users):
      - **Done (clubs):** "Add logo" / "Change logo" / "Remove" in Club settings → Details, for coordinators and admins. The server resizes it to a 512×512 PNG and stores it in a Railway Storage Bucket (the disk in dev). Each change gets a new file name and the old file is deleted first. Shown in Club settings and My clubs. See `TECHNICAL_DECISIONS.md` §57.
      - **Done (opponents):** "Add logo" / "Change logo" / "Remove" on Create and Edit opponent, saved with the form, for whoever can edit opponents. The same storage and rules as the club's. Shown on the Opponents list, Opponent details and Schedule match.
      - **Done (players and users):** a player's photo on Edit player, for whoever can edit the player, and the user's own photo on Account. Both are saved straight away and cropped to a square. They show on Player details, Account and the More panel.
      - **Shown** wherever they belong. See the next item.
    - [x] **Logos and photos everywhere they belong.** Wherever an image is set it's shown; where it isn't, the screen looks as before, apart from player rows. See `TECHNICAL_DECISIONS.md` §57.
      - **Opponent logo** before "vs" on every match card (Matches tab, Team and Age Group Details) and on Home's next-up card.
      - **Club and opponent logos** side by side at the top of Match details, and before each side's name on the live console's scoreboard.
      - **Grey screens, white sections** (the developer, 2026-10-25): every screen has a light grey background with its content in white sections outlined in grey, as on a finished match's General tab. Filters that pick one option are the same segmented control everywhere (the match type's: All · Official · Tournament · Friendly), and sorts the same chips with a "Sort" label (the call-up's), since 2026-10-26.
      - **Date and time pickers** (the developer, 2026-10-27): the calendar and the kick-off time clock are white like their sheet, with carbon text and the club's colour for the selected day or time, whether or not the phone is in dark mode (`TECHNICAL_DECISIONS.md` §73).
      - **Player rows** start with one badge: the photo with the shirt number under it, only the photo or only the number (centred), or a head-and-shoulders placeholder when there's neither. Used in call-ups and the pool (Match details), Age Group and Team members, Club players, Add players, the line-up picker and the live console's squad and minutes rows, the match report's ratings, and training ratings.
      - **People's photos** in Club settings → Members, Member details and age group staff.
      - **Club logo on push notifications** (Android), when the club has one and the server has a public address.
      - **Stats and pickers:** the team leaders card (photo, or the placeholder, before every name), the training stats table (the player badge) and Add staff's picker (the photo in the initials circle).
      - **Decided** (the developer, 2026-10-26): a user's photo is always shown, to any signed-in user, by its current random id. It isn't limited to people who share a club with them.
    - [x] **Tablets, foldables, desktop and web** (2026-10-27; see `TECHNICAL_DECISIONS.md` §11.5): every screen adapts to its width. From a medium-width window up (a phone on its side, a tablet, desktop, web), the tabs of the club screen and the detail screens are a rail on the left. Forms and lists are no wider than 720dp, centred, and card lists (My clubs, Age groups, Teams) become a grid. Still to do: list and details side by side (Matches, Players, Opponents).
    - [x] Client-side Architecture (MVVM, Koin, UseCases).
    - [x] API Integration (Ktor Client): every screen talks to the server through repositories over a shared authenticated Ktor client (bearer tokens, refresh on 401), plus WebSockets for the live match and the club's Matches tab.
    - [/] Role-specific Dashboards:
      - **Done:** "My clubs" lists the user's clubs (opening straight into the only one when there's just one). Each club opens on a Home tab with the season's headline numbers (teams, players, coaches, matches this week), what's next up, and alerts for the people who manage it (e.g. a match missing call-ups or a report).
      - **Still pending:** the server's super-admin, club-admin and parent dashboard endpoints (Phase 6) have no client screens yet.
    - [x] **Opponents** (design 38), with logos (see Logo and photo upload):
      - [x] **List:** a searchable list (name or short name, ignoring accents) used to pick an opponent on Schedule match and to browse them from More → Opponents. See `TECHNICAL_DECISIONS.md` §51.
      - [x] **Create opponent:** from the list, with stadium, contact and notes. A name the club already has is refused. Created while picking for a match, it comes back selected.
      - [x] **Opponent details:** stadium with directions, head-to-head by season (W/D/L, goals, last five) and every match against them, plus contact and notes.
      - [x] **Edit opponent:** from the details, for those allowed to edit opponents. Emptying a field clears it.
    - [x] Club "More" panel (design 37): a full-screen panel that slides in from the right, replacing the old "More" dropdown and the header's account icon.
      - **Account header:** avatar, name, email, role chip and club name; tapping it opens Account.
      - **Grouped rows by permission:** coordinators see every create action. A coach sees Create team and Create players for their own age groups, as far as the club's Permissions allow.
      - **Account rows:** Account settings, Switch club (2+ clubs) and Log out (with a confirmation).
      - **Players:** the club's player list opens as its own screen, with season, age-group and search filters.
    - [x] Match center (Live updates & History):
      - **Matches tab:** the club's Matches tab updates live. Below its filters (season, age group, team; All / Upcoming / Finished, All by default) it shows the same list as a team's and an age group's Games tab: Upcoming, then Recent results.
      - **Match Details:** line-ups, call-ups, events and score (once played: General, Stats, Events and Timeline tabs for the staff; General and the goals for everyone else), plus the details card (opponent, type, format such as 7x7 from the team's size, date, kickoff, location, notes).
      - **Live match console:** for coaches and staff.
      - **After the match:** finished results stay as history, with the post-match report and per-player stats. Season stats live on Player, Team and Age Group Details (see Phase 5).
- [x] **Phase 8: Training & Periodization** (see §5b). A session's exercises are reordered by dragging each by its handle (objectives and phases keep the order they were added). The sessions' List / Calendar choice is remembered on the device.
    - [x] Training permission: a configurable club setting for sessions and plans.
    - [x] **First:** Macrocycle (year plan), mesocycles (month plans) and microcycles (week plans) for an age group, with a Home alert when a period starts without its plan. Built before the sessions, because a coach sets the year's, month's and week's objectives before preparing a session.
    - [x] Club country (Portugal by default), for public holidays.
    - [x] Create an age group's sessions in bulk: days and hours plus a period, with or without public holidays, and a preview that warns about sessions it will override. One-off sessions too. List and calendar. A single session can be cancelled.
    - [x] Exercise library, reusable across sessions.
    - [x] Player evaluations per session, 0–10 with decimals (0 = did not take part), by any staff member of the age group. Staff-only.
    - [x] Home to-do when an ended session has players still to evaluate.
    - [x] Training stats (attendance, average rating) on Age Group Details' Stats tab and on Player Details, staff-only.
- [ ] **Phase 9: Squadify for Coaches** (see §5c and `TECHNICAL_DECISIONS.md` §72):
    - **Launch order** (the developer, 2026-10-29): Squadify for Coaches launches first, before the clubs app; selling to a coach is easier than to a club. So the release work below is for the coach app's launch, and the clubs app reuses it.
    - [/] Shared server module (`:server:core`) with the game use cases, and the permission check behind an `AccessPolicy`. The clubs server moves onto it with no change in behaviour.
        - [x] The game use cases ask an `AccessPolicy`; the clubs server's (`ClubAccessPolicy`) makes the same checks as before (`TECHNICAL_DECISIONS.md` §72).
        - [x] The `:server:core` module, with the game use cases, the repository and service interfaces, the stats and the exceptions moved into it (`TECHNICAL_DECISIONS.md` §72).
        - [x] The shared tables and every Exposed repository in `:server:data`, with a schema test per server (`TECHNICAL_DECISIONS.md` §72). The coach database reuses the clubs' tables: a space is a club, its members club members.
        - [x] The login code (tokens, sign-up, sign-in, refresh) and the Ktor setup, shared (`:server:core`, `:server:web`).
    - [ ] The app split: shared screens and view models, plus a clubs module and a coach module (Koin setup, repositories, navigation, name, icon). A second Android app and a second iOS target.
    - [/] The coach server: its own database, migrations, users, spaces and members, deployed as its own Railway service and database, on staging and production. A change on `develop` that affects both servers (shared code or shared tables) deploys both to staging.
        - [x] `server/coach`: its own database and migrations (the shared tables), accounts (sign-up, sign-in, refresh), schema test, Dockerfile (`TECHNICAL_DECISIONS.md` §72).
        - [ ] Railway: the coach service and database on staging, then production.
        - [ ] Spaces and members: first setup, `CoachAccessPolicy`, the structure and game routes.
    - [ ] The coach app: sign-up, first setup (sport, age group, team), invites.
    - [ ] Plans stored and enforced on the server, set by a platform admin at first.
    - [ ] Payments (RevenueCat) and ads on the Free plan (AdMob).
    - [ ] **Launch readiness** (Android and iOS, needed before the coach app goes to the stores):
        - [ ] R8 full mode (`isMinifyEnabled = true` with the right keep rules) and a Baseline Profile for the release build (`TECHNICAL_DECISIONS.md` §11.7, §11.8).
        - [ ] Edge-to-edge: audit every screen's `WindowInsets` (§11.6, §11.8).
        - [ ] iOS: registered with Firebase (Crashlytics, push), and pushes received on iOS (`TECHNICAL_DECISIONS.md` §12).
        - [ ] Store requirements (§5c): account deletion in the app, Google's UMP consent form before ads, a privacy policy and data-safety form.
        - [ ] Production: the coach server, its database and the push credentials on Railway production.
    - [ ] Later: moving an age group from the coach app to a club.
