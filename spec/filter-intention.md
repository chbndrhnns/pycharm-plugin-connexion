### Goal
Disable the PyCharm/IntelliJ quick-fix/intention that shows as “Update … to reflect signature change” (emitted by `com.intellij.refactoring.suggested.SuggestedRefactoringIntentionContributor` via the `intentionMenuContributor` EP) by implementing logic in our plugin to suppress it, and describe how to verify the suppression.

### Background
- The quick fix is injected dynamically by `SuggestedRefactoringIntentionContributor` (an `IntentionMenuContributor`) that contributes a `MyIntention` with family name `"Suggested Refactoring"` and text from `RefactoringBundle` key `suggested.refactoring.change.signature.intention.text` (e.g., “Update usages/overrides/implementations … to reflect signature change”).
- There is no static intention ID; suppression must happen by filtering the contributed intention or by preventing the contributor from adding it to the menu.

### Approach
- Add a new `IntentionMenuContributor` implementation in the plugin that runs after built-in contributors and removes matching intention descriptors from `ShowIntentionsPass.IntentionsInfo` before the menu is displayed.
- Target only the Suggested Refactoring change-signature intention by checking the contributed action’s family/text (e.g., family = `Suggested Refactoring`, text matches the “Update … to reflect signature change” pattern), to avoid collateral removals.
- Register the contributor in the plugin’s `plugin.xml` under the `intentionMenuContributor` extension point with an ordering hint so it executes after the platform contributor.

### Implementation Steps
1) **Create suppressor contributor class** (Kotlin or Java within the plugin module)
    - Implement `com.intellij.codeInsight.daemon.impl.IntentionMenuContributor`.
    - In `collectActions`, iterate over `intentions.intentionsToShow`, `intentions.errorFixesToShow`, and `intentions.inspectionFixesToShow`; remove any `HighlightInfo.IntentionActionDescriptor` whose underlying `IntentionAction` satisfies:
        - `familyName == "Suggested Refactoring"` and
        - `text` matches/starts with `Update` and contains `reflect signature change` (or use `startsWith("Update ") && text.contains("reflect signature change")`).
    - Ensure safe removal (e.g., filter into a new list or remove via iterator).

2) **Register the contributor in plugin.xml**
    - Add an `<intentionMenuContributor implementation="…SuppressSuggestedRefactoringIntentionContributor" order="last"/>` entry under the plugin’s module descriptor.
    - Ensure the plugin depends on the IntelliJ platform modules that provide the EP (`com.intellij.modules.lang` or the PyCharm core module).

3) **Guard against other contexts**
    - (Optional) Also check for `PriorityAction.Priority.HIGH` plus family/text to further narrow matching.
    - Log (debug) a message when removal happens to ease troubleshooting; avoid noisy logging in production builds.

4) **Build & package**
    - Update project build files if needed (module source set includes the new class).
    - Build the plugin (Gradle/intellij-platform plugin build or IDE build configuration).

5) **Verification steps in PyCharm**
    - Install the built plugin into PyCharm.
    - Open a project with a simple class/function. Edit the signature (e.g., add a parameter) to trigger the “Suggested Refactoring” intention.
    - Place caret on the declaration or a call site where the “Update … to reflect signature change” quick fix would normally appear.
    - Press `Alt+Enter` (or `Show Intention Actions` shortcut) and confirm the “Update … to reflect signature change” entry is **absent** while unrelated intentions remain.
    - (Optional) check that the gutter/inlay hint behavior is unchanged; only the intention should be suppressed.

### Testing Strategy
- **Manual UI check**: primary validation because this is an IDE intention menu change.
- **Automated (optional)**: an IDE integration test could construct a `ShowIntentionsPass.IntentionsInfo` with a mocked `MyIntention`-like action and confirm the suppressor removes it. This is optional if manual verification is acceptable.

### Risks / Mitigations
- Risk: Over-broad filtering could remove other “Suggested Refactoring” intentions (e.g., rename). Mitigation: match both family and the specific signature-change text fragment.
- Risk: Ordering — our contributor must run after the built-in contributor. Use `order="last"` in plugin.xml; if necessary, adjust with `order="after …"` once the specific ID is known.