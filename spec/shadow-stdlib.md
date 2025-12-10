### Overview

To warn users when they create a new Python file whose name clashes with a standard‑library module, you’ll typically
implement a small inspection around file creation/renaming. In the IntelliJ Platform / PyCharm world you *don’t* usually
“hook” the low‑level VFS but rather:

1. Listen to file creation and rename events, or
2. Validate names via the rename/create dialogs, or
3. Provide an inspection / intention that flags the conflict once the file exists.

Below is how you’d approach it conceptually, then how it maps into IntelliJ APIs.

---

### 1. Decide when the warning should appear

You have a few UX options; your choice affects the API you hook into:

1. **Immediately on create / rename (modal warning)**
    - User chooses *New | Python File* or renames a file, and you show a warning dialog before finalizing the name.
    - Feels more “preventive”, but is also more intrusive.

2. **As an editor / project inspection (gutter or problems view)**
    - The file gets created normally, but you attach a problem to the file (e.g., inspection on `PyFile` or on the
      virtual file) saying “File name shadows stdlib module `xyz`”.
    - Less intrusive; users can ignore or suppress.

3. **Both**, with a gentle dialog + an inspection for existing files.

The implementation path is very similar; it mostly affects which extension point(s) you implement.

---

### 2. Get a list of standard‑library module names

You’ll need a way to know which names are “reserved”. Approaches:

- **Static list per Python version**
    - Ship a list of `{'os', 'sys', 'pathlib', ...}` per major/minor version; possibly generated from CPython sources or
      `sys.stdlib_module_names` (3.10+).
    - Easiest to implement; needs maintenance when new stdlib modules appear.

- **Dynamic from a configured interpreter**
    - For the project’s active Python SDK, query the stdlib via helper code (e.g., run a tiny helper that prints
      `sys.stdlib_module_names` or inspects `Lib/` in that interpreter).
    - Cache results keyed by SDK.

In PyCharm, you’d typically tie this to the project’s Python SDK (via `PythonSdkUtil` / `Sdk` utilities) and cache the
collected stdlib module names into some in‑memory structure or `UserData`.

For the rest of this explanation, assume you have a helper like:

```kotlin
interface PythonStdlibIndex {
    fun getStdlibModules(sdk: Sdk?): Set<String>
}
```

that returns module base names without extensions.

---

### 3. Hook into file creation / renaming

#### Option A: Intercept file name in dialogs (recommended)

If you want to warn *before* the file appears, you can:

- **Override the New File action for Python** or
- **Attach validation to the input dialog** used for new Python files.

Rough outline for an action-based solution:

1. Extend or wrap the existing *New Python File* action (look for the action ID used in the Python plugin, something
   like `Python.NewFile` in the `plugin.xml`).
2. In `actionPerformed(e: AnActionEvent)`:
    - Open a custom input dialog for the new file name (or reuse standard dialog but with an extra validation step).
    - Extract the base name (strip extension).
    - Resolve project’s Python SDK.
    - Check whether the name is in the stdlib set.
    - If yes, show a warning dialog (with a “Continue anyway” option) or inline validation error.
    - Only then create the file via `WriteCommandAction` and standard `PsiDirectory.createFile()` / template creation
      APIs.

This approach keeps all logic inside your plugin without touching lower-level VFS listeners.

#### Option B: Use a VFS listener (lower level, more generic)

If you want to catch **any** creation of `.py` files (not only via the New action), you can:

1. Register a `VirtualFileListener` in your plugin (via `VirtualFileManager.getInstance().addVirtualFileListener(...)`
   or the newer listener APIs / EPs).
2. Implement `fileCreated(event: VirtualFileEvent)` / `fileRenamed` equivalents.
3. In the handler:
    - Check `event.file.extension == "py"`.
    - Get the file name without extension.
    - Resolve the project context (you may need to map `VirtualFile` ⇒ `Project` using `ProjectLocator` or listeners
      that are project-aware).
    - Get the project’s SDK and call your `PythonStdlibIndex`.
    - If the name is in the stdlib set, show a notification balloon / IDE warning (`NotificationGroupManager`, etc.).

This won’t prevent creation (VFS events are after the fact), but it gives an immediate notification.

#### Option C: PSI / refactoring listeners for renames

To also handle **renaming** of existing files:

- Use `PsiTreeChangeListener` or, more appropriately, refactoring listeners / `PsiFileFactory` hooks.
- In PyCharm, you can also hook into rename refactoring for files (`RenamePsiElementProcessor`) and validate the new
  name before the rename is committed.

For example:

