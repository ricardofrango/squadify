# Permission Test Matrix

> **Model change (players & coaches → age groups):** coaches/staff now attach to **age groups**
> (`age_group_staff`), not teams, and players belong to **age-group pools** (`age_group_players`).
> `CheckUserPermissionUseCase` resolves any `teamId` a caller passes to its age group before checking.
> Read every "Team X" role / link in the tables below as "the **age group** of Team X". `TeamRole` is
> now `COACH`/`STAFF` only (`PARENT`/`PLAYER` removed). The `*UseCaseTest` files have been rewritten to
> the age-group model; this matrix's prose is pending a full rewrite. See `PROCESS_FLOWS.md` §1.1.

A concrete set of test personas and scenarios covering every user type described in the domain model — admin, coordinator, member (with and without team assignments, single- and multi-club), player, and parent — so the whole authorization surface can be validated in one pass. Each scenario lists what the code actually does **today** versus what it **should** do per the requirements discussed (see `issues to fix.md` for the underlying bugs); use this to drive new/rewritten unit tests.

## Before you write these as tests: the existing permission test file doesn't compile

`server/src/test/kotlin/.../domain/usecase/CheckUserPermissionUseCaseTest.kt` tests an obsolete shape of the permission model: a single unified `Role` enum, a `TeamRepository.isUserStaffOfTeam(...): Boolean`, and a `CheckUserPermissionUseCase` that takes two constructor args and *returns* the resolved role. None of that exists anymore — the model has since split into `ClubRole`/`TeamRole`, `TeamRepository.getUserRoleInTeam(...): TeamRole?`, a three-arg constructor (`userRepository, teamRepository, transactionManager`), and a `Unit`-returning use case. This file will not compile against the current `core`/`domain` code. Any new test work needs to replace it, not extend it — worth adding to `issues to fix.md` as its own item.

## 1. Test personas

| ID | Persona | Club role(s) | Team role(s) | Player/parent link | Purpose |
|---|---|---|---|---|---|
| P1 | **Super Admin** | — | — | — | `isPlatformAdmin = true`; should bypass every check ("God Mode") |
| P2 | **Club A – Admin** | Club A: `CLUB_ADMIN` | — | — | Full access baseline |
| P3 | **Club A – Coordinator** | Club A: `COORDINATOR` | — | — | Should be equivalent to P2 until billing exists |
| P4 | **Club A – Plain Member** | Club A: `MEMBER` | none | — | Someone who works at the club but isn't assigned to any team yet — should see only the club |
| P5 | **Club A – Coach of Team 1** | Club A: `MEMBER` | Team 1: `COACH` | — | The common case: a member with one team assignment |
| P6 | **Club A – Staff of Team 2** | Club A: `MEMBER` | Team 2: `STAFF` | — | Same as P5 but `STAFF`, different team |
| P7 | **Club A – Multi-role Member** | Club A: `MEMBER` | Team 1: `COACH`, Team 2: `STAFF` | — | "Coach of Team A and staff of Team B" from the spec |
| P8 | **Cross-club Coach** | Club A: `MEMBER`, Club B: `MEMBER` | Club A/Team 1: `COACH`, Club B/Team 5: `COACH` | — | "Coach in two different teams in two different clubs" from the spec |
| P9 | **Player with own login** | *(current code: Club A `MEMBER`, granted incidentally; target: none)* | none | `Player` row, on `TeamPlayers` for Team 1 (Club A) | Exposes the current-vs-target divergence for a player who self-registered |
| P10 | **Parent of one child** | none | none | `UserPlayers` → a player on Team 1 (Club A) | The baseline family-member persona |
| P11 | **Parent of two children, different clubs** | none | none | `UserPlayers` → Player X (Club A/Team 1), `UserPlayers` → Player Y (Club B/Team 5) | The exact "father with two sons in two different clubs" scenario from the spec |
| P12 | **Outsider** | none | none | none | Registered user with zero relationship to Club A or B — negative control |
| P13 | **Suspended member** | Club A: `MEMBER`, `user.status = SUSPENDED` | none | — | Tests whether account status is enforced |

