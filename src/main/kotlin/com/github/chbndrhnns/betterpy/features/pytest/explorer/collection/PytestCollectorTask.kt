package com.github.chbndrhnns.betterpy.features.pytest.explorer.collection

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectionSnapshot
import com.github.chbndrhnns.betterpy.features.pytest.explorer.service.PytestExplorerService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.jetbrains.python.sdk.PythonSdkUtil
import java.io.File
import java.nio.file.Files

/**
 * Runs `pytest --collect-only -q` in the background and delivers parsed results
 * to [PytestExplorerService].
 *
 * Uses the project's configured Python SDK to find the correct pytest.
 * Injects `conftest_collector.py` via `-p` flag to get structured JSON output on stderr.
 * Parses both quiet stdout (test node IDs) and JSON stderr (tests + fixtures with metadata).
 */
class PytestCollectorTask(
    project: Project,
    private val onComplete: ((CollectionSnapshot) -> Unit)? = null,
) : Task.Backgroundable(project, "Collecting Pytest Tests", true), DumbAware {

    override fun run(indicator: ProgressIndicator) {
        indicator.text = "Discovering pytest tests and fixtures..."
        indicator.isIndeterminate = true

        PytestExplorerService.getInstance(project).notifyCollectionStarted()

        LOG.info("Starting pytest collection for project: ${project.name}")

        val pythonPath = findPythonPath() ?: run {
            LOG.info("No Python SDK configured for project")
            deliverResult(CollectionSnapshot.empty("No Python SDK configured"))
            return
        }

        val workDir = project.basePath ?: run {
            LOG.warn("Project has no base path")
            deliverResult(CollectionSnapshot.empty("Project has no base path"))
            return
        }

        val collectorScript = collectorScriptPath() ?: run {
            LOG.warn("Could not find conftest_collector.py script")
            deliverResult(CollectionSnapshot.empty("Collector script not found"))
            return
        }

        if (indicator.isCanceled) return

        try {
            LOG.debug("Running pytest collect: python=$pythonPath, workDir=$workDir, script=$collectorScript")
            val result = runPytestCollect(pythonPath, workDir, collectorScript, indicator)
            LOG.info("Collection complete: ${result.tests.size} tests, ${result.fixtures.size} fixtures, ${result.errors.size} errors")
            deliverResult(result)
        } catch (e: Exception) {
            LOG.warn("Failed to collect pytest tests", e)
            deliverResult(CollectionSnapshot.empty("Collection failed: ${e.message}"))
        }
    }

    private fun findPythonPath(): String? {
        val modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
            val sdk = PythonSdkUtil.findPythonSdk(module) ?: continue
            val path = sdk.homePath ?: continue
            return path
        }
        return null
    }

    private fun runPytestCollect(
        pythonPath: String,
        workDir: String,
        collectorScript: String,
        indicator: ProgressIndicator,
    ): CollectionSnapshot {
        val args = buildPytestArgs(collectorScript)
        val env = buildEnvironment(collectorScript)

        val commandLine = GeneralCommandLine(
            listOf(pythonPath, "-m", "pytest") + args,
        ).withWorkDirectory(workDir)

        env.forEach { (key, value) -> commandLine.withEnvironment(key, value) }

        val handler = CapturingProcessHandler(commandLine)
        // Use runProcess instead of runProcessWithProgressIndicator to keep the
        // progress bar indeterminate — the latter internally sets fraction which
        // makes the bar appear stuck near 100 %.
        val output = handler.runProcess(TIMEOUT_MS)

        val stdout = output.stdout
        val stderr = output.stderr
        val exitCode = output.exitCode

        LOG.debug("pytest exited with code $exitCode (stdout=${stdout.length} chars, stderr=${stderr.length} chars)")
        if (LOG.isDebugEnabled && stderr.isNotEmpty()) {
            LOG.debug("pytest stderr (first 2000 chars): ${stderr.take(2000)}")
        }

        val errors = classifyExitCode(exitCode, stderr)
        val jsonData = PytestOutputParser.parseJsonOutput(stderr)

        if (errors.isNotEmpty()) {
            if (exitCode == -1) {
                LOG.error("pytest collection errors: $errors")
            } else if (jsonData != null) {
                LOG.info("pytest exited with code $exitCode but collection data was received")
            } else {
                LOG.warn("pytest collection problems: $errors")
            }
        }

        if (jsonData != null) {
            return buildSnapshotFromJson(jsonData, errors)
        }

        val quietItems = PytestOutputParser.parseQuietOutput(stdout, workDir)
        return CollectionSnapshot(
            timestamp = System.currentTimeMillis(),
            tests = groupParametrizedTests(quietItems.map { item ->
                val parts = item.nodeId.split("::")
                val path = parts[0]
                val lastPart = parts.last()
                val className = if (parts.size >= 3) parts[parts.size - 2].substringBefore("[") else null
                val funcName = lastPart.substringBefore("[")
                val paramId = if ("[" in lastPart) lastPart.substringAfter("[").removeSuffix("]") else null
                CollectedTest(
                    nodeId = item.nodeId,
                    modulePath = path,
                    className = className,
                    functionName = funcName,
                    fixtures = emptyList(),
                    parametrizeIds = listOfNotNull(paramId),
                )
            }),
            fixtures = emptyList(),
            errors = errors,
        )
    }

    private fun buildSnapshotFromJson(
        jsonData: CollectionJsonData,
        errors: List<String>,
    ): CollectionSnapshot {
        val tests = groupParametrizedTests(jsonData.tests.map { test ->
            val funcName = test.name.substringBefore("[")
            val paramId = if ("[" in test.name) test.name.substringAfter("[").removeSuffix("]") else null
            CollectedTest(
                nodeId = test.nodeid,
                modulePath = test.nodeid.substringBefore("::"),
                className = test.cls,
                functionName = funcName,
                fixtures = test.fixtures,
                parametrizeIds = listOfNotNull(paramId),
            )
        })
        val fixtures = jsonData.fixtures.values.map { fix ->
            CollectedFixture(
                name = fix.name,
                scope = fix.scope,
                definedIn = fix.baseid,
                functionName = fix.func_name,
                dependencies = fix.argnames,
                isAutouse = fix.autouse,
            )
        }
        return CollectionSnapshot(
            timestamp = System.currentTimeMillis(),
            tests = tests,
            fixtures = fixtures,
            errors = errors,
        )
    }

    private fun deliverResult(snapshot: CollectionSnapshot) {
        if (onComplete != null) {
            onComplete.invoke(snapshot)
        } else {
            PytestExplorerService.getInstance(project).updateSnapshot(snapshot)
        }
    }

    private fun groupParametrizedTests(tests: List<CollectedTest>): List<CollectedTest> {
        val result = mutableListOf<CollectedTest>()
        val grouped = mutableMapOf<String, MutableList<CollectedTest>>()
        for (test in tests) {
            if (test.parametrizeIds.isNotEmpty()) {
                val key = "${test.modulePath}::${test.className ?: ""}::${test.functionName}"
                grouped.getOrPut(key) { mutableListOf() }.add(test)
            } else {
                result.add(test)
            }
        }
        for ((_, group) in grouped) {
            val first = group.first()
            result.add(
                first.copy(
                    parametrizeIds = group.flatMap { it.parametrizeIds },
                )
            )
        }
        return result
    }

    companion object {
        private val LOG = Logger.getInstance(PytestCollectorTask::class.java)
        private const val TIMEOUT_MS = 60_000

        fun buildPytestArgs(collectorScriptPath: String): List<String> {
            return listOf(
                "--collect-only", "-q",
                "--no-header",
                "-p", "conftest_collector",
            )
        }

        fun buildEnvironment(collectorScriptPath: String): Map<String, String> {
            val scriptDir = File(collectorScriptPath).parent
            return mapOf(
                "PYTHONPATH" to scriptDir,
                "PYTHONDONTWRITEBYTECODE" to "1",
            )
        }

        fun classifyExitCode(exitCode: Int, stderr: String): List<String> {
            if (isPytestNotInstalled(stderr)) {
                return listOf("pytest is not installed: $stderr")
            }
            return when (exitCode) {
                0, 1, 2, 5 -> emptyList()
                -1 -> listOf("pytest failed to start (exit code -1): $stderr")
                else -> listOf("pytest exited with code $exitCode: $stderr")
            }
        }

        private fun isPytestNotInstalled(stderr: String): Boolean {
            return stderr.contains("No module named pytest") ||
                    stderr.contains("ModuleNotFoundError") && stderr.contains("pytest")
        }

        /**
         * Resolves the conftest_collector.py script to a real filesystem path.
         * When running from a JAR, the resource is inside the archive and cannot be
         * used directly as a PYTHONPATH entry. In that case we extract it to a temp file.
         */
        fun collectorScriptPath(): String? {
            val resource = PytestCollectorTask::class.java.getResource("/scripts/conftest_collector.py")
                ?: return null
            return try {
                // Works when running from exploded classes (dev / tests)
                File(resource.toURI()).absolutePath
            } catch (_: Exception) {
                // Resource is inside a JAR — extract to a temp directory
                try {
                    val tempDir = Files.createTempDirectory("betterpy-collector").toFile()
                    tempDir.deleteOnExit()
                    val tempScript = File(tempDir, "conftest_collector.py")
                    tempScript.deleteOnExit()
                    resource.openStream().use { input ->
                        tempScript.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    LOG.info("Extracted collector script from JAR to: ${tempScript.absolutePath}")
                    tempScript.absolutePath
                } catch (e: Exception) {
                    LOG.warn("Failed to extract collector script from JAR", e)
                    null
                }
            }
        }
    }
}
