To implement the "Test failed line" highlighting in a PyCharm plugin, you need to extend the existing Python test runner infrastructure. Since PyCharm uses its own `PythonTRunnerConsoleProperties` and `PyTestsLocator`, you must register a custom `SMStacktraceParser` to correctly extract failing line numbers from Python tracebacks.

### 1. Implement a Python-specific `TestStackTraceParser`
Python stack traces are not parsed correctly by the default JVM-oriented parser. You should create a parser that uses PyCharm's internal `TraceBackParser` to identify the failing file and line.

```kotlin
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser
import com.intellij.openapi.project.Project
import com.jetbrains.python.traceBackParsers.TraceBackParser

class PyTestStackTraceParser(
    url: String,
    proxy: SMTestProxy,
    project: Project
) : TestStackTraceParser(url, proxy.stacktrace, proxy.errorMessage, proxy.locator, project) {

    private var myFailedLine = -1
    private val myErrorMessage = proxy.errorMessage

    init {
        val stacktrace = proxy.stacktrace
        if (stacktrace != null) {
            // Logic to find the failing line in the test file
            val lines = stacktrace.split("\n")
            for (line in lines) {
                // Use PyCharm's built-in parsers (PyTracebackParser, PyTestTracebackParser)
                for (parser in TraceBackParser.PARSERS) {
                    val link = parser.findLinkInTrace(line)
                    if (link != null) {
                        // In a real implementation, verify link.fileName matches the test file
                        myFailedLine = link.lineNumber
                        break
                    }
                }
                if (myFailedLine != -1) break
            }
        }
    }

    override fun getFailedLine(): Int = myFailedLine
    override fun getErrorMessage(): String? = myErrorMessage
}
```

### 2. Extend `PythonTRunnerConsoleProperties`
You need to ensure the test runner uses your parser. The best way is to wrap or extend the properties provided by the Python test configuration.

```kotlin
import com.intellij.execution.Executor
import com.intellij.execution.testframework.sm.SMStacktraceParser
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser
import com.intellij.openapi.project.Project
import com.jetbrains.python.testing.PyAbstractTestConfiguration
import com.jetbrains.python.testing.PythonTRunnerConsoleProperties

class MyPyTestConsoleProperties(
    config: PyAbstractTestConfiguration,
    executor: Executor
) : PythonTRunnerConsoleProperties(config, executor, true, config.testLocator), SMStacktraceParser {

    override fun getTestStackTraceParser(url: String, proxy: SMTestProxy, project: Project): TestStackTraceParser {
        return PyTestStackTraceParser(url, proxy, project)
    }
}
```

### 3. Register the Custom Properties in your Plugin
If you are creating a new test configuration type, override `createTestConsoleProperties`. If you want to support existing ones, you might need to use an `SMTRunnerEventsListener` to intercept test results, though providing a custom `SMStacktraceParser` via the configuration is the standard way.

### 4. Implementation of `TestFailedLineManager` and Inspection
With the `failedLine` now correctly stored in `TestStateStorage`, follow these steps:

1.  **Service**: Implement `TestFailedLineManager`. It should map a `PsiElement` (like a `PyFunction` or a `PyCallExpression`) to the URL used in `TestStateStorage`. For Python, this URL usually looks like `python<path>://module.ClassName.methodName`.
2.  **Inspection**: Create a `PyTestFailedLineInspection` (extending `LocalInspectionTool`).
    *   Use a `PyElementVisitor` to visit test methods and assertions.
    *   Check `TestFailedLineManager.getInstance(project).getTestInfo(element)`.
    *   If a failure is found, register a problem using `ProblemHighlightType.GENERIC_ERROR_OR_WARNING` and apply `CodeInsightColors.RUNTIME_ERROR` text attributes.

### plugin.xml Registration
```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Register the manager implementation -->
    <projectService 
        serviceInterface="com.intellij.testIntegration.TestFailedLineManager"
        serviceImplementation="com.yourplugin.PyTestFailedLineManagerImpl"/>

    <!-- Register the local inspection -->
    <localInspection 
        language="Python" 
        displayName="Python test failed line" 
        groupName="Python" 
        enabledByDefault="true" 
        level="WARNING" 
        implementationClass="com.yourplugin.PyTestFailedLineInspection"/>
</extensions>
```

### Key Technical Details
*   **Line Numbers**: Python tracebacks use 1-based line numbers. Ensure you convert these correctly to 0-based offsets when highlighting in the editor via the `Document` API.
*   **Locators**: Use `PyTestsLocator` to resolve the test URLs back to PSI elements to ensure consistency with PyCharm's "Jump to Source" functionality.