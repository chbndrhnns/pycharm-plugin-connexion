package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IntroduceParameterObjectUiTest {

    init {
        di = DI {
            extend(di)
            bindSingleton<CIServer>(overrides = true) {
                object : CIServer by NoCIServer {
                    override fun reportTestFailure(
                        testName: String,
                        message: String,
                        details: String,
                        linkToLogs: String?,
                    ) {
                        if (shouldIgnoreIdeFailure(message, details)) {
                            println("Ignoring IDE failure for $testName: $message")
                            return
                        }
                        fail { "$testName fails: $message.\n$details" }
                    }
                }
            }
        }
    }

    @Test
    fun `introduce parameter object refactoring via UI`() {
        assumeUiIntegrationEnabled()
        preparePythonSdk()

        val projectPath = Path.of("src/integrationTest/resources/testProjects/simpleProject").toAbsolutePath()

        Starter.newContext(
            testName = "introduceParameterObject",
            testCase = TestCase(IdeProductProvider.PY, projectInfo = LocalProjectInfo(projectPath))
                .withVersion("2025.3.1"),
        ).apply {
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            Assumptions.assumeTrue(
                !pathToPlugin.isNullOrBlank(),
                "path.to.build.plugin must be set (run via :integrationTest)",
            )
            val pluginPath = Path.of(pathToPlugin)
            Assertions.assertTrue(
                Files.exists(pluginPath),
                "Plugin path does not exist: $pluginPath",
            )
            PluginConfigurator(this).installPluginFromPath(pluginPath)
        }.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(2.minutes)

            // Open the test file
            openFile(relativePath = "main.py")
            waitForIndicators(2.minutes)

            // Set up the uv Python interpreter by clicking the banner link
            val uvBannerText = "Set up a uv simple-project environment"
            val uvBannerPresent = runCatching {
                waitFor("uv banner to appear", 30.seconds) {
                    ui.x { byVisibleText(uvBannerText) }.present()
                }
            }.isSuccess
            if (uvBannerPresent) {
                println("UV banner present; SDK should be preconfigured via jdk.table.xml.")
            }

            // Place caret on the function definition line
            ui.codeEditor().goToLine(1)

            // Invoke the Introduce Parameter Object action
            waitFor("Introduce Parameter Object action to be enabled", 30.seconds) {
                runCatching {
                    invokeAction(
                        "com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction",
                        now = false,
                    )
                    true
                }.getOrDefault(false)
            }

            // Dialog is auto-accepted via test settings.

            // Verify the refactoring result
            waitFor("refactoring to apply", 30.seconds) {
                val text = ui.codeEditor().text
                text.contains("@dataclass") &&
                    text.contains("class ProcessOrderParams") &&
                    (text.contains("params: ProcessOrderParams") || text.contains("params:ProcessOrderParams"))
            }
            val resultText = ui.codeEditor().text
            Assertions.assertTrue(
                resultText.contains("@dataclass"),
                "Expected @dataclass decorator in result, but got:\n$resultText",
            )
            Assertions.assertTrue(
                resultText.contains("class ProcessOrderParams"),
                "Expected ProcessOrderParams class in result, but got:\n$resultText",
            )
            Assertions.assertTrue(
                resultText.contains("params: ProcessOrderParams") ||
                    resultText.contains("params:ProcessOrderParams"),
                "Expected function signature to use parameter object, but got:\n$resultText",
            )

            // Let indexing settle before IDE shutdown to avoid false CI failures.
            waitForIndicators(1.minutes)
        }
    }

    private fun assumeUiIntegrationEnabled() {
        val systemEnabled = System.getProperty("runIntegrationTests")
            ?.equals("true", ignoreCase = true) == true
        val envEnabled = System.getenv("RUN_INTEGRATION_TESTS")
            ?.equals("true", ignoreCase = true) == true
        Assumptions.assumeTrue(
            systemEnabled || envEnabled,
            "Set -DrunIntegrationTests=true or RUN_INTEGRATION_TESTS=true to enable UI integration tests.",
        )
    }

    private fun preparePythonSdk() {
        val projectRoot = Path.of("").toAbsolutePath()
        val testProject = projectRoot.resolve("src/integrationTest/resources/testProjects/simpleProject")
        val venvDir = testProject.resolve(".venv")
        val pythonBin = venvDir.resolve("bin/python")

        if (!Files.isExecutable(pythonBin)) {
            val pythonCmd = findPythonOnPath()
                ?: throw IllegalStateException("python3/python not found on PATH")
            runProcess(listOf(pythonCmd, "-m", "venv", venvDir.toString()))
        }

        val pythonVersion = runProcessOutput(
            listOf(
                pythonBin.toString(),
                "-c",
                "import sys; print('Python %d.%d.%d' % sys.version_info[:3])",
            ),
        ).trim()

        val platformType = readGradleProperty(projectRoot, "platformType")
        val platformVersion = readGradleProperty(projectRoot, "platformVersion")
        val configOptionsDir = projectRoot.resolve(
            "build/idea-sandbox/${platformType}-${platformVersion}/config_integrationTest/options",
        )
        Files.createDirectories(configOptionsDir)

        val sdkName = "Python (project venv)"
        val sdkUuid = UUID.randomUUID().toString()
        val jdkTableXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <application>
              <component name="ProjectJdkTable">
                <jdk version="2">
                  <name value="$sdkName" />
                  <type value="Python SDK" />
                  <version value="$pythonVersion" />
                  <homePath value="$pythonBin" />
                  <roots>
                    <classPath>
                      <root type="composite" />
                    </classPath>
                    <sourcePath>
                      <root type="composite" />
                    </sourcePath>
                  </roots>
                  <additional SDK_UUID="$sdkUuid">
                    <setting name="FLAVOR_ID" value="VirtualEnvSdkFlavor" />
                    <setting name="FLAVOR_DATA" value="{}" />
                  </additional>
                </jdk>
              </component>
            </application>
        """.trimIndent()

        Files.writeString(configOptionsDir.resolve("jdk.table.xml"), jdkTableXml)

        val settingsXml = """
            <application>
              <component name="PycharmDddToolkitSettings">
                <option name="parameterObject">
                  <ParameterObjectFeatureSettings>
                    <option name="enableParameterObjectRefactoring" value="true" />
                    <option name="autoAcceptRefactoringDialog" value="true" />
                  </ParameterObjectFeatureSettings>
                </option>
              </component>
            </application>
        """.trimIndent()

        Files.writeString(configOptionsDir.resolve("pycharm-ddd-toolkit.xml"), settingsXml)
    }

    private fun findPythonOnPath(): String? {
        val candidates = listOf("python3", "python")
        for (candidate in candidates) {
            val exitCode = runProcess(listOf(candidate, "--version"), allowFailure = true)
            if (exitCode == 0) {
                return candidate
            }
        }
        return null
    }

    private fun readGradleProperty(projectRoot: Path, key: String): String {
        val file = projectRoot.resolve("gradle.properties")
        Files.newBufferedReader(file).useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("$key=")) {
                    return line.substringAfter('=').trim()
                }
            }
        }
        throw IllegalStateException("Missing $key in gradle.properties")
    }

    private fun runProcess(command: List<String>, allowFailure: Boolean = false): Int {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw IllegalStateException("Process timed out: ${command.joinToString(" ")}")
        }
        if (!allowFailure && process.exitValue() != 0) {
            throw IllegalStateException(
                "Process failed (${process.exitValue()}): ${command.joinToString(" ")}\n$output",
            )
        }
        return process.exitValue()
    }

    private fun runProcessOutput(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw IllegalStateException("Process timed out: ${command.joinToString(" ")}")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException(
                "Process failed (${process.exitValue()}): ${command.joinToString(" ")}\n$output",
            )
        }
        return output
    }

    private fun shouldIgnoreIdeFailure(message: String, details: String): Boolean {
        val combined = "$message\n$details"
        return combined.contains("myRegisteredIndexes", ignoreCase = true) ||
            (combined.contains("RegisteredIndexes") && combined.contains("FileBasedIndexImpl"))
    }
}
