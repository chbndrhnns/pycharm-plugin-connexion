package com.github.chbndrhnns.betterpy.features.pytest.outcome

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import fixtures.TestBase

class PyTestFailedLineInspectionTest : TestBase() {

    fun `test no highlight when setting disabled`() {
        val code = """
            def test_failing():
                x = 1
                y = 2
                assert x == y
        """.trimIndent()

        myFixture.configureByText("test_disabled.py", code)

        val projectRoot = project.basePath!!
        val locationUrl = "python<$projectRoot>://test_disabled.test_failing"

        val diffService = TestOutcomeDiffService.getInstance(project)
        diffService.put(locationUrl, OutcomeDiff("1", "2", failedLine = 4))

        // Disable the setting
        PluginSettingsState.instance().state.enablePyTestFailedLineInspection = false

        try {
            myFixture.enableInspections(PyTestFailedLineInspection::class.java)
            val highlights = myFixture.doHighlighting()

            val failedLineHighlight = highlights.find { it.description == "Test failed at this line" }
            assertNull("Highlight should NOT be present when setting is disabled", failedLineHighlight)
        } finally {
            // Restore setting
            PluginSettingsState.instance().state.enablePyTestFailedLineInspection = true
        }
    }

    fun `test highlights failed assert line`() {
        val code = """
            def test_failing():
                x = 1
                y = 2
                assert x == y
        """.trimIndent()

        myFixture.configureByText("test_sample.py", code)
        val pyFile = myFixture.file
        val virtualFile = pyFile.virtualFile

        // Setup the diff data in service
        // The URL needs to match what PytestLocationUrlFactory generates.
        // For a file in the project root, it's typically python<project_root>://test_sample.test_failing
        val projectRoot = project.basePath!!
        val locationUrl = "python<$projectRoot>://test_sample.test_failing"

        val diffService = TestOutcomeDiffService.getInstance(project)
        // Line 4 is 'assert x == y'
        diffService.put(locationUrl, OutcomeDiff("1", "2", failedLine = 4))

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        val highlights = myFixture.doHighlighting()

        val failedLineHighlight = highlights.find { it.description == "Test failed at this line" }
        assertNotNull("Highlight should be present", failedLineHighlight)

        val document = myFixture.getDocument(pyFile)
        val line = document.getLineNumber(failedLineHighlight!!.startOffset) + 1
        assertEquals("Highlight should be on line 4", 4, line)
    }

    fun `test highlights failed function definition`() {
        val code = """
            def test_failing_at_start():
                pass
        """.trimIndent()

        myFixture.configureByText("test_start.py", code)
        val pyFile = myFixture.file
        val projectRoot = project.basePath!!
        val locationUrl = "python<$projectRoot>://test_start.test_failing_at_start"

        val diffService = TestOutcomeDiffService.getInstance(project)
        // Line 1 is 'def test_failing_at_start():'
        diffService.put(locationUrl, OutcomeDiff("", "", failedLine = 1))

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        val highlights = myFixture.doHighlighting()

        val failedLineHighlight = highlights.find { it.description == "Test failed at this line" }
        assertNotNull("Highlight should be present", failedLineHighlight)

        val document = myFixture.getDocument(pyFile)
        val line = document.getLineNumber(failedLineHighlight!!.startOffset) + 1
        assertEquals("Highlight should be on line 1", 1, line)
    }

