# Decisions

Track key project decisions with context and rationale.

## 2026-03-28

- Create a fresh project `predictive-typing-quality-trust` instead of editing `typing-speed-core` in place because the existing scope already contains execution history, validation evidence, and partially completed tasks.
- Keep this project focused on predictive-typing planning, evaluation, and rollout governance rather than direct implementation work.
- Treat the inaccessible ChatGPT research page as a citation gap, not a blocker to setting up the planning structure and Linear objects.
- Use Linear raw payloads when the generated CLI wrapper omits supported fields such as project team or issue project assignment.
- Complete milestone and issue creation through the Linear GraphQL API because the generated CLI advertised `create-milestone` and `create-issue` commands that returned `Tool create_milestone not found` and `Tool create_issue not found` in this environment.
- Record the known path-leakage validation failure in `typing-speed-core` separately so it is not misattributed to this project.
- Add primary-source citations directly into the planning docs where they sharpen concrete architecture choices: use the Gboard decoder and neural-search-space papers to justify the hybrid stack assumption, and use the Google / SwiftKey / Apple privacy papers to justify keeping personalization and privacy constraints coupled from the start.
