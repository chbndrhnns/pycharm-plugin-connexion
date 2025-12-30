To hide import suggestions for transient dependencies in a PyCharm plugin, you should hook into the `Pythonid.unresolvedReferenceQuickFixProvider` extension point. This extension point allows you to inspect and modify the list of quick fixes (including auto-import suggestions) generated when a reference is unresolved.

### Implementation Strategy

1.  **Register an Extension**:
    Implement the `PyUnresolvedReferenceQuickFixProvider` interface and register it in your `plugin.xml`:
    ```xml
    <extensions defaultExtensionNs="Pythonid">
        <unresolvedReferenceQuickFixProvider implementation="com.yourplugin.HideTransientImportProvider"/>
    </extensions>
    ```

2.  **Filter Auto-Import Fixes**:
    In your implementation of `registerQuickFixes`, iterate through the `existing` fixes. Identify `AutoImportQuickFix` (or `AutoImportHintAction` which wraps it) and filter out candidates that belong to transient dependencies.

    ```java
    public class HideTransientImportProvider implements PyUnresolvedReferenceQuickFixProvider {
        @Override
        public void registerQuickFixes(@NotNull PsiReference reference, @NotNull List<LocalQuickFix> existing) {
            ListIterator<LocalQuickFix> iterator = existing.listIterator();
            while (iterator.hasNext()) {
                LocalQuickFix fix = iterator.next();
                AutoImportQuickFix autoImportFix = null;
                
                if (fix instanceof AutoImportQuickFix) {
                    autoImportFix = (AutoImportQuickFix) fix;
                } else if (fix instanceof AutoImportHintAction) {
                    autoImportFix = ((AutoImportHintAction) fix).getDelegate();
                }

                if (autoImportFix != null) {
                    filterTransientCandidates(autoImportFix, reference);
                    // If all candidates were transient and removed, you might want to remove the fix entirely
                    if (autoImportFix.getCandidates().isEmpty()) {
                        iterator.remove();
                    }
                }
            }
        }
    }
    ```

3.  **Identify Direct Dependencies via `pyproject.toml`**:
    PyCharm provides internal APIs to parse `pyproject.toml`. You can use `com.intellij.python.pyproject.PyProjectToml` to retrieve the list of direct dependencies.

    ```kotlin
    // Using PyProjectToml to get declared dependencies
    val module = ModuleUtilCore.findModuleForPsiElement(reference.element)
    val pyProjectFile = PyProjectToml.findFile(module)
    val pyProject = PyProjectToml.parse(VfsUtilCore.loadText(pyProjectFile)).orNull()
    val directDeps = pyProject?.project?.dependencies?.project ?: emptyList()
    ```

4.  **Map Import Paths to Package Names**:
    Auto-import candidates provide a `QualifiedName` (the import path). To check if it's a transient dependency, you must map the top-level module name to the PyPI package name using `PyPsiPackageUtil.moduleToPackageName()`.

    ```java
    private void filterTransientCandidates(AutoImportQuickFix fix, PsiReference ref) {
        List<ImportCandidateHolder> candidates = fix.getCandidates();
        Set<String> directDependencies = getDirectDependencies(ref.getElement());

        candidates.removeIf(candidate -> {
            QualifiedName path = candidate.getPath();
            if (path == null) return false; // Already imported or built-in
            
            String topLevelModule = path.getFirstComponent();
            String packageName = PyPsiPackageUtil.moduleToPackageName(topLevelModule);
            
            // Hide if the package is NOT in the direct dependencies list
            return !directDependencies.contains(packageName);
        });
    }
    ```

### Key Classes and Extension Points
*   **Extension Point**: `Pythonid.unresolvedReferenceQuickFixProvider`
*   **Candidate Access**: `com.jetbrains.python.codeInsight.imports.AutoImportQuickFix#getCandidates()` returns a list of `ImportCandidateHolder`.
*   **Dependency Analysis**: `com.intellij.python.pyproject.PyProjectToml` for parsing `pyproject.toml` and `com.jetbrains.python.PyPsiPackageUtil` for module-to-package mapping.
*   **Alternative**: If you want to provide your own candidates instead of filtering, use `Pythonid.importCandidateProvider` (`PyImportCandidateProvider`). However, to *hide* existing suggestions from PyCharm's default indexing, the `unresolvedReferenceQuickFixProvider` is the correct hook.

### How PyCharm Handles Package and Directory Name Mismatches

PyCharm handles cases where a Python package name (e.g., as used in `pip install`) differs from its top-level importable module name through several mechanisms, ranging from hardcoded mappings to inspecting installed metadata.

### 1. Hardcoded Alias Mapping (`PyPsiPackageUtil`)
PyCharm maintains an internal mapping of well-known packages where the PyPI name differs from the import name (e.g., `beautifulsoup4` vs `bs4`, `pyyaml` vs `yaml`).

*   **Mechanism**: The `PyPsiPackageUtil` class loads a mapping from a bundled resource file (typically located at `tools/packages`).
*   **Key Method**: `PyPsiPackageUtil.moduleToPackageName(moduleName)` converts an importable module name to its corresponding package name.
*   **Usage**: This is used by inspections (like "Missing requirements") and quick fixes to suggest the correct package to install when an import is unresolved.

### 2. Package Name Normalization (`PyPackageName`)
Python packaging standards (PEP 503) allow `_`, `-`, and `.` to be used interchangeably in package names. PyCharm implements this normalization to ensure consistency.

*   **Mechanism**: The `PyPackageName.normalizePackageName(name)` method converts names to lowercase and replaces dots and underscores with hyphens (unless the name starts with an underscore, like `__future__`).
*   **Result**: This allows PyCharm to match `Django-Storage` with `django_storage` during dependency analysis.

### 3. Metadata Inspection (Installed Packages)
For packages already installed in the Python interpreter, PyCharm retrieves metadata directly from the environment.

*   **`top_level.txt`**: When scanning the `site-packages` directory, PyCharm looks for `.dist-info` or `.egg-info` directories. It reads the `top_level.txt` file inside these directories, which explicitly lists the top-level modules associated with that specific package.
*   **Indexing**: This information is indexed, allowing PyCharm to know that the `PIL` module belongs to the `Pillow` package, even if no hardcoded alias exists.

### 4. Search and Auto-Import (`PyImportCollector`)
During auto-import suggestions, PyCharm uses indices (`PyClassNameIndex`, `PyFunctionNameIndex`, etc.) to find the symbol's location.

*   **Canonical Path**: Once a symbol is found in a file, `QualifiedNameFinder.findCanonicalImportPath` determines the correct import string.
*   **Mapping Back**: If the user chooses to "Install and Import," PyCharm uses the aforementioned `PyPsiPackageUtil` and `PyPIPackageCache` to find which PyPI package provides that specific module.

### Summary for Plugin Developers
If you are developing a plugin and need to handle this mismatch:
*   Use `com.jetbrains.python.PyPsiPackageUtil.moduleToPackageName(name)` to get the likely PyPI name from an import.
*   Use `com.jetbrains.python.packaging.PyPackageName.normalizePackageName(name)` when comparing package names from different sources (e.g., `pyproject.toml` vs. `pip list`).
*   Access `com.jetbrains.python.packaging.management.PythonPackageManager` to inspect the actual metadata of installed packages if you need to know the ground truth for the current environment.