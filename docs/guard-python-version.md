### Short answer
There is no declarative way in `plugin.xml` to require a minimum Python interpreter version. You must check the project/module SDK at runtime and enable/disable features (or fail fast) based on the detected `LanguageLevel`/Python version.

### Recommended pattern
- Decide the minimum supported Python version (e.g., 3.10).
- Centralize the check in a small helper (reads the Python SDK and resolves its language level/version).
- Gate entry points:
  - `AnAction.update` → disable/hide actions.
  - `ProjectActivity`/`StartupActivity` → show a notification once per project when unsupported.
  - `RunConfiguration.checkConfiguration` → throw `RuntimeConfigurationError` with a clear message.
  - Inspections/intentions → check `LanguageLevel` for the current file/module.

### Kotlin helpers
```kotlin
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.Module
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkUtil

object PythonVersionGuard {
    // Set the minimum you need
    private val minLevel = LanguageLevel.PYTHON310

    fun isSatisfied(project: Project): Boolean {
        val sdk = PythonSdkUtil.findPythonSdk(project) ?: return false
        val level = PythonSdkUtil.getLanguageLevelForSdk(sdk) ?: return false
        return level.isAtLeast(minLevel)
    }

    fun isSatisfied(module: Module): Boolean {
        val sdk = PythonSdkUtil.findPythonSdk(module) ?: return false
        val level = PythonSdkUtil.getLanguageLevelForSdk(sdk) ?: return false
        return level.isAtLeast(minLevel)
    }

    fun require(project: Project) {
        if (!isSatisfied(project)) {
            error("This feature requires Python ${minLevel.versionString} or newer.")
        }
    }
}
```

Notes:
- `LanguageLevel` has constants like `PYTHON39`, `PYTHON310`, etc., and typically an `isAtLeast` helper. If you only have a version string, you can parse it to `LanguageLevel` via Python SDK utilities and then compare.
- `PythonSdkUtil.findPythonSdk(project/module)` returns the configured Python interpreter (SDK). If none is configured, treat as unsupported and prompt the user to configure one.

### Disable actions when unsupported
```kotlin
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MyFeatureAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val ok = project != null && PythonVersionGuard.isSatisfied(project)
        e.presentation.isEnabledAndVisible = ok
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        PythonVersionGuard.require(project)
        // ... run feature ...
    }
}
```

### Notify at project open (optional)
```kotlin
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

class PythonVersionCheckActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!PythonVersionGuard.isSatisfied(project)) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("My Plugin Notifications")
                .createNotification(
                    "Unsupported Python version",
                    "This plugin requires Python 3.10+.",
                    NotificationType.WARNING
                )
                .notify(project)
        }
    }
}
```

### For inspections/intentions
When working with PSI, prefer the file’s effective language level (per-file overrides exist):
```kotlin
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.LanguageLevel

fun isFileLevelSupported(element: PsiElement): Boolean {
    val file = element.containingFile as? PyFile ?: return false
    val level = file.languageLevel  // effective level for this file
    return level.isAtLeast(LanguageLevel.PYTHON310)
}
```

### Why not `plugin.xml`?
- `since-build`/`until-build` only constrain the IDE build, not the project’s Python interpreter version.
- There’s no built-in `<requires python="3.10+"/>` mechanism. Runtime checks are the supported approach.

### UX tips
- Always tell the user which interpreter was detected and which version is required.
- Offer a quick action to open `Project Interpreter` settings when unsupported.
- In multi-module projects, check the relevant module’s SDK instead of the project-wide one.

This approach is simple, robust, and matches common IntelliJ Platform plugin practices for language/SDK gating.