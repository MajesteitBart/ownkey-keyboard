---
id: WS-A
name: Runtime foundation
owner: ownkey-keyboard-team
status: done
created: 2026-09-15T18:36:00Z
updated: 2026-09-15T19:29:52Z
---

# Workstream: Runtime foundation

## Objective

Native adapter, catalog, model storage, downloads and inference process.

## Owned Files/Areas

lib/offline-asr/, app offline model/runtime services.

## Dependencies

See task `depends_on` fields; execute sequentially within shared files.

## Risks

Physical device evidence is unavailable; preserve fail-closed routing and release gates.

## Handoff Criteria

Mapped implementation and tests pass; remaining physical checks are explicitly recorded.
