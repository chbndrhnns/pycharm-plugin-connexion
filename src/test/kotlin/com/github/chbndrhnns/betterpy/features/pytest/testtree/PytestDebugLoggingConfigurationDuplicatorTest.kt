package com.github.chbndrhnns.betterpy.features.pytest.testtree

import com.intellij.execution.RunManager
import com.jetbrains.python.testing.PyTestConfiguration
import com.jetbrains.python.testing.PyTestFactory
import com.jetbrains.python.testing.PythonTestConfigurationType
import fixtures.TestBase

class PytestDebugLoggingConfigurationDuplicatorTest : TestBase() {

    fun testDuplicateCreatesTemporaryConfiguration() {
        val runManager = RunManager.getInstance(project)
        val type = PythonTestConfigurationType.getInstance()
        val factory = type.configurationFactories.first { it.id == PyTestFactory.id }

        val originalSettings = runManager.createConfiguration("Original Tests", factory)
        val originalConfig = originalSettings.configuration as PyTestConfiguration
        originalConfig.additionalArguments = "-v"
        originalConfig.keywords = "smoke"
        originalConfig.parameters = "tests/test_sample.py"
        runManager.addConfiguration(originalSettings)

        val nodeId = "tests/test_sample.py::test_demo"
        val newSettings = PytestDebugLoggingConfigurationDuplicator.duplicate(runManager, originalSettings, nodeId)

        assertNotNull("New configuration should be created", newSettings)
        val settings = newSettings!!
        assertTrue("New configuration should be temporary", settings.isTemporary)

        val newConfig = settings.configuration as PyTestConfiguration
        assertEquals("-v --log-cli-level=DEBUG", newConfig.additionalArguments)
        assertEquals("tests/test_sample.py $nodeId", newConfig.parameters)
        assertEquals("smoke and test_demo", newConfig.keywords)

        val refreshedOriginal = originalSettings.configuration as PyTestConfiguration
        assertEquals("Original configuration should remain unchanged", "-v", refreshedOriginal.additionalArguments)
        assertEquals("smoke", refreshedOriginal.keywords)
        assertEquals("tests/test_sample.py", refreshedOriginal.parameters)
    }
}