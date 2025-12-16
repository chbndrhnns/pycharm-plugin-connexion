### Goal
In PyCharm/IntelliJ, the “quick fix” list is just a list of `IntentionAction`s (some come from inspections as `LocalQuickFix`, but they are still shown via the intention UI). To **remove** one particular existing fix (“Rename to self”) and **offer** your own (“Add 'self' parameter”), you typically do two things:

1) **Provide your own intention/quick fix** that solves the same situation.
2) **Filter out** the platform/Python-plugin intention that you don’t want to show.

### 1) First identify what actually produces “Rename to self”
Don’t guess; determine the actual `IntentionAction` class.

#### Quick way (works well in tests)
Create a small test that opens a Python file where the fix appears and prints/inspects `myFixture.availableIntentions`:

```kotlin
val intentions = myFixture.availableIntentions
println(intentions.joinToString("\n") { "${it.text} -> ${it.javaClass.name}" })
```

This gives you:
- the exact `text` shown (“Rename to self”)
- the implementing class name (what you should filter on)

Why this matters: different “Rename …” actions can share similar text; filtering by class is usually safer than filtering by text.

### 2) Implement “Add 'self' parameter”
You can implement this either as:
- a standalone `IntentionAction` (always shown when available), or
- a `LocalQuickFix` attached to *your* inspection (more canonical “quick fix” UX)

If you want it to appear in the same situations as the existing Python inspection fix, an inspection-based quick fix is usually the best approach.

#### Typical availability condition
Common scenario for “Add `self`”:
- You’re inside a `PyFunction` that is a method (function defined in a `PyClass`).
- The function is *not* `@staticmethod`.
- The parameter list is empty or the first parameter isn’t an instance parameter.

Optionally, you can be stricter:
- Only offer if the body references `self` (so you don’t “add self” to methods that don’t use it).

#### How to apply the fix
In the fix `applyFix` (or `invoke` if you do an intention):
- Get the `PyFunction` PSI.
- Modify its `PyParameterList` in a write action.
- Insert a new parameter named `self` as the first parameter.

In Python PSI, you typically generate new PSI via `PyElementGenerator` and replace/insert elements rather than string-editing.

Pseudo-outline:
```kotlin
class AddSelfParameterQuickFix : LocalQuickFix {
  override fun getFamilyName() = "Add 'self' parameter"

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val function = descriptor.psiElement.parentOfType<PyFunction>(withSelf = false) ?: return
    val params = function.parameterList

    WriteCommandAction.runWriteCommandAction(project) {
      val generator = PyElementGenerator.getInstance(project)
      val selfParam = generator.createParameter("self")
      params.addParameterBefore(selfParam, params.parameters.firstOrNull())
    }
  }
}
```
(Exact helpers vary by PyCharm build; the key point is: **use PSI generation + insertion under a write command**.)

### 3) Suppress only the “Rename to self” action (not the whole inspection)
You generally do **not** want to disable an entire inspection just to remove one fix.

Instead, register an **intention/quick-fix filter** via the IntelliJ Platform extension point that can decide, at runtime, whether an `IntentionAction` should be shown in a given context.

#### Approach
- Implement an `IntentionActionFilter`-style extension (exact EP name depends on the platform version, but the concept is stable):
  - It receives the candidate `IntentionAction` plus editor/file/offset context.
  - Return **false** to hide that action.

Your filter logic:
- If `action.text == "Rename to self"` **or** `action.javaClass.name == "<the class you found in step 1>"` …
- and the PSI context is one where your “Add 'self' parameter” should be offered …
- then suppress it.

You should **scope the suppression** so you don’t hide it globally in places where it’s still useful.

Practical scoping examples:
- Only when the target is a `PyFunction` inside a `PyClass`.
- Only when there are **zero parameters** (if that’s your target case).
- Only when the method is not annotated with `@staticmethod`.

### 4) Make sure your action is preferred/visible
Even after filtering the unwanted fix, ensure your fix is easy to pick:
- Implement `PriorityAction` / `HighPriorityAction` (depending on what’s available) so it sorts near the top.
- Keep the text exactly as desired: `Add 'self' parameter`.

### 5) Add tests (recommended)
Add a test that:
1) Configures a Python method in a class missing `self`.
2) Asserts:
   - `myFixture.availableIntentions` does **not** contain “Rename to self”.
   - It **does** contain “Add 'self' parameter”.
3) Launches the action and checks resulting code.

Example assertions:
```kotlin
assertFalse(myFixture.availableIntentions.any { it.text == "Rename to self" })
assertTrue(myFixture.availableIntentions.any { it.text == "Add 'self' parameter" })
```

Given your repo conventions, you’ll typically extend `fixtures.TestBase` so Python helpers + mock SDK are configured.

### 6) Debugging tips
If filtering doesn’t seem to work:
- Confirm your filter EP is loaded (check plugin.xml / logs).
- In a test, inspect `availableIntentions` and print class names before and after registering the filter.
- Be careful about localization: filtering by `text` can break in non-English IDEs; filtering by **class** is more robust.

### What I’d do in this project (minimal-risk sequence)
1) Write a focused test that reproduces “Rename to self” and prints the `IntentionAction` class name.
2) Implement “Add 'self' parameter” as a quick fix (inspection) or intention.
3) Add an intention-action filter that suppresses exactly that class (and only in the contexts you handle).
4) Add tests for both “suppressed” and “replacement works”.

If you want, tell me the exact code snippet where you see “Rename to self” (a minimal Python example), and I can propose the precise availability predicate (when to show “Add self” vs when you should *not* hide “Rename to self”).