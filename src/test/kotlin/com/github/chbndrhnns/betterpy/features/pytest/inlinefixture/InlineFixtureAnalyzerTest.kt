package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

class InlineFixtureAnalyzerTest : TestBase() {

    fun testResolvesFixtureFromParameter() {
        val code = """
            import pytest

            @pytest.fixture
            def db():
                return create_db()

            def test_query(<caret>db):
                assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        val file = myFixture.file as PyFile
        val element = file.findElementAt(myFixture.caretOffset) ?: error("Element not found")
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element) ?: error("Expected model")

        assertEquals("db", model.fixtureName)
    }

    fun testResolvesFixtureFromDefinition() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>db():
                return create_db()

            def test_query(db):
                assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        val file = myFixture.file as PyFile
        val element = file.findElementAt(myFixture.caretOffset) ?: error("Element not found")
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element) ?: error("Expected model")

        assertEquals("db", model.fixtureName)
        assertTrue(model.dependencies.isEmpty())
    }

    fun testDetectsDependenciesAndReturn() {
        val code = """
            import pytest

            @pytest.fixture
            def user(db, cache):
                u = User(name="test")
                db.save(u)
                return u

            def test_user(user):
                assert user.name == "test"
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        val file = myFixture.file as PyFile
        val element = file.findElementAt(file.text.indexOf("def user")) ?: error("Element not found")
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element) ?: error("Expected model")

        assertEquals(listOf("db", "cache"), model.dependencies)
        assertEquals("u", model.returnInfo.returnedExpression?.text)
    }

    fun testDetectsYieldFixture() {
        val code = """
            import pytest

            @pytest.fixture
            def db():
                conn = create_connection()
                yield conn
                conn.close()

            def test_query(db):
                assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        val file = myFixture.file as PyFile
        val element = file.findElementAt(file.text.indexOf("def db")) ?: error("Element not found")
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element) ?: error("Expected model")

        assertTrue(model.isYieldFixture)
        assertEquals("conn", model.returnInfo.returnedExpression?.text)
        assertEquals(1, model.returnInfo.statementsAfterYield.size)
    }

    fun testDetectsParametrizedFixture() {
        val code = """
            import pytest

            @pytest.fixture(params=["a", "b", "c"])
            def letter(request):
                return request.param

            def test_letter(letter):
                assert letter in ["a", "b", "c"]
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        val file = myFixture.file as PyFile
        val element = file.findElementAt(file.text.indexOf("def letter")) ?: error("Element not found")
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element) ?: error("Expected model")

        assertTrue(model.isParametrized)
    }

    fun testDetectsNonFunctionScope() {
        val code = """
            import pytest

            @pytest.fixture(scope="session")
            def db():
                return create_db()

            def test_query(db):
                assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        val file = myFixture.file as PyFile
        val element = file.findElementAt(file.text.indexOf("def db")) ?: error("Element not found")
        val analyzer = InlineFixtureAnalyzer(project, file)
        val model = analyzer.analyze(element) ?: error("Expected model")

        assertEquals("session", model.scope?.value)
    }
}
