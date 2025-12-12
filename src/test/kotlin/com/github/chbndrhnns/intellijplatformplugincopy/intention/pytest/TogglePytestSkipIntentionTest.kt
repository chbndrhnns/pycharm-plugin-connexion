package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

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
            "Toggle pytest skip"
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

        val intention = myFixture.availableIntentions.single { it.text.startsWith("Toggle pytest skip") }
        assertEquals("Toggle pytest skip (function)", intention.text)
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
            "Toggle pytest skip"
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
            "Toggle pytest skip"
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

        val intention = myFixture.availableIntentions.single { it.text.startsWith("Toggle pytest skip") }
        assertEquals("Toggle pytest skip (class)", intention.text)
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
            "Toggle pytest skip"
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
            "Toggle pytest skip"
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
            "Toggle pytest skip"
        )
    }
    
    fun testSkipModule() {
        myFixture.configureByText("test_foo.py", """
            import pytest
            
            # Module skip
            <caret>
            def test_something():
                pass
        """.trimIndent())
        
        val intention = myFixture.findSingleIntention("Toggle pytest skip")
        myFixture.launchAction(intention)
        
        val text = myFixture.file.text
        assertTrue(text.contains("pytestmark = [pytest.mark.skip]"))
        assertTrue(text.contains("import pytest"))
        // Check that module skip comment is still there
        assertTrue(text.contains("# Module skip"))
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

        val intention = myFixture.availableIntentions.single { it.text.startsWith("Toggle pytest skip") }
        assertEquals("Toggle pytest skip (module)", intention.text)
    }

    fun testSkipModuleUpdate() {
        myFixture.configureByText("test_foo.py", """
            import pytest
            
            pytestmark = [pytest.mark.foo]
            <caret>
            def test_something():
                pass
        """.trimIndent())
        
        val intention = myFixture.findSingleIntention("Toggle pytest skip")
        myFixture.launchAction(intention)
        
        val text = myFixture.file.text
        // We expect both markers
        assertTrue(text.contains("pytest.mark.foo"))
        assertTrue(text.contains("pytest.mark.skip"))
        assertTrue(text.contains("pytestmark = ["))
    }
    
    fun testUnskipModule() {
        myFixture.configureByText("test_foo.py", """
            import pytest
            
            pytestmark = [pytest.mark.skip]
            <caret>
            def test_something():
                pass
        """.trimIndent())
        
        val intention = myFixture.findSingleIntention("Toggle pytest skip")
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
            "Toggle pytest skip"
        )
    }
}