1. Implement `RenamePsiElementProcessor` for `PsiFile` representing Python files.
2. In `substituteElementToRename` or `isInplaceRenameSupported`, you can intercept the rename dialog.
3. In `prepareRenaming`, validate the new name, using the same stdlib check as above, and possibly show a warning.

---

### 4. Provide an inspection for already-existing files (optional but nice)

Even if you hook creation, users might already have conflicting files in their projects. You can add an inspection:

1. Implement a `LocalInspectionTool` that runs on Python files (`PyFile`).
2. In `checkFile` / `buildVisitor`, take the file’s `name` (without `.py`).
3. Resolve the project SDK, query your `PythonStdlibIndex`.
4. If name is in stdlib set, register a problem on the file:

```kotlin
holder.registerProblem(
    file,
    "File name shadows standard library module '$baseName'",
    ProblemHighlightType.WEAK_WARNING
)
```

5. Optionally, provide a quick fix that suggests renaming the file.

This complements the creation‑time hook and is easy to implement given the infrastructure already present in the Python
plugin.

---

### 5. Wiring it into the plugin (IntelliJ Platform specifics)

At a high level, your plugin would have:

- **An EP for the inspection** (`localInspection` in `plugin.xml`) targeting Python files (via language="Python" or the
  correct EP in the Python plugin).
- **Either**:
    - A custom *New Python File* action (declared in `plugin.xml`) that delegates to existing creation logic but adds
      validation, or
    - A `VirtualFileListener` / project component / startup activity that registers the listener and shows
      notifications.
- **Stdlib index component/service**:
    - Application- or project-level service that caches stdlib names by SDK.
    - Optionally updated when SDKs are added/removed or new Python versions are detected.

Pseudo-`plugin.xml` for key parts (very high level):

```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij">
    <localInspection language="Python"
                     implementationClass="com.example.StdlibShadowingFileInspection"/>

    <!-- If you choose a custom New File action -->
    <action id="MyPlugin.NewPythonFile" class="com.example.NewPythonFileWithStdlibCheckAction"
            text="Python File" description="Creates a new Python file with stdlib-name check"
            icon="PythonIcons.Python.File">
      <add-to-group group-id="NewGroup" anchor="before" relative-to-action="Python.NewFile"/>
    </action>
  </extensions>
</idea-plugin>
```

(Exact IDs depend on what the Python plugin uses; you can inspect the existing plugin XML in `python` module.)

---

### 6. Edge cases / behavior details to think about

- **What about packages vs modules?**  
  `collections` might be a directory package (`collections/__init__.py`) in stdlib. Are you warning only on top-level
  `.py` files or also on directories / packages? You might also want to warn when the user creates a package with a
  stdlib name.

- **Python version differences**  
  `importlib.resources` exists only in newer versions; your stdlib set should match the project’s SDK version if
  possible.

- **Third‑party modules with the same name**  
  Sometimes names overlap; you might want to phrase the message as “shadows standard library module in this interpreter”
  rather than hard-coding CPython’s stdlib list.

- **Suppressions / don’t show again**  
  Users might intentionally shadow stdlib for educational or testing reasons.
    - For the inspection, allow `@SuppressInspection` or standard inspection suppression.
    - For the dialog, at minimum a “Don’t show again for this file name / this project” option (for which you can store
      a `Set<String>` in project settings).

- **Multiple SDKs / no SDK**  
  If no Python interpreter is configured, you might skip the check. If multiple SDKs: either merge all stdlib sets or
  base it on the default project SDK.

---

### 7. Concrete first steps in this codebase

Given you’re working inside the `intellij-community` sources:

1. **Locate the existing New Python File action/template**  
   Search in the `python` module for the action that creates Python files (`NewPythonFileAction` or similar; you can use
   `search_project` for `"NewPythonFile"`, `"PythonFileType"`, or `"PyFile"`).

2. **Explore how SDK‑aware analyses are done**  
   Look at existing inspections that need the project’s SDK (e.g., type‑checking or unresolved reference inspections) to
   see which helpers they use.

3. **Prototype an inspection first**  
   It’s often easier to:
    - Implement a `LocalInspectionTool` that flags `PyFile` names that match stdlib.
    - Once you’re happy with the logic and stdlib lookup, reuse the same helper in a smaller create/rename hook for the
      dialog.

If you’d like, tell me which UX style you prefer (hard block vs soft warning vs inspection only), and I can sketch the
exact IntelliJ-side interfaces/classes in this repo you’d most likely need to touch, plus some concrete Kotlin/Java
skeleton code that fits into the `python` module here.