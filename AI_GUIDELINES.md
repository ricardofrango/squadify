# AI Interaction Guidelines - Club Manager

This document contains behavioral rules and constraints for the AI assistant. These are not technical or business decisions, but instructions on how to interact with the developer and the codebase.

## 1. Scope & Autonomy
- **Strict Adherence**: Do not perform any refactoring, file creation, or code changes that were not explicitly requested by the developer.
- **Incremental Progress**: Focus only on the specific task described in the prompt. If a secondary improvement is identified, suggest it in the chat instead of applying it automatically.
- **Minimal Changes**: When applying a fix or a feature, keep the changes to the minimum necessary to satisfy the requirement, respecting the existing code style.
- **Modern Code**: Avoid using deprecated APIs, methods, or features. Always check for the most current and recommended way to implement a functionality.
- **Resource Management**: Never hardcode UI text (copys) in screens or components. Always use resource files (e.g., `strings.xml`) to support localization and maintenance.
- **No Raw Exception Messages in UI**: Never surface a caught exception's `.message`, a stack trace, or any other raw exception/technical detail directly as end-user-facing UI text. This is a security issue (information disclosure) as well as a UX one -- exception text can leak internal class names, SQL, file paths, or business logic. Always map errors to a static, localized, user-facing copy from resource files instead.
- **UI-Driven Lifecycle**: For critical one-shot events (like Splash navigation), do not trigger actions in the `ViewModel`'s `init` block if they emit to a Flow/Channel. Instead, trigger the action from the UI's `LaunchedEffect` after the subscription is established to ensure events are never missed.
- **Modern Navigation**: Prefer bottom navigation (`NavigationBar`) for main screen areas (like Teams/Players) to provide a modern and accessible mobile experience.
- **Filtering Patterns**: Implement filters using `FilterChip` in a horizontal scrollable row (`LazyRow`) at the top of the content area.
- **Compose Previews & Orientation Support**: Every Composable screen must ship with at least one `@Preview` composable (or multiple, e.g. light/dark) so it can be reviewed without running the app, and must support both portrait and landscape orientations (no fixed/locked orientation, no layout that only works in one). Verify this before considering a new or modified screen done.
- **Database Efficiency**: Always avoid O(N) complexity when accessing the database. Prefer batch operations (e.g., `inList`, `batchInsert`) over multiple individual queries inside loops to prevent the N+1 problem.
- **Scalability (Pagination)**: When creating list-based endpoints (GET), always consider or implement pagination (limit/offset) to ensure the system remains performant as data grows.
- **Documentation Maintenance**: Always review and update relevant documentation files (`REQUIREMENTS.md`, `TECHNICAL_DECISIONS.md`, etc.) when implementing changes that affect business logic, architecture, or system flows.
- **Database Schema Sync**: Whenever a change adds, removes, or alters a table, column, index, constraint, or enum in `Tables.kt` (or otherwise changes the database structure), always update the "Database Schema: Current State of the Art" section in `PROCESS_FLOWS.md` (ER diagram, constraints, enum reference) in the same change, so it never drifts from the real schema.
- **Test Coverage for Business Rules**: Whenever a change introduces or modifies a business rule (e.g. validation, eligibility, permission, or state-transition logic inside a Use Case), always add or update the corresponding unit test(s) in the same change. This mirrors the existing pattern of `CheckUserPermissionUseCaseTest` and must be kept up regardless of whether tests were explicitly requested.

## 2. Communication
- **Conciseness**: Keep explanations brief and technical.
- **Verification**: Always confirm when a task is completed and wait for the next instruction before proceeding to a new phase of the roadmap.
