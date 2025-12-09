### Approach for Generating Pytest Node IDs

To generate valid `pytest` node IDs from `SMTestProxy` within a PyCharm plugin, you need to bridge the gap between PyCharm's generic test representation and the specific `nodeid` format used by pytest.

PyCharm's Python plugin uses a specific protocol (`python<...>`) in `locationUrl` to map test nodes back to PSI elements. You can leverage the existing `PyTestsLocator` to resolve these elements and then reconstruct the `nodeid`.

### 1. Data Record Definition

Define a record (or data class) to hold the representations. This serves as the intermediary format.

```kotlin
import com.intellij.psi.PsiElement
import com.intellij.openapi.vfs.VirtualFile

data class PytestTestRecord(
    val nodeid: String,
    val psiElement: PsiElement?,
    val file: VirtualFile?,
    val locationUrl: String,
    val metainfo: String?
)
```

### 2. Conversion Logic

Create a utility or service to perform the conversion. The key steps are:
1.  **Resolve Location**: Use `PyTestsLocator` to convert `SMTestProxy.locationUrl` into a `Location<PsiElement>`.
2.  **Determine Relative Path**: Convert the file's absolute path to a path relative to the project root (as pytest expects).
3.  **Construct Suffix**: Walk up the PSI tree (e.g., from Method -> Class) to build the `::Class::Method` suffix.
4.  **Handle Parameters**: If `metainfo` is present (supplied by `pytest-teamcity` plugin), use it to correctly format parameterized tests (e.g., `test_fn[param1]`).

#### Implementation Example (Kotlin)

```kotlin
import com.intellij.execution.Location
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.testing.PyTestsLocator

object PytestNodeIdGenerator {

    fun createRecordFrom(proxy: SMTestProxy, project: Project): PytestTestRecord? {
        val locationUrl = proxy.locationUrl ?: return null
        
        // 1. Resolve to PSI using PyCharm's internal locator
        // PyTestsLocator handles the "python<...>" protocol
        val locations = PyTestsLocator.getLocation(
            locationUrl, 
            project, 
            GlobalSearchScope.projectScope(project)
        )
        
        val location = locations.firstOrNull()
        val element = location?.psiElement
        val file = element?.containingFile?.virtualFile
        
        // Pytest plugin passes test name (potentially with params) in metainfo
        // e.g. "test_my_func[param1]"
        val metainfo = proxy.metainfo 

        if (file == null) return null

        // 2. Calculate Node ID
        val nodeid = calculateNodeId(element, file, project, metainfo)

        return PytestTestRecord(
            nodeid = nodeid,
            psiElement = element,
            file = file,
            locationUrl = locationUrl,
            metainfo = metainfo
        )
    }

    private fun calculateNodeId(
        element: PsiElement, 
        file: VirtualFile, 
        project: Project, 
        metainfo: String?
    ): String {
        // A. Get relative path for file (pytest uses / separator)
        val relativePath = ProjectFileIndex.getInstance(project)
            .getContentRootForFile(file)
            ?.let { root -> 
                com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(file, root) 
            } ?: file.path // Fallback to absolute if root not found

        // B. Build the PSI suffix (::Class::Method)
        val parts = mutableListOf<String>()
        var current: PsiElement? = element
        
        // Determine the leaf name
        // If metainfo is present, it usually contains the precise test name 
        // including parameters (e.g. "test_add[1-2]") which PSI doesn't have.
        var leafName = metainfo
        
        while (current != null && current !is PyFile) {
            if (current is PyClass || current is PyFunction) {
                val name = if (current == element && !leafName.isNullOrEmpty()) {
                    leafName // Use metainfo for the leaf (test method)
                } else {
                    (current as? com.intellij.psi.PsiNamedElement)?.name
                }
                
                if (name != null) {
                    parts.add(0, name)
                }
            }
            current = current.parent
        }

        // C. Combine parts
        // pytest format: path/to/file.py::ClassName::MethodName
        return if (parts.isEmpty()) {
            relativePath
        } else {
            "$relativePath::${parts.joinToString("::")}"
        }
    }
}
```

### 3. Integration with Tree Panel

To use this from a PyCharm UI action (e.g., right-click context menu on the Test Runner tree), you can fetch the selected `SMTestProxy`.

**In `plugin.xml`**:
Ensure you depend on the Python plugin modules.
```xml
<depends>com.intellij.modules.python</depends>
```

**Action Implementation**:
```kotlin
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ShowNodeIdAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Get selected test proxy from context
        val selectedProxy = e.getData(AbstractTestProxy.DATA_KEY) as? com.intellij.execution.testframework.sm.runner.SMTestProxy
        
        if (selectedProxy != null) {
            val record = PytestNodeIdGenerator.createRecordFrom(selectedProxy, project)
            
            if (record != null) {
                Messages.showInfoMessage(
                    "Node ID: ${record.nodeid}\nPsiElement: ${record.psiElement}", 
                    "Pytest Node Info"
                )
            } else {
                Messages.showWarningDialog("Could not resolve test record.", "Error")
            }
        }
    }
}
```

### Key Considerations

*   **`metainfo`**: The `pytest` integration in PyCharm sends the test name (often including parameters like `[param]`) as "metainfo". This is crucial for distinguishing parameterized test cases, as the `locationUrl` usually points only to the function definition, which is shared by all parameters.
*   **Protocol**: PyCharm uses `python<...>` protocol strings in `locationUrl` to encode the FQN. `PyTestsLocator` is the standard way to decode this.
*   **Separators**: Ensure file paths use `/` as separators for valid `pytest` node IDs, even on Windows.
*   **Dependencies**: You must compile against `intellij.python.community.impl` (or similar) to access `PyTestsLocator` and PSI classes (`PyClass`, `PyFunction`).