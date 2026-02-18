package com.github.chbndrhnns.betterpy.features.pytest.inlinefixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.jetbrains.python.psi.PyFile
import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable
import fixtures.assertRefactoringActionNotAvailable

class InlineFixtureRefactoringTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.pytest.inlinefixture.InlineFixtureRefactoringAction"

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableInlinePytestFixtureRefactoring = true
    }

    fun testActionAvailableOnFixtureParameter() {
        myFixture.assertRefactoringActionAvailable(
            "test_example.py",
            """
            import pytest

            @pytest.fixture
            def db():
                return "db"

            def test_save(<caret>db):
                assert db is not None
            """,
            actionId
        )
    }

    fun testActionAvailableOnFixtureDefinition() {
        myFixture.assertRefactoringActionAvailable(
            "test_example.py",
            """
            import pytest

            @pytest.fixture
            def <caret>db():
                return "db"

            def test_save(db):
                assert db is not None
            """,
            actionId
        )
    }

    fun testActionNotAvailableWhenFeatureDisabled() {
        PluginSettingsState.instance().state.enableInlinePytestFixtureRefactoring = false
        myFixture.assertRefactoringActionNotAvailable(
            "test_example.py",
            """
            import pytest

            @pytest.fixture
            def db():
                return "db"

            def test_save(<caret>db):
                assert db is not None
            """,
            actionId
        )
    }

    fun testActionNotAvailableOnRegularParameter() {
        myFixture.assertRefactoringActionNotAvailable(
            "example.py",
            """
            def helper(<caret>x):
                return x + 1
            """,
            actionId
        )
    }

    fun testInlineSimpleFixtureWithReturn() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>db():
                return create_db()

            def test_query(db):
                assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret) ?: error("Expected model")

        InlineFixtureProcessor(
            project = project,
            model = model,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            def test_query():
                db = create_db()
                assert db is not None
            """.trimIndent() + "\n"
        )
    }

    fun testInlineFixtureWithDependencies() {
        val code = """
            import pytest

            @pytest.fixture
            def db():
                return create_db()

            @pytest.fixture
            def <caret>user(db):
                u = User(name="test")
                db.save(u)
                return u

            def test_user_name(user):
                assert user.name == "test"
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            @pytest.fixture
            def db():
                return create_db()


            def test_user_name(db):
                user = User(name="test")
                db.save(user)
                assert user.name == "test"
            """.trimIndent() + "\n"
        )
    }

    fun testInlineFixtureNoDuplicateDependencies() {
        val code = """
            import pytest

            @pytest.fixture
            def db():
                return create_db()

            @pytest.fixture
            def <caret>saved_item(db):
                item = Item()
                db.save(item)
                return item

            def test_item(db, saved_item):
                result = db.get(saved_item.id)
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            @pytest.fixture
            def db():
                return create_db()


            def test_item(db):
                saved_item = Item()
                db.save(saved_item)
                result = db.get(saved_item.id)
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testInlineFixtureWithMultipleDependencies() {
        val code = """
            import pytest

            @pytest.fixture
            def db():
                return create_db()

            @pytest.fixture
            def cache():
                return create_cache()

            @pytest.fixture
            def <caret>service(db, cache):
                return Service(db, cache)

            def test_service(service):
                assert service.is_ready()
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            @pytest.fixture
            def db():
                return create_db()


            @pytest.fixture
            def cache():
                return create_cache()


            def test_service(db, cache):
                service = Service(db, cache)
                assert service.is_ready()
            """.trimIndent() + "\n"
        )
    }

    fun testInlineFixtureReturnSameNameAssignment() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>adapter():
                adapter = object()
                return adapter

            def test_(adapter):
                adapter
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            def test_():
                adapter = object()
                adapter
            """.trimIndent() + "\n"
        )
    }

    fun testInlineYieldFixtureSetupOnly() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>db():
                conn = create_connection()
                yield conn
                conn.close()

            def test_query(db):
                assert db.query("SELECT 1") is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            def test_query():
                db = create_connection()
                assert db.query("SELECT 1") is not None
            """.trimIndent() + "\n"
        )
    }

    fun testInlineThisUsageOnlyKeepsFixture() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>db():
                return create_db()

            def test_one(db):
                assert db is not None

            def test_two(db):
                assert db.query("SELECT 1")
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)
        val targetUsage = model!!.usages.first { it.function.name == "test_one" }

        InlineFixtureProcessor(
            project = project,
            model = model,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_THIS_ONLY),
            targetUsage = targetUsage
        ).run()

        myFixture.checkResult(
            """
            import pytest


            @pytest.fixture
            def db():
                return create_db()


            def test_one():
                db = create_db()
                assert db is not None


            def test_two(db):
                assert db.query("SELECT 1")
            """.trimIndent() + "\n"
        )
    }

    fun testInlineClassBasedFixtureUsage() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>db():
                return create_db()

            class TestOperations:
                def test_save(self, db):
                    assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            class TestOperations:
                def test_save(self):
                    db = create_db()
                    assert db is not None
            """.trimIndent() + "\n"
        )
    }

    fun testInlineFixtureFromConftest() {
        val conftest = myFixture.addFileToProject(
            "conftest.py",
            """
            import pytest

            @pytest.fixture
            def db():
                return create_db()

            @pytest.fixture
            def cache():
                return create_cache()
            """.trimIndent()
        )

        val code = """
            def test_query(db):
                assert db is not None
        """.trimIndent()

        myFixture.configureByText("test_module.py", code)
        val testFile = myFixture.file.virtualFile
        myFixture.doHighlighting()

        myFixture.configureByText(
            "conftest.py",
            """
            import pytest

            @pytest.fixture
            def <caret>db():
                return create_db()

            @pytest.fixture
            def cache():
                return create_cache()
            """.trimIndent()
        )
        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.openFileInEditor(conftest.virtualFile)
        myFixture.checkResult(
            """
            import pytest


            @pytest.fixture
            def cache():
                return create_cache()
            """.trimIndent()
        )

        myFixture.openFileInEditor(testFile)
        myFixture.checkResult(
            """
            import pytest


            def test_query():
                db = create_db()
                assert db is not None
            """.trimIndent() + "\n"
        )
    }

    fun testInlineUsefixturesDecoratorUsage() {
        val code = """
            import pytest

            @pytest.fixture
            def <caret>setup_db():
                create_db()

            @pytest.mark.usefixtures("setup_db")
            def test_query():
                assert True
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest


            def test_query():
                create_db()
                assert True
            """.trimIndent() + "\n"
        )
    }

    fun testInlineSideEffectOnlyFixture() {
        val code = """
            import pytest
            import os

            @pytest.fixture
            def <caret>setup_env():
                os.environ["KEY"] = "value"

            def test_env(setup_env):
                assert os.environ["KEY"] == "value"
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val analyzer = InlineFixtureAnalyzer(project, myFixture.file as PyFile)
        val model = analyzer.analyze(myFixture.elementAtCaret)

        InlineFixtureProcessor(
            project = project,
            model = model!!,
            options = InlineFixtureOptions(inlineMode = InlineMode.INLINE_ALL_AND_REMOVE)
        ).run()

        myFixture.checkResult(
            """
            import pytest
            import os


            def test_env():
                os.environ["KEY"] = "value"
                assert os.environ["KEY"] == "value"
            """.trimIndent() + "\n"
        )
    }
}
