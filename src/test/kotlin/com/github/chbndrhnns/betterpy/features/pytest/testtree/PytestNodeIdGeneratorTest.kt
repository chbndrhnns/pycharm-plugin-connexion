package com.github.chbndrhnns.betterpy.features.pytest.testtree

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import fixtures.TestBase

class PytestNodeIdGeneratorTest : TestBase() {

    fun testGeneratesCorrectNodeIdForParameterizedTest() {
        // Given: A real file in the project
        val psiFile = myFixture.configureByText(
            "test_file.py",
            """
            import pytest

            @pytest.mark.parametrize("i", [1, 2])
            def test_new(i):
                pass
            """.trimIndent()
        )
        val virtualFile = psiFile.virtualFile
        val filePath = virtualFile.path

        // Given: A test hierarchy simulating a parameterized test
        val rootProxy = SMTestProxy("test_root", true, null)

        // File proxy
        // Note: SMTestProxy locationUrl for file usually starts with file:// or just the path for python?
        // But PytestNodeIdGenerator.parseProxy logic for file resolution relies on finding the module from protocol `python<path>`.
        // Actually, if directElement works, we are good.

        // Let's try to construct proxies that might resolve.
        // Or we can rely on the fact that parseProxy uses the proxy path if available, 
        // regardless of whether it resolved via PyTestsLocator, AS LONG AS it finds the file.

        // parseProxy Step 2: Find Module & Resolve Element
        // It needs a valid locationUrl to find the file.
        val protocol = "python<${filePath}>"
        val locationUrl = "$protocol://test_file.test_new"

        val fileProxy = SMTestProxy("test_file.py", true, "$protocol://test_file")
        rootProxy.addChild(fileProxy)

        val functionProxy = SMTestProxy("test_new", true, locationUrl)
        fileProxy.addChild(functionProxy)

        // The parameterized child
        // Its name is typically (1) or [1-1] depending on runner.
        // The issue says "(1)".
        val paramProxy = SMTestProxy("(1)", false, locationUrl)
        functionProxy.addChild(paramProxy)

        // When: We parse the proxy
        val record = PytestNodeIdGenerator.parseProxy(paramProxy, project)

        // Then: The node ID should use brackets format [1] instead of ::(1)
        assertNotNull("Record should not be null", record)
        // We expect: test_file.py::test_new[1]
        // Note: projectRelativePath might depend on where the file is created.
        // In tests, it's usually /src/test_file.py or similar.
        // The logic uses VfsUtilCore.getRelativePath(file, root).

        val relativePath = "test_file.py" // Since we created it at root of content
        assertEquals("${relativePath}::test_new[1]", record?.nodeid)
    }
}