## 2. Scenario matrix

Legend: ✅ current behavior matches target · ❌ gap (current ≠ target) · — not applicable.

### Platform-level

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P1 | View Super Admin dashboard | Allow | Allow | ✅ |
| P2 | View Super Admin dashboard | Deny | Deny | ✅ |
| P12 | View Super Admin dashboard | Deny | Deny | ✅ |
| P1 | Provision a new club | Allow | Allow | ✅ |
| P2 | Provision a new club | Deny | Deny | ✅ |

### Club profile

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P2 | View Club A details | Allow | Allow | ✅ |
| P3 | View Club A details | Allow | Allow | ✅ |
| P4 | View Club A details | Allow | Allow | ✅ (this *is* "sees only the club") |
| P5 | View Club A details | Allow | Allow | ✅ |
| P9 | View Club A details | Allow (via incidental `MEMBER` grant) | Allow | ⚠️ right outcome, wrong mechanism |
| P10 | View Club A details | Allow | Allow | ✅ **fixed** -- `GetClubDetailsUseCase` now passes `allowPlayerOrParentAccess = true` |
| P13 | View Club A details | Deny | Deny | ✅ **fixed** -- `CheckUserPermissionUseCase` now checks `user.status` |
| P12 | View Club A details | Deny | Deny | ✅ |
| P3 | Update Club A profile | Allow | Allow | ✅ **fixed** -- `UpdateClubUseCase` now requires `COORDINATOR` |
| P2 | Update Club A profile | Allow | Allow | ✅ |
| P5 | Update Club A profile | Deny | Deny | ✅ |
| P3 | Change a Club A member's role | Allow | Allow | ✅ **fixed** -- `UpdateClubMemberRoleUseCase` now requires `COORDINATOR` |
| P2 | Change a Club A member's role | Allow | Allow | ✅ |

### Teams & roster

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P2 | List Club A's teams | Allow, all teams | Allow, all teams | ✅ |
| P4 | List Club A's teams | Empty list | Empty list | ✅ **fixed** -- `GetTeamsUseCase` now filters by the caller's `TeamRole` |
| P5 | List Club A's teams | Allow, Team 1 only | Allow, Team 1 only | ✅ **fixed** -- see above |
| P12 | List Club A's teams | Deny | Deny | ✅ |
| P2 | View Team 1 details/roster | Allow | Allow | ✅ |
| P5 | View Team 1 details/roster (their own team) | Allow | Allow | ✅ **fixed** -- `GetTeamDetailsUseCase`/`GetTeamPlayersUseCase`/`GetTeamStaffUseCase` now pass `minTeamRole = TeamRole.STAFF` |
| P6 | View Team 2 details/roster (their own team) | Allow | Allow | ✅ **fixed** -- see above |
| P6 | View Team 1 details/roster (not their team) | Deny | Deny | ✅ |
| P10 | View Team 1 details (their child's team) | Allow | Allow | ✅ **fixed** -- `GetTeamDetailsUseCase`/`GetTeamPlayersUseCase`/`GetTeamStaffUseCase` now pass `allowPlayerOrParentAccess = true` |
| P8 | View Club B/Team 5 details (their other coaching job) | Allow | Allow | ✅ **fixed** -- see above |
| P8 | View Club A/Team 3 details (a team they have no role on) | Deny | Deny | ✅ |
| P12 | View Team 1 details | Deny | Deny | ✅ |
| P10 | List Club A's teams (their child's team only) | Empty list | Team 1 only | ✅ **fixed** -- `GetTeamsUseCase` now also filters in teams reached via `TeamPlayers`/`UserPlayers` |
| P2 | List Club A's players (no team filter) | Allow, every player | Allow, every player | ✅ |
| P4 | List Club A's players (no team filter) | **Allow, every player** | Empty list | ✅ **fixed** -- `GetPlayersUseCase` now scopes to the caller's own team(s), same as `GetTeamsUseCase` |
| P5 | List Club A's players (no team filter) | **Allow, every player** | Allow, Team 1's roster only | ✅ **fixed** -- see above |
| P10 | List Club A's players (no team filter) | Deny | Allow, their child's team roster only | ✅ **fixed** -- `GetPlayersUseCase` now also passes `allowPlayerOrParentAccess = true` |
| P5 | List Team 1's players (with a team filter) | Deny (missing `minTeamRole`) | Allow | ✅ **fixed** -- `GetPlayersUseCase` never set `minTeamRole` on this path either; now passes `minTeamRole = TeamRole.STAFF` alongside `allowPlayerOrParentAccess = true` |

