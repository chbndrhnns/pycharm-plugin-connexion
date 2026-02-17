package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

/**
 * Unit tests for ExtractFixtureAnalyzer.
 * Tests fixture detection accuracy, variable flow analysis, and edge cases.
 */
class ExtractFixtureAnalyzerTest : TestBase() {

    fun testDetectsUsedFixtureParameters() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db, cache):
                item = db + "test"
                result = cache + item
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = db")
        val selectionEnd = text.indexOf("result = cache + item") + "result = cache + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        assertEquals(2, model.usedFixtures.size)
        assertTrue(model.usedFixtures.any { it.name == "db" })
        assertTrue(model.usedFixtures.any { it.name == "cache" })
    }

    fun testIgnoresSelfAndClsParameters() {
        val code = """
            import pytest
            
            class TestClass:
                def test_method(self, db):
                    item = db + "test"
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = db")
        val selectionEnd = selectionStart + "item = db + \"test\"".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        assertEquals(1, model.usedFixtures.size)
        assertEquals("db", model.usedFixtures.first().name)
        assertFalse(model.usedFixtures.any { it.name == "self" })
    }

    fun testFindsDefinedLocalVariables() {
        val code = """
            import pytest
            
            def test_save(db):
                item = "test"
                result = db + item
                count = 42
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("count = 42") + "count = 42".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        assertTrue(model.definedLocals.contains("item"))
        assertTrue(model.definedLocals.contains("result"))
        assertTrue(model.definedLocals.contains("count"))
    }

    fun testFindsOutputVariables() {
        val code = """
            import pytest
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        // 'result' is defined in selection and used after (in assert)
        assertTrue(model.outputVariables.contains("result"))
    }

    fun testReturnsNullForEmptySelection() {
        val code = """
            import pytest
            
            def test_save(db):
                item = "test"
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        // No selection (start == end)
        myFixture.editor.selectionModel.setSelection(10, 10)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()

        assertNull(model)
    }

    fun testReturnsNullForSelectionOutsideFunction() {
        val code = """
            import pytest
            
            x = 1
            
            def test_save(db):
                item = "test"
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("x = 1")
        val selectionEnd = selectionStart + "x = 1".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()

        assertNull(model)
    }

    fun testHandlesForLoopVariables() {
        val code = """
            import pytest
            
            def test_loop(items):
                for item in items:
                    print(item)
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("for item")
        val selectionEnd = text.indexOf("print(item)") + "print(item)".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        assertTrue(model.definedLocals.contains("item"))
        assertTrue(model.usedFixtures.any { it.name == "items" })
    }

    fun testHandlesListComprehension() {
        val code = """
            import pytest
            
            def test_comprehension(db):
                results = [x for x in db]
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("results = ")
        val selectionEnd = selectionStart + "results = [x for x in db]".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        assertTrue(model.definedLocals.contains("results"))
        assertTrue(model.usedFixtures.any { it.name == "db" })
    }

    fun testMultipleStatements() {
        val code = """
            import pytest
            
            def test_multi(db, cache):
                a = db.get()
                b = cache.get()
                c = a + b
                assert c
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("a = db.get()")
        val selectionEnd = text.indexOf("c = a + b") + "c = a + b".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!

        assertEquals(3, model.statements.size)
        assertEquals(2, model.usedFixtures.size)
        assertTrue(model.definedLocals.containsAll(setOf("a", "b", "c")))
        // 'c' is used after selection in assert
        assertTrue(model.outputVariables.contains("c"))
    }
}
