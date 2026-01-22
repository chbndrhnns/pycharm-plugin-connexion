package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import fixtures.TestBase
import fixtures.assertIntentionAvailable
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class TogglePytestSkipIntentionTest : TestBase() {

    fun testAvailableInsideTestFunctionBody() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            def test_something():
                <caret>pass
            """,
            """
            import pytest

            @pytest.mark.skip
            def test_something():
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testMenuItemIndicatesFunctionScope() {
        myFixture.configureByText(
            "test_foo.py",
            """
            import pytest

            def test_something():
                <caret>pass
            """.trimIndent()
        )
        myFixture.doHighlighting()

        val intention = myFixture.availableIntentions.single { it.text.startsWith("BetterPy: Toggle pytest skip") }
        assertEquals("BetterPy: Toggle pytest skip (function)", intention.text)
    }

    fun testAvailableInsideTestMethodBody() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            class TestClass:
                def test_something(self):
                    <caret>pass
            """,
            """
            import pytest

            class TestClass:
                @pytest.mark.skip
                def test_something(self):
                    pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testClassLevelAvailableOnClassName() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            class Test<caret>Class:
                def test_something(self):
                    pass
            """,
            """
            import pytest

            @pytest.mark.skip
            class TestClass:
                def test_something(self):
                    pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testClassLevelAvailableOnClassKeyword() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            <caret>class TestClass:
                def test_something(self):
                    pass
            """,
            """
            import pytest

            @pytest.mark.skip
            class TestClass:
                def test_something(self):
                    pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testMenuItemIndicatesClassScope() {
        myFixture.configureByText(
            "test_foo.py",
            """
            import pytest

            class Test<caret>Class:
                def test_something(self):
                    pass
            """.trimIndent()
        )
        myFixture.doHighlighting()

        val intention = myFixture.availableIntentions.single { it.text.startsWith("BetterPy: Toggle pytest skip") }
        assertEquals("BetterPy: Toggle pytest skip (class)", intention.text)
    }

    fun testClassLevelNotAvailableInsideClassBody() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            import pytest

            class TestClass:
                <caret>pass

            def test_something(self):
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testNotAvailableInsideNonTestFunction() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            import pytest

            def helper():
                <caret>pass

            def test_something():
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testNotAvailableInsideNonTestClass() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            import pytest

            class Foo:
                <caret>pass

            def test_something():
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testSkipModule() {
        myFixture.configureByText(
            "test_foo.py", """
            import pytest
            
            # Module skip
            <caret>
            def test_something():
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Toggle pytest skip")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        assertTrue(text.contains("pytestmark = [pytest.mark.skip]"))
        assertTrue(text.contains("import pytest"))
        // Check that module skip comment is still there
        assertTrue(text.contains("# Module skip"))
    }

    fun testFunctionLevelAvailableOnDefKeyword() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            <caret>def test_something():
                pass
            """,
            """
            import pytest

            @pytest.mark.skip
            def test_something():
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testMenuItemIndicatesModuleScope() {
        myFixture.configureByText(
            "test_foo.py",
            """
            import pytest

            <caret>
            def test_something():
                pass
            """.trimIndent()
        )
        myFixture.doHighlighting()

        val intention = myFixture.availableIntentions.single { it.text.startsWith("BetterPy: Toggle pytest skip") }
        assertEquals("BetterPy: Toggle pytest skip (module)", intention.text)
    }

    fun testSkipModuleUpdate() {
        myFixture.configureByText(
            "test_foo.py", """
            import pytest
            
            pytestmark = [pytest.mark.foo]
            <caret>
            def test_something():
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Toggle pytest skip")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        // We expect both markers
        assertTrue(text.contains("pytest.mark.foo"))
        assertTrue(text.contains("pytest.mark.skip"))
        assertTrue(text.contains("pytestmark = ["))
    }

    fun testUnskipModule() {
        myFixture.configureByText(
            "test_foo.py", """
            import pytest
            
            pytestmark = [pytest.mark.skip]
            <caret>
            def test_something():
                pass
        """.trimIndent()
        )

        val intention = myFixture.findSingleIntention("BetterPy: Toggle pytest skip")
        myFixture.launchAction(intention)

        val text = myFixture.file.text
        // Should be removed or empty list
        assertTrue(text.contains("pytestmark = []") || !text.contains("pytestmark"))
        assertFalse(text.contains("pytest.mark.skip"))
    }

    fun testSkipFunctionAddsImport() {
        myFixture.assertIntentionAvailable(
            "test_foo.py",
            """
            def test_something():
                <caret>pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }

    fun testNotAvailableOnTopLevelAssignment() {
        myFixture.assertIntentionNotAvailable(
            "test_foo.py",
            """
            import pytest
            
            val3 = <caret>[]
            
            def test_something():
                pass
            """,
            "BetterPy: Toggle pytest skip"
        )
    }
}
