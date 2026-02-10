### Advanced Pytest Fixture Explorer — Implementation Guide

A comprehensive guide for building a PyCharm plugin that provides a **Tool Window** for exploring pytest tests and their fixture dependency trees. The plugin periodically runs `pytest --collect-only`, parses the output, matches results to PSI elements, and presents an interactive tree UI.

---

### Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Plugin Descriptor & Dependencies](#2-plugin-descriptor--dependencies)
3. [Data Model](#3-data-model)
4. [Collection: Running `pytest --collect-only`](#4-collection-running-pytest---collect-only)
5. [Parsing Collector Output](#5-parsing-collector-output)
6. [PSI Matching & Navigation](#6-psi-matching--navigation)
7. [Indexing & Caching](#7-indexing--caching)
8. [Tool Window UI](#8-tool-window-ui)
9. [Trigger Strategies (Events & Scheduling)](#9-trigger-strategies-events--scheduling)
10. [Testing Strategy](#10-testing-strategy)
11. [Performance & Scalability](#11-performance--scalability)
12. [Extension Points & Future Work](#12-extension-points--future-work)

---

### 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                    Tool Window UI                        │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │  Test Tree   │  │ Fixture Tree │  │ Detail Panel   │  │
│  └──────┬──────┘  └──────┬───────┘  └───────┬────────┘  │
│         │                │                   │           │
│         └────────────────┼───────────────────┘           │
│                          │                               │
├──────────────────────────┼───────────────────────────────┤
│              PytestExplorerModel (Service)                │
│  ┌───────────────┐  ┌────────────┐  ┌─────────────────┐ │
│  │ CollectionData │  │ FixtureMap │  │ PSI Resolver    │ │
│  └───────┬───────┘  └─────┬──────┘  └────────┬────────┘ │
│          │                │                   │          │
├──────────┼────────────────┼───────────────────┼──────────┤
│          │         Index / Cache Layer         │          │
│  ┌───────┴────────────────┴───────────────────┴───────┐  │
│  │          PytestCollectionIndex (FileBasedIndex)     │  │
│  └────────────────────────┬───────────────────────────┘  │
│                           │                              │
├───────────────────────────┼──────────────────────────────┤
│               Collection Engine                          │
│  ┌────────────────────────┴───────────────────────────┐  │
│  │  PytestCollectorTask (runs pytest --collect-only)  │  │
│  └────────────────────────┬───────────────────────────┘  │
│                           │                              │
│                    Python SDK / venv                      │
└──────────────────────────────────────────────────────────┘
```

**Key principles:**

- **Separation of concerns:** Collection, parsing, PSI resolution, and UI are independent layers.
- **Non-blocking:** All `pytest` invocations run on background threads via `Task.Backgroundable`.
- **Incremental updates:** Re-collect only changed modules when possible.
- **PSI-safe:** All PSI access happens on the EDT or under `ReadAction`.

---

### 2. Plugin Descriptor & Dependencies

#### `plugin.xml`

```xml
<idea-plugin>
  <id>com.example.pytest-fixture-explorer</id>
  <name>Pytest Fixture Explorer</name>
  <version>1.0.0</version>
  <vendor>Your Name</vendor>

  <depends>com.intellij.modules.python</depends>
  <depends>com.intellij.modules.platform</depends>

  <extensions defaultExtensionNs="com.intellij">
    <!-- Tool Window -->
    <toolWindow id="Pytest Explorer"
                anchor="bottom"
                factoryClass="com.example.pytestexplorer.ui.PytestExplorerToolWindowFactory"
                icon="AllIcons.Toolwindows.ToolWindowStructure"/>

    <!-- Project-level service holding the model -->
    <projectService
        serviceImplementation="com.example.pytestexplorer.model.PytestExplorerService"/>

    <!-- File-based index for collected test metadata -->
    <fileBasedIndex
        implementation="com.example.pytestexplorer.index.PytestCollectionIndex"/>

    <!-- Startup activity to schedule initial collection -->
    <postStartupActivity
        implementation="com.example.pytestexplorer.PytestExplorerStartupActivity"/>
  </extensions>

  <actions>
    <action id="PytestExplorer.Refresh"
            class="com.example.pytestexplorer.actions.RefreshCollectionAction"
            text="Refresh Pytest Collection"
            icon="AllIcons.Actions.Refresh"/>
  </actions>
</idea-plugin>
```

#### `build.gradle.kts` (Gradle IntelliJ Plugin)

```kotlin
plugins {
    id("org.jetbrains.intellij") version "1.17.0"
    kotlin("jvm") version "1.9.22"
}

intellij {
    version.set("2024.1")
    type.set("PY") // Target PyCharm
    plugins.set(listOf("python-ce")) // or "python" for Professional
}
```

---

### 3. Data Model

#### Core Data Classes

```kotlin
package com.example.pytestexplorer.model

import com.intellij.psi.SmartPsiElementPointer

/**
 * Represents a single collected pytest test item.
 *
 * @param nodeId The pytest node ID, e.g. "tests/test_auth.py::TestLogin::test_success"
 * @param modulePath Relative path to the test module
 * @param className Optional test class name
 * @param functionName Test function/method name
 * @param fixtures List of fixture names this test depends on
 * @param parametrizeIds Parametrize variant IDs, if any
 */
data class CollectedTest(
    val nodeId: String,
    val modulePath: String,
    val className: String?,
    val functionName: String,
    val fixtures: List<String>,
    val parametrizeIds: List<String> = emptyList(),
)

/**
 * Represents a pytest fixture discovered via collection or PSI analysis.
 *
 * @param name The fixture name (may differ from function name via @pytest.fixture(name=...))
 * @param scope Fixture scope: "function", "class", "module", "package", "session"
 * @param definedIn Path to the file where the fixture is defined
 * @param functionName The actual Python function name
 * @param dependencies Names of other fixtures this fixture depends on
 * @param isAutouse Whether the fixture has autouse=True
 */
data class CollectedFixture(
    val name: String,
    val scope: String,
    val definedIn: String,
    val functionName: String,
    val dependencies: List<String>,
    val isAutouse: Boolean = false,
)

/**
 * A resolved reference linking collected data to a live PSI element.
 */
data class ResolvedTestElement(
    val test: CollectedTest,
    val psiPointer: SmartPsiElementPointer<*>?,
    val resolvedFixtures: List<ResolvedFixtureElement>,
)

data class ResolvedFixtureElement(
    val fixture: CollectedFixture,
    val psiPointer: SmartPsiElementPointer<*>?,
    val dependencies: List<ResolvedFixtureElement>, // recursive tree
)

/**
 * Immutable snapshot of the entire collection result.
 */
data class CollectionSnapshot(
    val timestamp: Long,
    val tests: List<CollectedTest>,
    val fixtures: List<CollectedFixture>,
    val errors: List<String>, // collection errors/warnings
)
```

#### Dependency Graph

```kotlin
package com.example.pytestexplorer.model

/**
 * Directed acyclic graph of fixture dependencies.
 * Nodes are fixture names; edges point from a fixture to its dependencies.
 */
class FixtureDependencyGraph(private val fixtures: Map<String, CollectedFixture>) {

    /** Returns the full transitive dependency tree for a given fixture. */
    fun dependenciesOf(fixtureName: String): List<CollectedFixture> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CollectedFixture>()
        fun dfs(name: String) {
            if (!visited.add(name)) return
            val fixture = fixtures[name] ?: return
            for (dep in fixture.dependencies) {
                dfs(dep)
            }
            result.add(fixture)
        }
        dfs(fixtureName)
        return result
    }

    /** Returns all fixtures that directly or transitively depend on the given fixture. */
    fun dependentsOf(fixtureName: String): List<CollectedFixture> {
        val reverseMap = mutableMapOf<String, MutableList<String>>()
        for ((name, fixture) in fixtures) {
            for (dep in fixture.dependencies) {
                reverseMap.getOrPut(dep) { mutableListOf() }.add(name)
            }
        }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CollectedFixture>()
        fun dfs(name: String) {
            if (!visited.add(name)) return
            fixtures[name]?.let { result.add(it) }
            reverseMap[name]?.forEach { dfs(it) }
        }
        dfs(fixtureName)
        return result
    }

    /** Detects circular dependencies (should not exist in valid pytest, but handle gracefully). */
    fun findCycles(): List<List<String>> {
        // Tarjan's or simple DFS-based cycle detection
        // ...
        return emptyList()
    }
}
```

---

### 4. Collection: Running `pytest --collect-only`

#### Collector Task

```kotlin
package com.example.pytestexplorer.collection

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.testing.PyTestDiscoveryUtil

/**
 * Runs `pytest --collect-only -q` in the background and delivers parsed results
 * to [PytestExplorerService].
 *
 * Key design decisions:
 * - Uses the project's configured Python SDK to find the correct pytest.
 * - Passes `--collect-only -q` for machine-parseable quiet output.
 *   Alternatively, use `--collect-only --quiet --quiet` (double-quiet) for
 *   minimal output, or the JSON report plugin for structured output.
 * - Runs per-module if the project has multiple Python modules with different SDKs.
 * - Cancellable via ProgressIndicator.
 */
class PytestCollectorTask(
    private val project: Project,
    private val onComplete: (CollectionResult) -> Unit,
) : Task.Backgroundable(project, "Collecting Pytest Tests", true) {

    override fun run(indicator: ProgressIndicator) {
        indicator.text = "Discovering pytest tests and fixtures..."
        indicator.isIndeterminate = false

        val modules = ModuleManager.getInstance(project).modules
            .filter { PyTestDiscoveryUtil.isPyTestSelected(it) }

        if (modules.isEmpty()) {
            onComplete(CollectionResult.empty("No pytest modules found"))
            return
        }

        val allTests = mutableListOf<RawCollectedItem>()
        val errors = mutableListOf<String>()

        for ((index, module) in modules.withIndex()) {
            indicator.fraction = index.toDouble() / modules.size
            indicator.text2 = "Collecting from module: ${module.name}"

            if (indicator.isCanceled) return

            val sdk = PythonSdkUtil.findPythonSdk(module) ?: continue
            val pythonPath = sdk.homePath ?: continue
            val workDir = module.moduleFile?.parent?.path
                ?: project.basePath ?: continue

            try {
                val result = runPytestCollect(pythonPath, workDir, indicator)
                allTests.addAll(result.items)
                errors.addAll(result.errors)
            } catch (e: Exception) {
                errors.add("Failed to collect from ${module.name}: ${e.message}")
            }
        }

        onComplete(CollectionResult(allTests, errors))
    }

    private fun runPytestCollect(
        pythonPath: String,
        workDir: String,
        indicator: ProgressIndicator,
    ): ModuleCollectionResult {
        val commandLine = GeneralCommandLine(
            pythonPath, "-m", "pytest",
            "--collect-only", "-q",
            "--no-header",
        ).withWorkDirectory(workDir)
            .withEnvironment("PYTHONDONTWRITEBYTECODE", "1")

        val handler = OSProcessHandler(commandLine)
        val output = CapturingProcessAdapter()
        handler.addProcessListener(output)
        handler.startNotify()

        // Wait with cancellation support
        while (!handler.waitFor(500)) {
            if (indicator.isCanceled) {
                handler.destroyProcess()
                return ModuleCollectionResult(emptyList(), listOf("Cancelled"))
            }
        }

        val stdout = output.output.stdout
        val stderr = output.output.stderr
        val exitCode = output.output.exitCode

        // Exit code 5 = no tests collected (not an error)
        // Exit code 0 = success
        // Exit code 2 = usage error / interrupted
        val errors = when {
            exitCode == 0 || exitCode == 5 -> emptyList()
            else -> listOf("pytest exited with code $exitCode: $stderr")
        }

        val items = PytestOutputParser.parseQuietOutput(stdout, workDir)
        return ModuleCollectionResult(items, errors)
    }
}

data class CollectionResult(
    val items: List<RawCollectedItem>,
    val errors: List<String>,
) {
    companion object {
        fun empty(reason: String) = CollectionResult(emptyList(), listOf(reason))
    }
}

data class ModuleCollectionResult(
    val items: List<RawCollectedItem>,
    val errors: List<String>,
)

data class RawCollectedItem(
    val nodeId: String,
    val type: ItemType, // MODULE, CLASS, FUNCTION, FIXTURE
)

enum class ItemType { MODULE, CLASS, FUNCTION, FIXTURE }
```

#### Alternative: JSON Report Plugin

For richer structured output, use `pytest-json-report` or `pytest --collect-only` with a custom conftest plugin that outputs JSON:

```python
# conftest_collector.py — injected via --override-ini or -p
import pytest, json, sys

def pytest_collection_finish(session):
    """Hook that fires after collection; emits JSON to stdout."""
    data = {"tests": [], "fixtures": {}}

    # Collect test items
    for item in session.items:
        fixtures = list(item.fixturenames)
        data["tests"].append({
            "nodeid": item.nodeid,
            "module": item.module.__name__,
            "cls": item.cls.__name__ if item.cls else None,
            "name": item.name,
            "fixtures": fixtures,
        })

    # Collect fixture definitions from the fixture manager
    fm = session._fixturemanager
    for name, fixturedefs in fm._arg2fixturedefs.items():
        for fdef in fixturedefs:
            data["fixtures"][name] = {
                "name": name,
                "scope": fdef.scope,
                "baseid": fdef.baseid,
                "func_name": fdef.func.__name__,
                "module": fdef.func.__module__,
                "argnames": list(fdef.argnames),
                "autouse": fdef.has_location,  # approximate
            }

    # Write to a temp file or stdout marker
    marker = "===PYTEST_COLLECTION_JSON==="
    print(f"{marker}{json.dumps(data)}{marker}", file=sys.stderr)
```

Then in the collector task:

```kotlin
val commandLine = GeneralCommandLine(
    pythonPath, "-m", "pytest",
    "--collect-only", "-q",
    "-p", "conftest_collector",  // or use --override-ini
    "--no-header",
).withWorkDirectory(workDir)
```

---

### 5. Parsing Collector Output

```kotlin
package com.example.pytestexplorer.collection

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Parses pytest --collect-only output in both quiet and JSON formats.
 */
object PytestOutputParser {

    private val QUIET_LINE_REGEX = Regex(
        """^(?<path>[^:]+)::(?:(?<cls>[^:]+)::)?(?<func>\S+)(?:\[(?<param>[^\]]+)])?$"""
    )

    /**
     * Parses quiet (`-q`) output where each line is a node ID like:
     *   tests/test_auth.py::TestLogin::test_success
     *   tests/test_auth.py::test_standalone[param1]
     */
    fun parseQuietOutput(stdout: String, workDir: String): List<RawCollectedItem> {
        return stdout.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("=") && !it.startsWith("-") }
            .mapNotNull { line ->
                QUIET_LINE_REGEX.matchEntire(line.trim())?.let { match ->
                    RawCollectedItem(
                        nodeId = line.trim(),
                        type = ItemType.FUNCTION,
                    )
                }
            }
            .toList()
    }

    /**
     * Parses the JSON output from the custom conftest collector plugin.
     * Extracts both tests and fixtures with full dependency information.
     */
    fun parseJsonOutput(stderr: String): CollectionJsonData? {
        val marker = "===PYTEST_COLLECTION_JSON==="
        val startIdx = stderr.indexOf(marker)
        val endIdx = stderr.lastIndexOf(marker)
        if (startIdx == -1 || endIdx <= startIdx) return null

        val json = stderr.substring(startIdx + marker.length, endIdx)
        return try {
            Gson().fromJson(json, CollectionJsonData::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

data class CollectionJsonData(
    val tests: List<JsonTestItem>,
    val fixtures: Map<String, JsonFixtureItem>,
)

data class JsonTestItem(
    val nodeid: String,
    val module: String,
    val cls: String?,
    val name: String,
    val fixtures: List<String>,
)

data class JsonFixtureItem(
    val name: String,
    val scope: String,
    val baseid: String,
    val func_name: String,
    val module: String,
    val argnames: List<String>,
    val autouse: Boolean,
)
```

---

### 6. PSI Matching & Navigation

This is the critical bridge between collected pytest data and the IDE's code model.

```kotlin
package com.example.pytestexplorer.psi

import com.example.pytestexplorer.model.CollectedTest
import com.example.pytestexplorer.model.CollectedFixture
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/**
 * Resolves collected test/fixture data to PSI elements.
 *
 * Design notes:
 * - All PSI access is wrapped in ReadAction for thread safety.
 * - Returns SmartPsiElementPointer to survive PSI tree modifications.
 * - Falls back gracefully when PSI elements cannot be found (file deleted, renamed, etc.).
 *
 * Leverages existing IntelliJ Python plugin infrastructure:
 * - [com.jetbrains.python.testing.pyTestFixtures.PyTestFixture] for fixture resolution
 * - [com.jetbrains.python.psi.stubs.PyFunctionNameIndex] for fast function lookup
 */
object PytestPsiResolver {

    /**
     * Resolves a collected test to its PSI function/method.
     *
     * Node ID format: "path/to/test.py::ClassName::test_method" or "path/to/test.py::test_func"
     */
    fun resolveTest(
        project: Project,
        test: CollectedTest,
    ): SmartPsiElementPointer<PyFunction>? = ReadAction.compute<SmartPsiElementPointer<PyFunction>?, Throwable> {
        val basePath = project.basePath ?: return@compute null
        val vFile = LocalFileSystem.getInstance()
            .findFileByPath("$basePath/${test.modulePath}") ?: return@compute null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) as? PyFile
            ?: return@compute null

        val function = if (test.className != null) {
            // Class method: find class, then method
            val pyClass = psiFile.findTopLevelClass(test.className)
                ?: PsiTreeUtil.findChildrenOfType(psiFile, PyClass::class.java)
                    .firstOrNull { it.name == test.className }
            pyClass?.findMethodByName(test.functionName, false, null)
        } else {
            // Top-level function
            psiFile.findTopLevelFunction(test.functionName)
        }

        function?.let {
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
        }
    }

    /**
     * Resolves a collected fixture to its defining PSI function.
     *
     * Strategy:
     * 1. Try direct file path resolution (from JSON collector data).
     * 2. Fall back to PyFunctionNameIndex stub search.
     * 3. Use existing PyTestFixture infrastructure for conftest.py traversal.
     */
    fun resolveFixture(
        project: Project,
        fixture: CollectedFixture,
    ): SmartPsiElementPointer<PyFunction>? = ReadAction.compute<SmartPsiElementPointer<PyFunction>?, Throwable> {
        val basePath = project.basePath ?: return@compute null

        // Strategy 1: Direct file resolution
        val vFile = LocalFileSystem.getInstance()
            .findFileByPath("$basePath/${fixture.definedIn}") ?: return@compute null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) as? PyFile
            ?: return@compute null

        val function = psiFile.findTopLevelFunction(fixture.functionName)
            ?: PsiTreeUtil.findChildrenOfType(psiFile, PyFunction::class.java)
                .firstOrNull { it.name == fixture.functionName }

        function?.let {
            SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it)
        }
    }

    /**
     * Extracts fixture dependencies directly from PSI by inspecting function parameters.
     * This is a PSI-only approach that doesn't require running pytest.
     *
     * Useful for:
     * - Real-time updates as the user types
     * - Validating collected data against actual code
     */
    fun extractFixtureDepsFromPsi(function: PyFunction): List<String> {
        val paramList = function.parameterList
        return paramList.parameters
            .filter { it.name != "self" && it.name != "request" }
            .mapNotNull { it.name }
    }
}
```

#### Leveraging Existing IntelliJ Fixture Resolution

The IntelliJ Python plugin already has robust fixture resolution in `com.jetbrains.python.testing.pyTestFixtures`. Use it:

```kotlin
import com.jetbrains.python.testing.pyTestFixtures.getFixtures
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixture as IjPyTestFixture

/**
 * Uses IntelliJ's built-in fixture resolution as a complement to pytest collection.
 * This provides real-time fixture data without running pytest.
 */
fun getFixturesFromPsi(
    module: com.intellij.openapi.module.Module,
    testFunction: PyFunction,
    typeEvalContext: TypeEvalContext,
): List<IjPyTestFixture> {
    return getFixtures(module, testFunction, typeEvalContext)
}
```

---

### 7. Indexing & Caching

#### File-Based Index

```kotlin
package com.example.pytestexplorer.index

import com.example.pytestexplorer.model.CollectedTest
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

/**
 * FileBasedIndex that stores pytest collection metadata per test file.
 *
 * Key: test file path (relative)
 * Value: serialized list of CollectedTest items
 *
 * This index enables:
 * - Fast lookup of all tests in a file without re-running pytest
 * - Incremental updates when individual files change
 * - Survival across IDE restarts
 *
 * The index is populated by [PytestCollectionIndexer] which is triggered
 * after each successful pytest collection run.
 */
class PytestCollectionIndex : FileBasedIndexExtension<String, List<String>>() {

    companion object {
        val NAME = ID.create<String, List<String>>("pytest.collection.index")
    }

    override fun getName(): ID<String, List<String>> = NAME

    override fun getIndexer(): DataIndexer<String, List<String>, FileContent> {
        return DataIndexer { inputData ->
            // Index Python test files by scanning for test functions/classes
            val file = inputData.file
            if (!isTestFile(file)) return@DataIndexer emptyMap()

            // Store function names as a lightweight index
            // Full collection data is stored in the project service
            val content = inputData.contentAsText.toString()
            val testNames = TEST_FUNCTION_REGEX.findAll(content)
                .map { it.groupValues[1] }
                .toList()

            if (testNames.isNotEmpty()) {
                mapOf(file.path to testNames)
            } else {
                emptyMap()
            }
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<String>> =
        object : DataExternalizer<List<String>> {
            override fun save(out: DataOutput, value: List<String>) {
                out.writeInt(value.size)
                value.forEach { out.writeUTF(it) }
            }

            override fun read(input: DataInput): List<String> {
                val size = input.readInt()
                return (0 until size).map { input.readUTF() }
            }
        }

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileBasedIndex.InputFilter { file ->
            file.extension == "py" && isTestFile(file)
        }

    override fun dependsOnFileContent(): Boolean = true
    override fun getVersion(): Int = 1

    private fun isTestFile(file: VirtualFile): Boolean {
        val name = file.name
        return name.startsWith("test_") || name.endsWith("_test.py") || name == "conftest.py"
    }

    companion object {
        private val TEST_FUNCTION_REGEX = Regex("""def\s+(test_\w+)\s*\(""")
    }
}
```

#### Project-Level Cache Service

```kotlin
package com.example.pytestexplorer.model

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level service that holds the current collection snapshot and
 * notifies listeners of updates.
 *
 * Persists the last collection result across IDE restarts via PersistentStateComponent.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "PytestExplorerState",
    storages = [Storage("pytestExplorer.xml")]
)
class PytestExplorerService(
    private val project: Project,
) : PersistentStateComponent<PytestExplorerService.State> {

    private val currentSnapshot = AtomicReference<CollectionSnapshot?>(null)
    private val listeners = mutableListOf<CollectionListener>()

    data class State(
        var lastCollectionTimestamp: Long = 0,
        var serializedTests: String = "", // JSON-serialized for persistence
        var serializedFixtures: String = "",
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
        // Restore snapshot from persisted state on load
        restoreFromState()
    }

    fun getSnapshot(): CollectionSnapshot? = currentSnapshot.get()

    fun updateSnapshot(snapshot: CollectionSnapshot) {
        currentSnapshot.set(snapshot)
        myState.lastCollectionTimestamp = snapshot.timestamp
        // Serialize for persistence
        myState.serializedTests = serializeTests(snapshot.tests)
        myState.serializedFixtures = serializeFixtures(snapshot.fixtures)
        // Notify listeners on EDT
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onCollectionUpdated(snapshot) }
        }
    }

    fun addListener(listener: CollectionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CollectionListener) {
        listeners.remove(listener)
    }

    private fun restoreFromState() {
        if (myState.serializedTests.isNotEmpty()) {
            val tests = deserializeTests(myState.serializedTests)
            val fixtures = deserializeFixtures(myState.serializedFixtures)
            currentSnapshot.set(CollectionSnapshot(
                timestamp = myState.lastCollectionTimestamp,
                tests = tests,
                fixtures = fixtures,
                errors = emptyList(),
            ))
        }
    }

    // Serialization helpers (use Gson or kotlinx.serialization)
    private fun serializeTests(tests: List<CollectedTest>): String = /* ... */ ""
    private fun deserializeTests(json: String): List<CollectedTest> = /* ... */ emptyList()
    private fun serializeFixtures(fixtures: List<CollectedFixture>): String = /* ... */ ""
    private fun deserializeFixtures(json: String): List<CollectedFixture> = /* ... */ emptyList()

    companion object {
        fun getInstance(project: Project): PytestExplorerService =
            project.service<PytestExplorerService>()
    }
}

fun interface CollectionListener {
    fun onCollectionUpdated(snapshot: CollectionSnapshot)
}
```

---

### 8. Tool Window UI

#### Tool Window Factory

```kotlin
package com.example.pytestexplorer.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class PytestExplorerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PytestExplorerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // Only show for projects with Python modules using pytest
        return true // Refine with PyTestDiscoveryUtil check
    }
}
```

#### Main Panel with Split View

```kotlin
package com.example.pytestexplorer.ui

import com.example.pytestexplorer.collection.PytestCollectorTask
import com.example.pytestexplorer.model.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Main panel for the Pytest Explorer tool window.
 *
 * Layout:
 * ┌─────────────────────────────────────────┐
 * │ [Toolbar: Refresh | Filter | Group By]  │
 * ├──────────────────┬──────────────────────┤
 * │   Test Tree      │   Fixture Details    │
 * │   (left split)   │   (right split)      │
 * │                  │                      │
 * │  ▸ test_auth.py  │  Fixture: db_session │
 * │    ▸ TestLogin   │  Scope: function     │
 * │      test_ok     │  Defined: conftest   │
 * │      test_fail   │  Dependencies:       │
 * │  ▸ test_api.py   │    ▸ db_engine      │
 * │                  │    ▸ settings        │
 * └──────────────────┴──────────────────────┘
 */
class PytestExplorerPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val testTree = com.intellij.ui.treeStructure.Tree()
    private val fixtureDetailPanel = FixtureDetailPanel(project)
    private val service = PytestExplorerService.getInstance(project)

    init {
        // Toolbar
        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)

        // Split pane: test tree (left) + fixture details (right)
        val splitPane = com.intellij.ui.JBSplitter(false, 0.5f).apply {
            firstComponent = JBScrollPane(testTree)
            secondComponent = fixtureDetailPanel
        }
        add(splitPane, BorderLayout.CENTER)

        // Status bar
        val statusLabel = JLabel("Ready")
        statusLabel.border = JBUI.Borders.empty(2, 5)
        add(statusLabel, BorderLayout.SOUTH)

        // Configure tree
        setupTree()

        // Listen for collection updates
        service.addListener { snapshot ->
            SwingUtilities.invokeLater { updateTree(snapshot) }
        }

        // Load existing data
        service.getSnapshot()?.let { updateTree(it) }
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            add(FilterByModuleAction())
            add(GroupByAction())
            addSeparator()
            add(ExpandAllAction())
            add(CollapseAllAction())
        }
        return ActionManager.getInstance()
            .createActionToolbar("PytestExplorer", group, true)
    }

    private fun setupTree() {
        testTree.isRootVisible = false
        testTree.cellRenderer = PytestTreeCellRenderer()

        // Double-click to navigate to source
        testTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val node = testTree.lastSelectedPathComponent as? DefaultMutableTreeNode
                    val userObj = node?.userObject
                    when (userObj) {
                        is TestTreeNode -> navigateToTest(userObj)
                        is FixtureTreeNode -> navigateToFixture(userObj)
                    }
                }
            }
        })

        // Selection listener to update fixture detail panel
        testTree.addTreeSelectionListener { e ->
            val node = testTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObj = node?.userObject
            if (userObj is TestTreeNode) {
                fixtureDetailPanel.showFixturesFor(userObj.test)
            }
        }
    }

    private fun updateTree(snapshot: CollectionSnapshot) {
        val root = DefaultMutableTreeNode("Tests")

        // Group by module
        val byModule = snapshot.tests.groupBy { it.modulePath }
        for ((modulePath, tests) in byModule.toSortedMap()) {
            val moduleNode = DefaultMutableTreeNode(ModuleTreeNode(modulePath))

            // Group by class within module
            val byClass = tests.groupBy { it.className }
            for ((className, classTests) in byClass) {
                val parent = if (className != null) {
                    val classNode = DefaultMutableTreeNode(ClassTreeNode(className))
                    moduleNode.add(classNode)
                    classNode
                } else {
                    moduleNode
                }

                for (test in classTests) {
                    parent.add(DefaultMutableTreeNode(TestTreeNode(test)))
                }
            }

            root.add(moduleNode)
        }

        testTree.model = javax.swing.tree.DefaultTreeModel(root)
    }

    private fun navigateToTest(node: TestTreeNode) {
        ReadAction.run<Throwable> {
            val pointer = com.example.pytestexplorer.psi.PytestPsiResolver
                .resolveTest(project, node.test)
            pointer?.element?.let {
                com.intellij.pom.Navigatable::class.java.cast(it).navigate(true)
            }
        }
    }

    private fun navigateToFixture(node: FixtureTreeNode) {
        ReadAction.run<Throwable> {
            val pointer = com.example.pytestexplorer.psi.PytestPsiResolver
                .resolveFixture(project, node.fixture)
            pointer?.element?.let {
                com.intellij.pom.Navigatable::class.java.cast(it).navigate(true)
            }
        }
    }

    // --- Action classes ---

    private inner class RefreshAction : AnAction("Refresh", "Re-collect pytest tests", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            PytestCollectorTask(project) { result ->
                val snapshot = buildSnapshot(result)
                service.updateSnapshot(snapshot)
            }.queue()
        }
    }

    private inner class FilterByModuleAction : AnAction("Filter", "Filter by module", AllIcons.General.Filter)  {
        override fun actionPerformed(e: AnActionEvent) { /* Show filter popup */ }
    }

    private inner class GroupByAction : AnAction("Group By", "Change grouping", AllIcons.Actions.GroupBy) {
        override fun actionPerformed(e: AnActionEvent) { /* Toggle grouping mode */ }
    }

    private inner class ExpandAllAction : AnAction("Expand All", null, AllIcons.Actions.Expandall) {
        override fun actionPerformed(e: AnActionEvent) {
            com.intellij.util.ui.tree.TreeUtil.expandAll(testTree)
        }
    }

    private inner class CollapseAllAction : AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
        override fun actionPerformed(e: AnActionEvent) {
            com.intellij.util.ui.tree.TreeUtil.collapseAll(testTree, 1)
        }
    }
}

