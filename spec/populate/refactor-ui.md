### Populate arguments UI refactor

#### Options considered

1) Single popup with toggles
    - Two primary choices: "All arguments" and "Required arguments only".
    - Inline toggles: "Recursive" and "From locals".
    - Defaults based on context (recursive on when applicable).
    - Persist last-used toggles per session/project.

2) Two-step flow (simple then advanced)
    - First popup: "All" vs "Required".
    - Secondary sheet for toggles (Recursive / From locals) via "More..." or modifier key.

3) Presets + customize
    - Presets: "All", "Required", "From locals".
    - "Customize..." opens toggles for Recursive / From locals.

4) Contextual auto-choice + fallback
    - Only show "All" and "Required".
    - Auto-enable recursive when nested dataclasses exist.
    - Use locals automatically and show a short notice.

#### Selected direction

Option 1: single popup with two modes and inline toggles for recursive and locals.

#### Implementation notes

- Keep the primary list short (two items).
- Show toggles in the same popup so it is still one step.
- Default recursive to on when nested dataclasses are present.
- Locals toggle defaults to off.