### Age groups & seasons

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P3 | Create a season / age group for Club A | Allow | Allow | ✅ |
| P4 | Create a season / age group for Club A | Deny | Deny | ✅ |
| P4 | List Club A's seasons/age groups | Empty list | Empty list | ✅ **fixed** -- `GetSeasonsUseCase`/`GetAgeGroupsUseCase` now scope to the season(s)/age group(s) the caller's own team(s) actually belong to, instead of every club member seeing all of them. (Originally scoped as a hard Deny in this matrix; revised to a filtered/empty list for consistency with `GetTeamsUseCase`'s P4 target and the developer's explicit instruction that this filtering approach apply to every persona, including players/parents.) |
| P7 | List Club A's seasons/age groups (two teams, two age groups, one season) | N/A (not previously modeled) | One season, two age groups | ✅ **fixed** -- see above; a caller's own teams are deduped per season/age group, not just unioned as raw team rows |
| P10 | List Club A's seasons/age groups (their child's team only) | N/A (not previously modeled) | Their child's team's season and age group only | ✅ **fixed** -- see above |
| P2 | Create a Team under Age Group X / Season Y where X doesn't actually belong to Y | Deny / validation error | Deny / validation error | ✅ **fixed** -- `CreateTeamUseCase` now validates the age group belongs to the club and season |

### Opponents

Unlike teams/players/matches/seasons/age groups, `GetOpponentsUseCase` is intentionally **not**
scoped by team assignment (confirmed with the developer): an opponent isn't tied to any specific team
in the data model, and the list is low-sensitivity reference data (just the names of other teams/clubs
this club plays against). Any club member -- with or without a team assignment -- can list them.