// Tree node types
data class ModuleTreeNode(val path: String)
data class ClassTreeNode(val name: String)
data class TestTreeNode(val test: CollectedTest)
data class FixtureTreeNode(val fixture: CollectedFixture)
```

#### Fixture Detail Panel (Dependency Tree)

```kotlin
package com.example.pytestexplorer.ui

import com.example.pytestexplorer.model.*
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Right-side panel showing fixture dependency tree for the selected test.
 *
 * Displays:
 * - Direct fixtures used by the test
 * - Recursive fixture dependencies as a tree
 * - Fixture metadata (scope, autouse, source file)
 */
class FixtureDetailPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val titleLabel = JBLabel("Select a test to view fixtures")
    private val fixtureTree = Tree()

    init {
        border = JBUI.Borders.empty(5)
        add(titleLabel, BorderLayout.NORTH)
        add(JBScrollPane(fixtureTree), BorderLayout.CENTER)
        fixtureTree.isRootVisible = false
    }

    fun showFixturesFor(test: CollectedTest) {
        titleLabel.text = "Fixtures for ${test.functionName}"

        val service = PytestExplorerService.getInstance(project)
        val snapshot = service.getSnapshot() ?: return
        val fixtureMap = snapshot.fixtures.associateBy { it.name }
        val graph = FixtureDependencyGraph(fixtureMap)

        val root = DefaultMutableTreeNode("Fixtures")
        for (fixtureName in test.fixtures) {
            val fixture = fixtureMap[fixtureName]
            val node = DefaultMutableTreeNode(
                FixtureDisplayNode(fixtureName, fixture?.scope ?: "?", fixture?.definedIn ?: "?")
            )
            // Add recursive dependencies
            if (fixture != null) {
                addDependencies(node, fixture, fixtureMap, mutableSetOf())
            }
            root.add(node)
        }

        fixtureTree.model = DefaultTreeModel(root)
        // Expand first two levels
        for (i in 0 until fixtureTree.rowCount.coerceAtMost(20)) {
            fixtureTree.expandRow(i)
        }
    }

    private fun addDependencies(
        parentNode: DefaultMutableTreeNode,
        fixture: CollectedFixture,
        fixtureMap: Map<String, CollectedFixture>,
        visited: MutableSet<String>,
    ) {
        for (depName in fixture.dependencies) {
            if (!visited.add(depName)) {
                parentNode.add(DefaultMutableTreeNode(
                    FixtureDisplayNode(depName, "?", "⟳ circular")
                ))
                continue
            }
            val dep = fixtureMap[depName]
            val depNode = DefaultMutableTreeNode(
                FixtureDisplayNode(depName, dep?.scope ?: "?", dep?.definedIn ?: "?")
            )
            if (dep != null) {
                addDependencies(depNode, dep, fixtureMap, visited)
            }
            parentNode.add(depNode)
        }
    }
}

