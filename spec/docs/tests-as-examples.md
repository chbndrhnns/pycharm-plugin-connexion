### What you already have (use it as a foundation)
- The repo already follows the IntelliJ “Intention description” convention under `src/main/resources/intentionDescriptions/<IntentionName>/description.html` (and typically `before.*` / `after.*` example files). That is *already* user-facing in the IDE, and it’s a great place to plug in automated examples.
- Tests already contain high-quality “before/after” fixtures as Python code strings (e.g. `myFixture.configureByText("a.py", before)` + `myFixture.checkResult(expected)` in `PyIntroduceParameterObjectCasesTest.kt`). Those strings are the closest thing you have to a “single source of truth” for examples.
- You already have a UI-test run target (`runIdeForUiTests`) with the Robot server plugin enabled and `-Drobot-server.port=8082` configured in `build.gradle.kts`. That’s a ready-made entry point for automated recordings.

### A practical automation design (keep one source of truth, generate everything else)
The easiest way to keep docs from drifting is to make **tests the canonical source** for examples/edge cases, and generate documentation artifacts from them.

#### 1) Define a small “doc spec” convention in tests
Pick one of these approaches (ordered from most robust to quickest):

**Option A — a small DSL used by tests (most robust):**
Create a helper used in tests to *declare* a doc example, separately from the assertions.
- Example shape (conceptually):
  - `docCase(featureId = "introduce-parameter-object", title = "Async function") { before("…<caret>…") ; after("…") ; preconditions("…") ; unsupported("…") ; action("Introduce parameter object") }`
- Tests continue to assert behavior as they do today; the docCase is just extra structured metadata.
- Upside: reliable extraction (no heuristics), and you can explicitly add “supported/unsupported” notes that aren’t always inferable from code.

**Option B — structured comments near fixtures (quickest):**
Add comment markers around the existing `before`/`expected` strings.
- Example idea: `// DOC(feature=introduce-parameter-object, case=async, kind=before)`
- Upside: almost no refactoring.
- Downside: extraction is more brittle.

**Option C — move examples into `src/test/testData/...` files and reference them (clean separation):**
Instead of embedding large strings, reference external files:
- `myFixture.configureByFile("feature/introduceParameterObject/async_before.py")`
- `myFixture.checkResultByFile("feature/introduceParameterObject/async_after.py")`
- Upside: your “doc assets” are already files that can be included verbatim in docs.
- Downside: requires migrating tests.

In practice: A + C together works best long-term (structured metadata + file-based examples).

#### 2) Generate documentation pages (Markdown/HTML) from those specs
Create a dedicated Gradle task (e.g. `generateFeatureDocs`) that:
- scans `src/test/kotlin/**` (and/or `src/test/testData/**`) for doc specs
- emits generated docs into something like `docs/generated/**` (or directly into `src/main/resources/intentionDescriptions/**` if you want to keep IDE help aligned)
- produces per-feature pages containing:
  - Preconditions
  - Supported / unsupported cases (ideally grouped and linked to test names)
  - “How to use” steps (intention name/action ID, shortcuts, where it appears)
  - Before/after code blocks

**Key point for automation:**
- Make the Gradle task declare proper `inputs` and `outputs` so it is incremental and runs automatically when tests change.
- In CI, run `generateFeatureDocs` and then fail if it changes tracked files (classic “generated files are out of date” check).

#### 3) Keep IDE intention descriptions in sync (high-value, low noise)
For each intention feature, generate (or partially generate):
- `src/main/resources/intentionDescriptions/<IntentionName>/before.py`
- `.../after.py`
- optionally, include a small generated section in `description.html` (or keep HTML hand-written and only generate the examples).

This gives you:
- immediate user-facing docs inside the IDE
- one consistent example set reused on the website/README

A good compromise is:
- `description.html` stays curated (human text)
- `before.*` / `after.*` examples are generated from tests (machine)

### Automated GIF recordings (reproducible and tied to examples)
You already have the right infrastructure hook: `runIdeForUiTests` with Robot.

A workable pipeline is:
1. For each doc example, define an accompanying “UI scenario” (open file → place caret → invoke action → accept dialogs → verify result).
2. Run those scenarios under `./gradlew runIdeForUiTests` (or a dedicated UI-test Gradle task).
3. Capture frames:
   - Use Robot to take screenshots at key steps (before invoke, after apply, etc.)
   - Or capture periodic screenshots during the flow.
4. Post-process frames into a GIF using an external tool (`ffmpeg`/`gifski`) invoked by a non-interactive Gradle task.
5. Store output under `docs/generated/gifs/<feature>/<case>.gif`.

Automation details that matter in practice:
- Make recordings deterministic:
  - fix IDE window size
  - disable animations if possible
  - use a consistent theme
  - stabilize caret placement and selection
- Keep GIF length short (2–6 seconds) and focused on one action.

### “Changes to source code should trigger regeneration” (what to wire into `check`)
A simple, effective setup:
- `generateFeatureDocs` task depends on nothing (it parses sources), but declares inputs:
  - `src/test/kotlin/**`
  - `src/test/testData/**` (if used)
  - any doc templates you maintain (e.g. `docs/templates/**`)
- `check` depends on `generateFeatureDocs`.
- Add a “verify generated docs are committed” check:
  - run `generateFeatureDocs`
  - then fail CI if there is a git diff in generated docs paths

This gives you: “any PR that changes behavior/examples must also update docs”, automatically.

### Suggested next steps (minimal-to-maximum impact)
1. **Pilot one feature** (e.g. “Introduce parameter object”) and implement Option B or C to prove the extraction path quickly.
2. Add `generateFeatureDocs` as a Gradle task and wire it into `check`.
3. Add a small set of Robot-based UI scenarios for 1–3 examples and produce GIFs.
4. Once the pipeline works, migrate more features and gradually move from Option B → Option A/C for stronger structure.

### Questions that would let you pick the best variant
- Do you want the *IDE-built-in* docs (`intentionDescriptions`) to be the primary user-facing docs, or do you also want a separate website/docs site (MkDocs/Docusaurus/GitHub Pages)?
- Are you OK with generated files being committed to the repo (recommended for plugin docs), or do you prefer generating only in CI/artifacts?
- For GIF generation: is it acceptable to require `ffmpeg`/`gifski` on CI, or should GIFs be generated only locally?

If you answer those, I can propose an exact folder layout (where generated docs go), a concrete test annotation/DSL shape that fits your current `myFixture` patterns, and the set of Gradle tasks to add (names, inputs/outputs, and how to wire into `check`).