package com.github.chbndrhnns.betterpy.features.pytest.explorer

import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedFixture
import com.github.chbndrhnns.betterpy.features.pytest.explorer.model.CollectedTest
import com.github.chbndrhnns.betterpy.features.pytest.explorer.psi.PytestPsiResolver
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