**2026-09-24**: also gained an *optional* `ageGroupId` parameter (see the `CreatePlayersUseCase`/
`CreateOpponentUseCase` row above for the full rationale, incl. why this stayed per-age-group rather
than a club-wide "coach of anything" check on a first attempt). When the caller supplies it -- the
create-match screen always knows which age group it's scoped to -- `minTeamRole = TeamRole.COACH,
ageGroupId = ageGroupId` is passed to `CheckUserPermissionUseCase`, so a coach of *that specific* age
group can see the list even with zero `club_members` role. Reported bug: P5 (a coach whose *only* row
anywhere is `age_group_staff`, with no `club_members`/`ClubRole` row at all) got an empty opponent list
from this use case -- `minClubRole = MEMBER` alone denies someone with no club role whatsoever, which
then silently pushed them toward the "+ Add new opponent" shortcut on the create-match screen (see
above) instead of picking an existing one.

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P2 | List Club A's opponents | Allow | Allow | ✅ |
| P4 | List Club A's opponents | Allow | Allow | ✅ (intentional -- see above, not scoped like Teams/Players/Matches/Seasons/AgeGroups) |
| P5 | List Club A's opponents, with the ageGroupId of the age group they coach | Deny (empty list) | Allow | ✅ **fixed 2026-09-24** -- see note above |
| P5 | List Club A's opponents, with a *different* age group's id (not theirs) or no ageGroupId at all | Deny | Deny | ✅ (correctly still denied -- coaching age group X doesn't grant blanket club access) |
| P12 | List Club A's opponents | Deny | Deny | ✅ |

### Matches

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P5 | Create a match for Team 1 | Allow | Allow | ✅ |
| P6 | Create a match for Team 1 (not their team) | Deny | Deny | ✅ |
| P2 | Create a match for any team in Club A | Allow | Allow | ✅ |
| P4 | Create a match | Deny | Deny | ✅ |
| P5 | List Team 1's matches (with a team filter) | Allow | Allow | ✅ **fixed** -- see above |
| P5 | List Club A's matches (no team filter) | Allow, Team 1's only | Allow, Team 1's only | ✅ **fixed** -- `GetMatchesUseCase` now scopes to the caller's own team(s) instead of every match in the club |
| P10 | View Team 1's match calendar | Allow | Allow | ✅ **fixed** -- `GetMatchesUseCase` now passes `allowPlayerOrParentAccess = true` |
| P10 | View a specific match's details | Allow | Allow | ✅ **fixed** -- `GetMatchDetailsUseCase` now passes `minTeamRole = TeamRole.STAFF`, `teamId = match.teamId`, `allowPlayerOrParentAccess = true` |
| P5 | Log a match event for Team 1 | Allow | Allow | ✅ (this one correctly sets `minTeamRole = COACH` -- deliberately not lowered) |
| P5 | Manage call-ups for Team 1 | Allow | Allow | ✅ |
| P6 | Manage call-ups for Team 2 (their own team) | Allow | Allow | ✅ **2026-09-18**: `ManageCallUpsUseCase` lowered to `minTeamRole = STAFF` -- staff can now manage call-ups for their own age group, not just coaches |
| P6 | Start a match / update match status for Team 2 (their own team) | Allow | Allow | ✅ **2026-09-18**: same change applied to `StartMatchUseCase` and `UpdateMatchStatusUseCase` |
| P6 | Manage call-ups for Team 1 (not their team) | Deny | Deny | ✅ |

### Dashboards

| Persona | Action | Current | Target | |
|---|---|---|---|---|
| P11 | View clubs list | Allow, both clubs returned | Allow, both clubs | ✅ (already works — `GetClubsUseCase` checks `UserPlayers` too) |
| P11 | View Parent dashboard | Allow (no permission check at all, by design — self-scoped) | Allow | ✅ |
| P2 | View Club Admin dashboard for Club A | Allow | Allow | ✅ |
| P3 | View Club Admin dashboard for Club A | Allow | Allow | ✅ |
| P4 | View Club Admin dashboard for Club A | Deny | Deny | ✅ |

## 2b. Write permissions: audited against the existing use cases

Separate from the read-scoping rules above, the developer asked whether writes need a distinct
permission-checking mechanism. They don't -- `CheckUserPermissionUseCase` is action-agnostic, and the
convention already used by most mutation use cases already implements the intended rule: omit
`minClubRole` (or use `null`) so `CLUB_ADMIN`/`COORDINATOR` auto-pass club-wide, and set
`minTeamRole = TeamRole.COACH` with the specific `teamId` so only that team's own coach passes
otherwise. Audited on 2026-09-03:

| Use case | Pattern today | Status |
|---|---|---|
| `CreateMatchUseCase` | no `minClubRole`, `minTeamRole = COACH` + `teamId` | ✅ correct |
| `AddPlayersToTeamUseCase` | no `minClubRole`, `minTeamRole = COACH` + `teamId` | ✅ correct |
| `ManageCallUpsUseCase` | no `minClubRole`, `minTeamRole = STAFF` + `teamId` | ✅ correct -- lowered from `COACH` to `STAFF` per the developer, 2026-09-18 (match-day operations open to staff, not just coaches) |
| `UpdateMatchStatusUseCase` | no `minClubRole`, `minTeamRole = STAFF` + `teamId` | ✅ correct -- lowered from `COACH` to `STAFF`, same 2026-09-18 change |
| `StartMatchUseCase` | no `minClubRole`, `minTeamRole = STAFF` + `teamId` | ✅ correct -- lowered from `COACH` to `STAFF`, same 2026-09-18 change |
| `AddMatchEventUseCase` | no `minClubRole`, `minTeamRole = COACH` + `teamId` | ✅ correct -- deliberately NOT lowered; logging/deleting match events stays coach-only |
| `DeleteMatchUseCase` | no `minClubRole`, `minTeamRole = COACH` + `teamId` | ✅ correct -- new, 2026-09-19; deliberately coach-only, same gate as `UpdateMatchUseCase`/`CreateMatchUseCase`, not grouped with the 2026-09-18 staff-level match-day operations above |
| `UpdateTeamUseCase` | was `minClubRole = COORDINATOR` only, no team-role path at all -- a coach could never edit their own team's name | ❌ was a bug; **fixed** -- added `minTeamRole = TeamRole.COACH` alongside the club-role check, see `UpdateTeamUseCaseTest` |
| `AddStaffToTeamsUseCase` | `minClubRole = COORDINATOR` only, no team-role path, no `teamId` | Intentional (decided with the developer): assigning staff grants someone else write access to the team, so it stays admin/coordinator-only |
| `CreatePlayersUseCase`, `CreateOpponentUseCase` | was `minClubRole = COORDINATOR` only, club-wide, no team/age-group scoping at all | ❌ was a bug; **fixed 2026-09-24** -- both requests gained an *optional* `ageGroupId` field, threaded from whichever age-group/match screen the "+ Add new player"/"+ Add new opponent" shortcut was launched from (the standalone club-roster entry points -- Club Details' "Add players to club" -- have no age group in scope and keep passing `null`, staying coordinator-only). When set, `checkUserPermissionUseCase(..., minTeamRole = TeamRole.COACH, ageGroupId = request.ageGroupId, ...)` uses the *existing* per-age-group staff check (the same mechanism `AddPlayersToAgeGroupUseCase` already used), so only a coach of *that specific* age group passes -- not a coach of some unrelated age group elsewhere in the club. Both use cases first fetch the age group and reject (`ForbiddenException`) if its `clubId` doesn't match the request's `clubId`, closing a cross-club spoofing gap a looser design would have had. **First attempt (reverted, see git history) added a club-wide "is this user a coach of ANY age group in the club" fallback (`allowAnyAgeGroupTeamRole` / `getBestTeamRoleInClub`) instead -- rejected as too broad: it would let a coach of age group X act on an unrelated age group Y just by being *a* coach somewhere in the club.** Reported bug: a coach with only an `age_group_staff` row (no `club_members` row at all) got a `ForbiddenException` when the client's "+ Add new player"/"+ Add new opponent" shortcuts silently called these use cases -- see the corresponding rows in "Age groups & seasons" and "Opponents" below. |
| `UpdatePlayerUseCase`, `CreateAgeGroupsUseCase`, `UpdateAgeGroupUseCase`, `CreateSeasonUseCase`, `UpdateSeasonUseCase`, `UpdateOpponentUseCase`, `AddClubMembersUseCase` | `minClubRole = COORDINATOR` only, club-wide, no team scoping | Intentional -- these are club-structural/administrative actions, not team-scoped |

Players and family members get zero write access everywhere today, by construction: there is no branch
in `CheckUserPermissionUseCase` that ever grants access via `TeamPlayers`/`UserPlayers`. When their
read-only path is eventually built (see "issues to fix.md"), the agreed design is to keep a single
`CheckUserPermissionUseCase` rather than split into separate read/write functions, and rely on
discipline + tests to guarantee no write use case ever passes a player/parent identity through it. See
the design-invariant comment on `CheckUserPermissionUseCase.kt`.

## 3. How to use this

Each row above is meant to become one test case (unit tests mocking `UserRepository`/`TeamRepository` for `CheckUserPermissionUseCase` itself, or higher-level tests per use case for the ones with extra logic like `CreateTeamUseCase`'s missing cross-check). The ❌ rows are the ones that should currently **fail** if written against the target behavior — that's expected until the corresponding fix in `issues to fix.md` lands; write them as failing/`@Ignore`d tests first if you want a red-to-green trail, or as passing tests against *current* behavior if you'd rather lock in today's contract while the redesign is planned.

Per `AI_GUIDELINES.md`, once the access-control fixes are made, the corresponding rows here should flip from ❌ to ✅ in the same change, and this file should stay in sync with `PROCESS_FLOWS.md` §1.1 (the `CheckUserPermissionUseCase` flowchart) and `issues to fix.md`.
