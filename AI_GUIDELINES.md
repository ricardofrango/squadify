# AI Interaction Guidelines - Club Manager

This document contains behavioral rules and constraints for the AI assistant. These are not technical or business decisions, but instructions on how to interact with the developer and the codebase.

## 1. Scope & Autonomy
- **Strict Adherence**: Do not perform any refactoring, file creation, or code changes that were not explicitly requested by the developer.
- **Incremental Progress**: Focus only on the specific task described in the prompt. If a secondary improvement is identified, suggest it in the chat instead of applying it automatically.
- **Minimal Changes**: When applying a fix or a feature, keep the changes to the minimum necessary to satisfy the requirement, respecting the existing code style.
- **Modern Code**: Avoid using deprecated APIs, methods, or features. Always check for the most current and recommended way to implement a functionality.
- **Resource Management**: Never hardcode UI text (copys) in screens or components. Always use resource files (e.g., `strings.xml`) to support localization and maintenance.
- **UI-Driven Lifecycle**: For critical one-shot events (like Splash navigation), do not trigger actions in the `ViewModel`'s `init` block if they emit to a Flow/Channel. Instead, trigger the action from the UI's `LaunchedEffect` after the subscription is established to ensure events are never missed.
- **Modern Navigation**: Prefer bottom navigation (`NavigationBar`) for main screen areas (like Teams/Players) to provide a modern and accessible mobile experience.
- **Filtering Patterns**: Implement filters using `FilterChip` in a horizontal scrollable row (`LazyRow`) at the top of the content area.
- **Database Efficiency**: Always avoid O(N) complexity when accessing the database. Prefer batch operations (e.g., `inList`, `batchInsert`) over multiple individual queries inside loops to prevent the N+1 problem.
- **Scalability (Pagination)**: When creating list-based endpoints (GET), always consider or implement pagination (limit/offset) to ensure the system remains performant as data grows.
- **Documentation Maintenance**: Always review and update relevant documentation files (`REQUIREMENTS.md`, `TECHNICAL_DECISIONS.md`, etc.) when implementing changes that affect business logic, architecture, or system flows.

## 2. Communication
- **Conciseness**: Keep explanations brief and technical.
- **Verification**: Always confirm when a task is completed and wait for the next instruction before proceeding to a new phase of the roadmap.
