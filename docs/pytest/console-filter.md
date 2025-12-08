I have implemented the `PytestConsoleFilter` to parse `pytest` node IDs in the terminal and added a corresponding test.

### 1. Created `PytestConsoleFilter.kt`
I created the filter class `com.jetbrains.python.testing.PytestConsoleFilter` which implements `Filter`. It uses a regular expression to detect pytest node IDs (e.g., `path/to/test.py::test_function`) and provides a `HyperlinkInfo` that resolves the file and the specific PSI element (class or function) to navigate to.

**File:** `python/src/com/jetbrains/python/testing/PytestConsoleFilter.kt`

```kotlin
package com.jetbrains.python.testing

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.types.TypeEvalContext
import java.io.File
import java.util.regex.Pattern

class PytestConsoleFilter(private val project: Project, private val workingDirectory: String? = project.basePath) : Filter {
    companion object {
        private val PATTERN = Pattern.compile("^\\s*(.+?\\.py)::(\\S+)")
    }

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matcher = PATTERN.matcher(line)
        if (matcher.find()) {
            val filePath = matcher.group(1)
            val nodeId = matcher.group(2)

            val startOffset = entireLength - line.length + matcher.start()
            val endOffset = entireLength - line.length + matcher.end()

            return Filter.Result(startOffset, endOffset, PytestNodeHyperlinkInfo(project, workingDirectory, filePath, nodeId))
        }
        return null
    }

    private class PytestNodeHyperlinkInfo(
        private val project: Project,
        private val workingDirectory: String?,
        private val filePath: String,
        private val nodeId: String
    ) : HyperlinkInfo {

        override fun navigate(project: Project) {
            val file = findFile(filePath) ?: return
            val psiFile = PsiManager.getInstance(project).findFile(file) as? PyFile ?: return

            val element = resolveElement(psiFile, nodeId)
            (element as? com.intellij.pom.Navigatable)?.navigate(true)
        }

        private fun findFile(path: String): com.intellij.openapi.vfs.VirtualFile? {
            val fs = LocalFileSystem.getInstance()
            var vFile = fs.findFileByPath(path)
            if (vFile == null && workingDirectory != null) {
                vFile = fs.findFileByIoFile(File(workingDirectory, path))
            }
            return vFile
        }

        private fun resolveElement(file: PyFile, nodeId: String): PsiElement? {
            val parts = nodeId.split("::")
            var currentElement: PsiElement = file
            val context = TypeEvalContext.codeAnalysis(project, file)

            for (part in parts) {
                val name = part.substringBefore("[")

                if (currentElement is PyFile) {
                    val cls = currentElement.findTopLevelClass(name)
                    if (cls != null) {
                        currentElement = cls
                        continue
                    }
                    val func = currentElement.findTopLevelFunction(name)
                    if (func != null) {
                        currentElement = func
                        continue
                    }
                    return null
                } else if (currentElement is PyClass) {
                    val method = currentElement.findMethodByName(name, false, context)
                    if (method != null) {
                        currentElement = method
                        continue
                    }
                    val nested = currentElement.findNestedClass(name, false)
                    if (nested != null) {
                        currentElement = nested
                        continue
                    }
                    return null
                }
            }

            return if (currentElement != file) currentElement else null
        }
    }
}
```

### 2. Registered the Filter
I updated `PyMessageFilterProvider` to include the new `PytestConsoleFilter` in the default filters list. This ensures it is active for Python run configurations.

**File:** `python/src/com/jetbrains/python/run/PyMessageFilterProvider.kt`

```kotlin
// ... imports
import com.jetbrains.python.testing.PytestConsoleFilter

class PyMessageFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(
    PythonTracebackFilter(project),
    PythonInstallPackageFilter(project),
    PytestConsoleFilter(project)
  )
}
```

### 3. Added a Test
I added a unit test to verify the filter parses the output correctly.

**File:** `python/testSrc/com/jetbrains/python/testing/PytestConsoleFilterTest.kt`

```kotlin
package com.jetbrains.python.testing

import com.jetbrains.python.fixtures.PyTestCase

class PytestConsoleFilterTest : PyTestCase() {
    fun testParse() {
        val filter = PytestConsoleFilter(myFixture.project, "/")
        val line = "test_file.py::test_func"
        val result = filter.applyFilter(line, line.length)
        assertNotNull(result)
    }
}
```