data class FixtureDisplayNode(
    val name: String,
    val scope: String,
    val definedIn: String,
) {
    override fun toString(): String = "$name [$scope] — $definedIn"
}
```

#### Custom Tree Cell Renderer

```kotlin
package com.example.pytestexplorer.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class PytestTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject ?: return

        when (node) {
            is ModuleTreeNode -> {
                icon = AllIcons.Nodes.Module
                append(node.path, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
            is ClassTreeNode -> {
                icon = AllIcons.Nodes.Class
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            is TestTreeNode -> {
                icon = AllIcons.Nodes.Test
                append(node.test.functionName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (node.test.fixtures.isNotEmpty()) {
                    append("  (${node.test.fixtures.size} fixtures)",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
            is FixtureDisplayNode -> {
                icon = AllIcons.Nodes.Function
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append("  [${node.scope}]", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
        }
    }
}
```

---

### 9. Trigger Strategies (Events & Scheduling)

```kotlin
package com.example.pytestexplorer

import com.example.pytestexplorer.collection.PytestCollectorTask
import com.example.pytestexplorer.model.PytestExplorerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection

/**
 * Startup activity that:
 * 1. Triggers initial collection after project opens.
 * 2. Registers file-change listeners for automatic re-collection.
 * 3. Sets up debounced re-collection to avoid excessive pytest runs.
 */
class PytestExplorerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Initial collection (delayed to let indexing finish)
        val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
        alarm.addRequest({ triggerCollection(project) }, 5000)

        // File change listener with debouncing
        val connection: MessageBusConnection = project.messageBus.connect()
        val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val hasTestFileChanges = events.any { event ->
                    val name = event.file?.name ?: return@any false
                    val ext = event.file?.extension ?: return@any false
                    ext == "py" && (
                        name.startsWith("test_") ||
                        name.endsWith("_test.py") ||
                        name == "conftest.py"
                    )
                }

                if (hasTestFileChanges) {
                    // Debounce: wait 3 seconds after last change before re-collecting
                    debounceAlarm.cancelAllRequests()
                    debounceAlarm.addRequest({ triggerCollection(project) }, 3000)
                }
            }
        })
    }

    private fun triggerCollection(project: Project) {
        if (project.isDisposed) return
        PytestCollectorTask(project) { result ->
            val snapshot = buildSnapshot(result)
            PytestExplorerService.getInstance(project).updateSnapshot(snapshot)
        }.queue()
    }
}
```

#### Additional Trigger Points

```kotlin
/**
 * Additional events that should trigger re-collection:
 *
 * 1. VCS operations (checkout, pull, merge):
 *    Subscribe to GitRepositoryChangeListener or VcsListener.
 *
 * 2. Python SDK changes:
 *    Subscribe to ProjectJdkTable.JDK_TABLE_TOPIC.
 *
 * 3. Run configuration execution:
 *    Subscribe to ExecutionManager.EXECUTION_TOPIC to re-collect
 *    after test runs complete (tests may have been added/removed).
 *
 * 4. Manual refresh:
 *    Via the toolbar Refresh button in the tool window.
 *
 * 5. Focus gained:
 *    Optionally re-collect when the IDE window regains focus
 *    (user may have edited files externally).
 */

// Example: VCS listener
// project.messageBus.connect().subscribe(
//     GitRepository.GIT_REPO_CHANGE,
//     GitRepositoryChangeListener { triggerCollection(project) }
// )

// Example: After test run
// project.messageBus.connect().subscribe(
//     ExecutionManager.EXECUTION_TOPIC,
//     object : ExecutionListener {
//         override fun processTerminated(
//             executorId: String,
//             env: ExecutionEnvironment,
//             handler: ProcessHandler,
//             exitCode: Int
//         ) {
//             if (env.runProfile is PyTestRunConfiguration) {
//                 debounceAlarm.cancelAllRequests()
//                 debounceAlarm.addRequest({ triggerCollection(project) }, 1000)
//             }
//         }
//     }
// )
```

---

### 10. Testing Strategy

#### Unit Tests

```kotlin
package com.example.pytestexplorer.test

import com.example.pytestexplorer.collection.PytestOutputParser
import com.example.pytestexplorer.model.FixtureDependencyGraph
import com.example.pytestexplorer.model.CollectedFixture
import org.junit.Assert.*
import org.junit.Test

class PytestOutputParserTest {

    @Test
    fun `parse quiet output with class`() {
        val output = """
            tests/test_auth.py::TestLogin::test_success
            tests/test_auth.py::TestLogin::test_failure
            tests/test_api.py::test_health_check
        """.trimIndent()

        val items = PytestOutputParser.parseQuietOutput(output, "/project")
        assertEquals(3, items.size)
        assertEquals("tests/test_auth.py::TestLogin::test_success", items[0].nodeId)
    }

    @Test
    fun `parse quiet output with parametrize`() {
        val output = "tests/test_math.py::test_add[1-2-3]"
        val items = PytestOutputParser.parseQuietOutput(output, "/project")
        assertEquals(1, items.size)
    }

    @Test
    fun `parse JSON output`() {
        val json = """===PYTEST_COLLECTION_JSON==={"tests":[{"nodeid":"t.py::test_x","module":"t","cls":null,"name":"test_x","fixtures":["db"]}],"fixtures":{"db":{"name":"db","scope":"function","baseid":"","func_name":"db","module":"conftest","argnames":[],"autouse":false}}}===PYTEST_COLLECTION_JSON==="""
        val result = PytestOutputParser.parseJsonOutput(json)
        assertNotNull(result)
        assertEquals(1, result!!.tests.size)
        assertEquals("db", result.tests[0].fixtures[0])
        assertEquals("function", result.fixtures["db"]!!.scope)
    }
}

class FixtureDependencyGraphTest {

    @Test
    fun `transitive dependencies`() {
        val fixtures = mapOf(
            "db_session" to CollectedFixture("db_session", "function", "conftest.py", "db_session", listOf("db_engine")),
            "db_engine" to CollectedFixture("db_engine", "session", "conftest.py", "db_engine", listOf("settings")),
            "settings" to CollectedFixture("settings", "session", "conftest.py", "settings", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val deps = graph.dependenciesOf("db_session")
        assertEquals(3, deps.size) // settings, db_engine, db_session
        assertEquals("settings", deps[0].name) // leaf first
    }

    @Test
    fun `reverse dependents`() {
        val fixtures = mapOf(
            "db_session" to CollectedFixture("db_session", "function", "conftest.py", "db_session", listOf("db_engine")),
            "db_engine" to CollectedFixture("db_engine", "session", "conftest.py", "db_engine", listOf("settings")),
            "settings" to CollectedFixture("settings", "session", "conftest.py", "settings", emptyList()),
        )
        val graph = FixtureDependencyGraph(fixtures)
        val dependents = graph.dependentsOf("settings")
        assertTrue(dependents.any { it.name == "db_engine" })
        assertTrue(dependents.any { it.name == "db_session" })
    }
}
```

#### Integration Tests (IntelliJ Test Framework)

```kotlin
package com.example.pytestexplorer.test

import com.example.pytestexplorer.psi.PytestPsiResolver
import com.example.pytestexplorer.model.CollectedTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration tests using IntelliJ's test framework.
 * These tests run inside a headless IDE instance with a real PSI tree.
 */
class PytestPsiResolverTest : BasePlatformTestCase() {

    fun `test resolve top-level test function`() {
        myFixture.configureByText("test_example.py", """
            def test_hello():
                assert True
        """.trimIndent())

        val test = CollectedTest(
            nodeId = "test_example.py::test_hello",
            modulePath = "test_example.py",
            className = null,
            functionName = "test_hello",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNotNull(pointer)
        assertEquals("test_hello", pointer!!.element?.name)
    }

    fun `test resolve class method`() {
        myFixture.configureByText("test_auth.py", """
            class TestLogin:
                def test_success(self):
                    pass
        """.trimIndent())

        val test = CollectedTest(
            nodeId = "test_auth.py::TestLogin::test_success",
            modulePath = "test_auth.py",
            className = "TestLogin",
            functionName = "test_success",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNotNull(pointer)
        assertEquals("test_success", pointer!!.element?.name)
    }

    fun `test extract fixture deps from PSI`() {
        myFixture.configureByText("test_db.py", """
            def test_query(db_session, mock_user):
                pass
        """.trimIndent())

        val func = myFixture.findElementByText("test_query", com.jetbrains.python.psi.PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals(listOf("db_session", "mock_user"), deps)
    }
}
```

---

### 11. Performance & Scalability

#### Guidelines

| Concern | Strategy |
|---------|----------|
| **Large projects (10k+ tests)** | Run `pytest --collect-only` per-module, not project-wide. Use `--ignore` to skip irrelevant directories. |
| **Frequent file saves** | Debounce re-collection (3–5 second delay). Only re-collect modules whose files changed. |
| **PSI resolution cost** | Resolve lazily — only when a node is expanded or selected. Cache `SmartPsiElementPointer` results. |
| **Memory** | Store only node IDs and fixture names in the index. Full `CollectedTest` objects live in the service, evicted on project close. |
| **Startup** | Restore last snapshot from `PersistentStateComponent` immediately. Run fresh collection in background. |
| **Thread safety** | Use `AtomicReference` for snapshot. All PSI access via `ReadAction`. UI updates via `invokeLater`. |

#### Incremental Collection

```kotlin
/**
 * For incremental updates, track which files changed and only re-collect those modules.
 *
 * Strategy:
 * 1. On file change, record the changed file paths.
 * 2. Map changed files to their pytest root directories.
 * 3. Run `pytest --collect-only <changed_dirs>` instead of full project.
 * 4. Merge results: replace entries for changed modules, keep others.
 */
fun incrementalCollect(
    changedFiles: Set<String>,
    existingSnapshot: CollectionSnapshot,
): CollectionSnapshot {
    // Determine affected test directories
    val affectedDirs = changedFiles
        .map { it.substringBeforeLast("/") }
        .toSet()

    // Run collection only for affected directories
    // ... (pass dirs as pytest arguments)

    // Merge: keep tests from unchanged modules, replace changed ones
    val unchangedTests = existingSnapshot.tests
        .filter { test -> affectedDirs.none { test.modulePath.startsWith(it) } }

    // Return merged snapshot
    return existingSnapshot.copy(
        tests = unchangedTests + newlyCollectedTests,
        timestamp = System.currentTimeMillis(),
    )
}
```

---

### 12. Extension Points & Future Work

#### Custom Extension Point for Fixture Providers

```xml
<!-- plugin.xml -->
<extensionPoints>
  <extensionPoint name="fixtureProvider"
                  interface="com.example.pytestexplorer.api.FixtureProvider"
                  dynamic="true"/>
</extensionPoints>
```

```kotlin
package com.example.pytestexplorer.api

import com.example.pytestexplorer.model.CollectedFixture
import com.intellij.openapi.project.Project

/**
 * Extension point for third-party plugins to contribute additional fixture sources.
 * E.g., pytest-django fixtures, factory_boy, etc.
 */
interface FixtureProvider {
    fun getAdditionalFixtures(project: Project): List<CollectedFixture>
}
```

#### Future Enhancements

1. **Fixture usage search:** "Find all tests using fixture X" via a custom `UsageSearcher`.
2. **Fixture graph visualization:** Render the dependency graph using JCEF or a Swing graph library.
3. **Inline hints:** Show fixture scope/source as inlay hints in the editor via `InlayHintsProvider`.
4. **Conftest.py awareness:** Highlight which conftest.py provides each fixture, with navigation.
5. **Fixture conflict detection:** Warn when multiple fixtures with the same name exist at different scopes.
6. **Run test from tree:** Right-click context menu to run a test directly from the explorer.
7. **Diff collection results:** Compare two snapshots to see added/removed tests after a branch switch.
8. **pytest plugin awareness:** Detect and handle fixtures from popular plugins (pytest-django, pytest-asyncio, pytest-mock) with specialized icons and metadata.

---

### Quick Reference: Key IntelliJ Platform APIs Used

| API | Purpose |
|-----|---------|
| `ToolWindowFactory` | Register and create the tool window |
| `Task.Backgroundable` | Run pytest in background thread |
| `GeneralCommandLine` + `OSProcessHandler` | Execute external processes |
| `SmartPsiElementPointer` | Survive PSI tree modifications |
| `ReadAction` | Thread-safe PSI access |
| `FileBasedIndex` | Persistent index of test metadata |
| `PersistentStateComponent` | Save/restore state across restarts |
| `BulkFileListener` | React to file system changes |
| `Alarm` | Debounce repeated triggers |
| `ColoredTreeCellRenderer` | Custom tree node rendering |
| `JBSplitter` | Split pane layout |
| `MessageBus` | Decouple event producers/consumers |
| `PyFunction`, `PyClass`, `PyFile` | Python PSI types |
| `PyTestFixture` (existing) | Reuse IntelliJ's fixture resolution |
