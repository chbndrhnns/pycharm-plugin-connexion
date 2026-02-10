package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class PytestPsiResolverTest : TestBase() {

    fun testResolveTopLevelTestFunction() {
        myFixture.configureByText(
            "test_example.py", """
            def test_hello():
                assert True
        """.trimIndent()
        )

        val test = CollectedTest(
            nodeId = "test_example.py::test_hello",
            modulePath = "test_example.py",
            className = null,
            functionName = "test_hello",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNotNull("Should resolve top-level test function", pointer)
        assertEquals("test_hello", pointer!!.element?.name)
    }

    fun testResolveClassMethod() {
        myFixture.configureByText(
            "test_auth.py", """
            class TestLogin:
                def test_success(self):
                    pass
        """.trimIndent()
        )

        val test = CollectedTest(
            nodeId = "test_auth.py::TestLogin::test_success",
            modulePath = "test_auth.py",
            className = "TestLogin",
            functionName = "test_success",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNotNull("Should resolve class method", pointer)
        assertEquals("test_success", pointer!!.element?.name)
    }

    fun testResolveNonExistentFunction() {
        myFixture.configureByText(
            "test_example.py", """
            def test_hello():
                pass
        """.trimIndent()
        )

        val test = CollectedTest(
            nodeId = "test_example.py::test_nonexistent",
            modulePath = "test_example.py",
            className = null,
            functionName = "test_nonexistent",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNull("Should return null for non-existent function", pointer)
    }

    fun testResolveNonExistentFile() {
        val test = CollectedTest(
            nodeId = "no_such_file.py::test_hello",
            modulePath = "no_such_file.py",
            className = null,
            functionName = "test_hello",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNull("Should return null for non-existent file", pointer)
    }

    fun testResolveNestedClassDistinguishesSameClassName() {
        myFixture.configureByText(
            "test_nested.py", """
            class Test1:
                class Test2:
                    def test_(self):
                        assert 1 == 2

            class Test2:
                def test_(self):
                    assert 1 == 2
        """.trimIndent()
        )

        val nestedTest = CollectedTest(
            nodeId = "test_nested.py::Test1::Test2::test_",
            modulePath = "test_nested.py",
            className = "Test2",
            functionName = "test_",
            fixtures = emptyList(),
        )

        val topLevelTest = CollectedTest(
            nodeId = "test_nested.py::Test2::test_",
            modulePath = "test_nested.py",
            className = "Test2",
            functionName = "test_",
            fixtures = emptyList(),
        )

        val nestedElement = PytestPsiResolver.resolveTestElement(project, nestedTest)
        assertNotNull("Should resolve nested class method", nestedElement)
        val nestedClass = nestedElement!!.containingClass
        assertNotNull(nestedClass)
        assertEquals("Test2", nestedClass!!.name)
        // The nested Test2's parent should be Test1
        val parentClass = PsiTreeUtil.getParentOfType(nestedClass, PyClass::class.java)
        assertNotNull("Nested Test2 should have parent class Test1", parentClass)
        assertEquals("Test1", parentClass!!.name)

        val topLevelElement = PytestPsiResolver.resolveTestElement(project, topLevelTest)
        assertNotNull("Should resolve top-level class method", topLevelElement)
        val topLevelClass = topLevelElement!!.containingClass
        assertNotNull(topLevelClass)
        assertEquals("Test2", topLevelClass!!.name)
        // The top-level Test2 should NOT have a parent class
        assertNull(
            "Top-level Test2 should not have parent class",
            PsiTreeUtil.getParentOfType(topLevelClass, PyClass::class.java)
        )
    }

    fun testResolveNonExistentClass() {
        myFixture.configureByText(
            "test_auth.py", """
            class TestLogin:
                def test_success(self):
                    pass
        """.trimIndent()
        )

        val test = CollectedTest(
            nodeId = "test_auth.py::NoSuchClass::test_success",
            modulePath = "test_auth.py",
            className = "NoSuchClass",
            functionName = "test_success",
            fixtures = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveTest(project, test)
        assertNull("Should return null for non-existent class", pointer)
    }

    fun testExtractFixtureDepsFromPsi() {
        myFixture.configureByText(
            "test_db.py", """
            def test_query(db_session, mock_user):
                pass
        """.trimIndent()
        )

        val func = myFixture.findElementByText("test_query", PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals(listOf("db_session", "mock_user"), deps)
    }

    fun testExtractFixtureDepsFiltersSelf() {
        myFixture.configureByText(
            "test_db.py", """
            class TestDB:
                def test_query(self, db_session):
                    pass
        """.trimIndent()
        )

        val func = myFixture.findElementByText("test_query", PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals("Should filter out 'self'", listOf("db_session"), deps)
    }

    fun testExtractFixtureDepsFiltersRequest() {
        myFixture.configureByText(
            "test_db.py", """
            def test_query(request, db_session):
                pass
        """.trimIndent()
        )

        val func = myFixture.findElementByText("test_query", PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals("Should filter out 'request'", listOf("db_session"), deps)
    }

    fun testExtractFixtureDepsEmptyParams() {
        myFixture.configureByText(
            "test_simple.py", """
            def test_nothing():
                pass
        """.trimIndent()
        )

        val func = myFixture.findElementByText("test_nothing", PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals("Should return empty list for no params", emptyList<String>(), deps)
    }

    fun testExtractFixtureDepsFiltersParametrizeArgs() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("arg", [1, 2, 3])
            def test_(arg, my_fixture):
                pass
        """.trimIndent()
        )

        val func = myFixture.findElementByText("test_", PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals("Should filter out parametrize arg", listOf("my_fixture"), deps)
    }

    fun testExtractFixtureDepsFiltersMultipleParametrizeArgs() {
        myFixture.configureByText(
            "test_param.py", """
            import pytest
            
            @pytest.mark.parametrize("a", [1, 2])
            @pytest.mark.parametrize("b", [3, 4])
            def test_(a, b, my_fixture):
                pass
        """.trimIndent()
        )

        val func = myFixture.findElementByText("test_", PyFunction::class.java)
        val deps = PytestPsiResolver.extractFixtureDepsFromPsi(func)
        assertEquals("Should filter out all parametrize args", listOf("my_fixture"), deps)
    }

    fun testResolveFixture() {
        myFixture.configureByText(
            "conftest.py", """
            import pytest
            
            @pytest.fixture
            def db_session():
                return "session"
        """.trimIndent()
        )

        val fixture = CollectedFixture(
            name = "db_session",
            scope = "function",
            definedIn = "conftest.py",
            functionName = "db_session",
            dependencies = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveFixture(project, fixture)
        assertNotNull("Should resolve fixture function", pointer)
        assertEquals("db_session", pointer!!.element?.name)
    }

    fun testResolveFixtureNonExistentFile() {
        val fixture = CollectedFixture(
            name = "db_session",
            scope = "function",
            definedIn = "no_such_conftest.py",
            functionName = "db_session",
            dependencies = emptyList(),
        )

        val pointer = PytestPsiResolver.resolveFixture(project, fixture)
        assertNull("Should return null for non-existent file", pointer)
    }
}