    fun `test highlights failed method in class`() {
        val code = """
            class Test:
                def test_(self):
                    do()
                    assert 1 == 2
        """.trimIndent()

        myFixture.configureByText("test_class.py", code)
        val pyFile = myFixture.file
        val projectRoot = project.basePath!!
        // PyCharm/Pytest URL for method in class typically looks like:
        // python<project_root>://test_class.Test.test_
        val locationUrl = "python<$projectRoot>://test_class.Test.test_"

        val diffService = TestOutcomeDiffService.getInstance(project)
        // Line 4 is 'assert 1 == 2' (1-based)
        // 1: class Test:
        // 2:     def test_(self):
        // 3:         do()
        // 4:         assert 1 == 2
        diffService.put(locationUrl, OutcomeDiff("1", "2", failedLine = 4))

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        val highlights = myFixture.doHighlighting()

        val failedLineHighlight = highlights.find { it.description == "Test failed at this line" }
        assertNotNull(
            "Highlight should be present for method in class. Highlights: ${highlights.map { it.description }}",
            failedLineHighlight
        )

        val document = myFixture.getDocument(pyFile)
        val line = document.getLineNumber(failedLineHighlight!!.startOffset) + 1
        assertEquals("Highlight should be on line 4", 4, line)
    }

    fun `test highlights arbitrary failing line in method`() {
        val code = """
            class Test:
                def test_(self):
                    do_something()
                    assert 1 == 1
        """.trimIndent()

        myFixture.configureByText("test_arbitrary.py", code)
        val pyFile = myFixture.file
        val projectRoot = project.basePath!!
        val locationUrl = "python<$projectRoot>://test_arbitrary.Test.test_"

        val diffService = TestOutcomeDiffService.getInstance(project)
        // Line 3 is 'do_something()' (1-based)
        diffService.put(locationUrl, OutcomeDiff("", "", failedLine = 3))

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        val highlights = myFixture.doHighlighting()

        val failedLineHighlight = highlights.find { it.description == "Test failed at this line" }
        assertNotNull("Highlight should be present for arbitrary line", failedLineHighlight)

        val document = myFixture.getDocument(pyFile)
        val line = document.getLineNumber(failedLineHighlight!!.startOffset) + 1
        assertEquals("Highlight should be on line 3", 3, line)
    }

    fun `test generates correct locationUrls for method in class`() {
        val code = """
            class MyClass:
                def test_method(self):
                    pass
        """.trimIndent()

        val psiFile = myFixture.configureByText("test_file.py", code)
        val pyFile = psiFile as com.jetbrains.python.psi.PyFile
        val pyClass = pyFile.topLevelClasses.first()
        val pyMethod = pyClass.methods.first()

        val urls = PytestLocationUrlFactory.fromPyFunction(pyMethod)

        // One of the URLs should be the qualified name from the project/content root
        // Another should be relative to the source root (if any)

        val projectRoot = project.basePath!!
        val expectedQName = "test_file.MyClass.test_method"

        assertTrue(
            "URLs should contain project root based URL with class name. Got: $urls",
            urls.any { it.contains(expectedQName) })

        // If it's a source root, it should also contain the class name
        // In this test environment, the file is usually in the content root
        urls.forEach { url ->
            assertTrue("URL '$url' should contain the class name 'MyClass'", url.contains("MyClass"))
        }
    }

    fun `test highlights failed method in nested class`() {
        val code = """
            class Test1:
                class Test2:
                    def test_(self):
                        assert 1 == 2
        """.trimIndent()

        myFixture.configureByText("test_nested.py", code)
        val pyFile = myFixture.file
        val projectRoot = project.basePath!!
        // The expected URL should include BOTH classes
        val locationUrl = "python<$projectRoot>://test_nested.Test1.Test2.test_"

        val diffService = TestOutcomeDiffService.getInstance(project)
        // 1: class Test1:
        // 2:     class Test2:
        // 3:         def test_(self):
        // 4:             assert 1 == 2
        diffService.put(locationUrl, OutcomeDiff("1", "2", failedLine = 4))

        myFixture.enableInspections(PyTestFailedLineInspection::class.java)
        val highlights = myFixture.doHighlighting()

        val failedLineHighlight = highlights.find { it.description == "Test failed at this line" }
        assertNotNull(
            "Highlight should be present for method in nested class. Highlights: ${highlights.map { it.description }}",
            failedLineHighlight
        )

        val document = myFixture.getDocument(pyFile)
        val line = document.getLineNumber(failedLineHighlight!!.startOffset) + 1
        assertEquals("Highlight should be on line 4", 4, line)
    }
}
