package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import fixtures.TestBase
import fixtures.doIntentionTest

class TogglePytestSkipIntentionTest : TestBase() {

    fun testSkipFunction() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            def test_some<caret>thing():
                pass
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

    fun testUnskipFunction() {
        myFixture.doIntentionTest(
            "test_foo.py",
            """
            import pytest

            @pytest.mark.skip
            def test_some<caret>thing():
                pass
            """,
            """
            import pytest

            def test_something():
                pass
            """,
            "Toggle pytest skip"
        )
    }

    fun testSkipClass() {
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
        myFixture.configureByText("test_foo.py", """
            def test_some<caret>thing():
                pass
        """.trimIndent())
        
        val intention = myFixture.findSingleIntention("Toggle pytest skip")
        myFixture.launchAction(intention)
        
        val text = myFixture.file.text
        assertTrue(text.contains("import pytest"))
        assertTrue(text.contains("@pytest.mark.skip"))
    }
}
