# Club Manager — Project Instructions

Before making any change in this repository, read the following documents in full. They are the source of truth for how this project works and how to work on it — do not duplicate or restate their content here, just read them:

- **`AI_GUIDELINES.md`** — behavioral rules for how the AI assistant should interact with the developer and the codebase (scope/autonomy, coding conventions, when to add tests, when to update documentation). Follow these on every task, not just when reminded.
- **`REQUIREMENTS.md`** — the product requirements and roadmap: user roles, domain hierarchy, core entities, and phase-by-phase status.
- **`TECHNICAL_DECISIONS.md`** — the architectural patterns, libraries, and design principles adopted (Clean Architecture, Koin, Exposed, security model, real-time flow, etc.) and why.
- **`PROCESS_FLOWS.md`** — flowcharts for every use case under `server/.../domain/usecase`, plus the current database schema (ER diagram, constraints, enum reference). Treat this as the living, as-implemented state of the system.

If a change affects business logic, architecture, or the database structure, update the relevant document(s) above in the same change, per the rules in `AI_GUIDELINES.md`.
