package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

/**
 * Tests for pytest fixture navigation (Go to Declaration and Show Implementations).
 */
class PytestFixtureNavigationTest : TestBase() {

    // Test 1: Simple module fixture - GTD from usage to definition
    fun testSimpleModuleFixture() {
        val code = """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return 42
            
            def test_something(my_fixture<caret>):
                assert my_fixture == 42
        """.trimIndent()

        myFixture.configureByText("test_simple.py", code)

        // elementAtCaret should resolve to the fixture definition via the reference
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)
        assertEquals("Should resolve to my_fixture", "my_fixture", (resolved as PyFunction).name)
    }

    // Test 2: Class override - fixture in base and derived class
    fun testClassOverrideFixture() {
        val code = """
            import pytest
            
            class BaseTest:
                @pytest.fixture
                def my_fixture(self):
                    return "base"
            
            class TestDerived(BaseTest):
                @pytest.fixture
                def my_fixture(self):
                    return "derived"
                
                def test_something(self, my_fixture<caret>):
                    assert my_fixture == "derived"
        """.trimIndent()

        myFixture.configureByText("test_class.py", code)

        // Should resolve to the derived class fixture (closest in precedence)
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)

        // The resolved fixture should be in TestDerived class
        val containingClass = (resolved as PyFunction).containingClass
        assertEquals("Should resolve to derived class fixture", "TestDerived", containingClass?.name)
    }

    // Test 3: Module vs conftest - module fixture takes precedence
    fun testModuleVsConftestPrecedence() {
        // Create conftest.py with a fixture
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "conftest"
        """.trimIndent()
        )

        // Create test file with same fixture name
        val code = """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "module"
            
            def test_something(my_fixture<caret>):
                assert my_fixture == "module"
        """.trimIndent()

        myFixture.configureByText("test_module.py", code)

        // Should resolve to module fixture (higher precedence)
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)

        // The resolved fixture should be in the same file
        assertEquals("Should resolve to module fixture", "test_module.py", (resolved as PyFunction).containingFile.name)
    }

    // Test 4: Nested conftest resolution
    fun testNestedConftestResolution() {
        // Create root conftest.py
        myFixture.addFileToProject(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "root"
        """.trimIndent()
        )

        // Create subdir conftest.py
        val subdirConftest = myFixture.addFileToProject(
            "subdir/conftest.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "subdir"
        """.trimIndent()
        )

        // Create test in subdir - use addFileToProject instead of configureByText
        val testFile = myFixture.addFileToProject(
            "subdir/test_nested.py", """
            def test_something(my_fixture):
                assert my_fixture == "subdir"
        """.trimIndent()
        )

        myFixture.configureFromExistingVirtualFile(testFile.virtualFile)

        // Move caret to the parameter
        val offset = testFile.text.indexOf("my_fixture")
        myFixture.editor.caretModel.moveToOffset(offset + "my_fixture".length)

        // Should resolve to subdir conftest (closer)
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)

        // The resolved fixture should be in subdir/conftest.py
        val resolvedPath = (resolved as PyFunction).containingFile.virtualFile.path
        assertTrue("Should resolve to subdir conftest", resolvedPath.contains("subdir/conftest.py"))
    }

    // Test 5: usefixtures string literal
    fun testUsefixuresStringReference() {
        val code = """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return 42
            
            @pytest.mark.usefixtures("my_fixture<caret>")
            def test_something():
                pass
        """.trimIndent()

        myFixture.configureByText("test_usefixtures.py", code)

        // Should resolve to the fixture definition
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)
        assertEquals("Should resolve to my_fixture", "my_fixture", (resolved as PyFunction).name)
    }

    // Test 6: Import resolution
    fun testImportedFixture() {
        // Create fixtures.py with a fixture
        myFixture.addFileToProject(
            "fixtures.py", """
            import pytest
            
            @pytest.fixture
            def my_fixture():
                return "imported"
        """.trimIndent()
        )

        // Create test file that imports the fixture
        val code = """
            from fixtures import my_fixture
            
            def test_something(my_fixture<caret>):
                assert my_fixture == "imported"
        """.trimIndent()

        myFixture.configureByText("test_import.py", code)

        // Should resolve to the imported fixture
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)
        assertEquals("Should resolve to fixtures.py", "fixtures.py", (resolved as PyFunction).containingFile.name)
    }

    // Test 7: Fixture with name= argument
    fun testFixtureWithNameArgument() {
        val code = """
            import pytest
            
            @pytest.fixture(name="custom_name")
            def my_fixture_impl():
                return 42
            
            def test_something(custom_name<caret>):
                assert custom_name == 42
        """.trimIndent()

        myFixture.configureByText("test_named.py", code)

        // Should resolve to the fixture with name= argument
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)
        assertEquals("Should resolve to my_fixture_impl", "my_fixture_impl", (resolved as PyFunction).name)
    }

    // Test 8: pytest_asyncio.fixture
    fun testPytestAsyncioFixture() {
        val code = """
            import pytest_asyncio
            
            @pytest_asyncio.fixture
            async def my_fixture():
                return 42
            
            async def test_something(my_fixture<caret>):
                assert my_fixture == 42
        """.trimIndent()

        myFixture.configureByText("test_async.py", code)

        // Should resolve to the async fixture
        val resolved = myFixture.elementAtCaret
        assertNotNull("Should resolve to fixture function", resolved)
        assertInstanceOf(resolved, PyFunction::class.java)
        assertEquals("Should resolve to my_fixture", "my_fixture", (resolved as PyFunction).name)
    }

    // Test 9: No reference for non-test functions
    fun testNoReferenceForNonTestFunction() {
        val code = """
            def regular_function(my_parameter<caret>):
                pass
        """.trimIndent()

        myFixture.configureByText("test_regular.py", code)

        // Should NOT resolve to anything (no fixture reference)
        val resolved = myFixture.elementAtCaret
        // elementAtCaret should be null or the parameter itself, not a fixture
        if (resolved is PyFunction) {
            fail("Should not resolve to a fixture function for non-test function parameter")
        }
    }

    // Test 10: No reference for self/cls parameters
    fun testNoReferenceForSelfParameter() {
        val code = """
            class TestClass:
                def test_something(self<caret>):
                    pass
        """.trimIndent()

        myFixture.configureByText("test_self.py", code)

        // Should NOT resolve to anything (no fixture reference for self)
        val resolved = myFixture.elementAtCaret
        // elementAtCaret should be null or the parameter itself, not a fixture
        if (resolved is PyFunction) {
            fail("Should not resolve to a fixture function for self parameter")
        }
    }
}
