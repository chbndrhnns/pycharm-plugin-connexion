package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.jetbrains.python.psi.PyFile
import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable
import fixtures.assertRefactoringActionNotAvailable

/**
 * Tests for Extract Pytest Fixture refactoring.
 */

class ExtractFixtureRefactoringTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.pytest.extractfixture.ExtractFixtureRefactoringAction"

    override fun setUp() {
        super.setUp()
        PluginSettingsState.instance().state.enableExtractPytestFixtureRefactoring = true
    }

    fun testActionAvailableInTestFunction() {
        myFixture.assertRefactoringActionAvailable(
            "test_example.py",
            """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                <caret>item = "test"
                db + item
            """,
            actionId
        )
    }

    fun testActionNotAvailableWhenFeatureDisabled() {
        PluginSettingsState.instance().state.enableExtractPytestFixtureRefactoring = false
        myFixture.assertRefactoringActionNotAvailable(
            "test_example.py",
            """
            import pytest
            
            def test_save():
                <caret>item = "test"
            """,
            actionId
        )
    }

    fun testActionNotAvailableOutsideTestOrFixture() {
        myFixture.assertRefactoringActionNotAvailable(
            "example.py",
            """
            def regular_function():
                <caret>x = 1
            """,
            actionId
        )
    }

    fun testBasicExtraction() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        // Select the two lines: "item = ..." and "result = ..."
        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1) // Inside "item"

        // Run processor directly to test extraction logic
        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(fixtureName = "saved_item")
        
        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture
            def saved_item(db):
                item = "test"
                result = db + item
                return result
            
            
            def test_save(saved_item):
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testMultipleFixturesUsed() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            @pytest.fixture
            def cache():
                return "cache"
            
            def test_complex(db, cache):
                db_result = db + "_saved"
                cache_result = cache + "_invalidated"
                assert db_result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        // Select the two lines using both fixtures
        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("db_result = db")
        val selectionEnd =
            text.indexOf("cache_result = cache + \"_invalidated\"") + "cache_result = cache + \"_invalidated\"".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(fixtureName = "setup_data")

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture
            def cache():
                return "cache"
            
            
            @pytest.fixture
            def setup_data(db, cache):
                db_result = db + "_saved"
                cache_result = cache + "_invalidated"
                return cache_result
            
            
            def test_complex(setup_data):
                assert db_result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractionFromYieldFixture() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                conn = "connection"
                yield conn
                conn = None
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(fixtureName = "saved_item")

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                conn = "connection"
                yield conn
                conn = None
            
            
            @pytest.fixture
            def saved_item(db):
                item = "test"
                result = db + item
                return result
            
            
            def test_save(saved_item):
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractionFromAsyncFixture() {
        val code = """
            import pytest
            import pytest_asyncio
            
            @pytest_asyncio.fixture
            async def async_db():
                return "async_db"
            
            @pytest.mark.asyncio
            async def test_async_save(async_db):
                item = "test"
                result = async_db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = async_db + item") + "result = async_db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(fixtureName = "saved_item")

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            import pytest_asyncio
            
            
            @pytest_asyncio.fixture
            async def async_db():
                return "async_db"
            
            
            @pytest.fixture
            def saved_item(async_db):
                item = "test"
                result = async_db + item
                return result
            
            
            @pytest.mark.asyncio
            async def test_async_save(saved_item):
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractAsHelperFunction() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(
            fixtureName = "create_item",
            extractionMode = ExtractionMode.HELPER_FUNCTION
        )

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            def create_item(db):
                item = "test"
                result = db + item
                return result
            
            
            def test_save(db):
                result = create_item(db)
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractWithScope() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(
            fixtureName = "session_item",
            fixtureScope = FixtureScope.SESSION
        )

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture(scope="session")
            def session_item(db):
                item = "test"
                result = db + item
                return result
            
            
            def test_save(session_item):
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractToConftest() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(
            fixtureName = "saved_item",
            targetLocation = TargetLocation.CONFTEST
        )

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        val conftestFile = file.containingDirectory?.findFile("conftest.py")
            ?: error("conftest.py should be created")
        myFixture.openFileInEditor(conftestFile.virtualFile)
        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def saved_item(db):
                item = "test"
                result = db + item
                return result
            """.trimIndent() + "\n"
        )

        myFixture.openFileInEditor(file.virtualFile)
        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            def test_save(saved_item):
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractionFromClassBasedTest() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            class TestUserOperations:
                def test_save(self, db):
                    item = "test"
                    result = db + item
                    assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(fixtureName = "saved_item")

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture
            def saved_item(db):
                item = "test"
                result = db + item
                return result
            
            
            class TestUserOperations:
                def test_save(self, saved_item):
                    assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractWithAutouse() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(
            fixtureName = "auto_item",
            autouse = true
        )

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture(autouse=True)
            def auto_item(db):
                item = "test"
                result = db + item
                return result
            
            
            def test_save():
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractWithScopeAndAutouse() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(
            fixtureName = "session_auto_item",
            fixtureScope = FixtureScope.SESSION,
            autouse = true
        )

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture(scope="session", autouse=True)
            def session_auto_item(db):
                item = "test"
                result = db + item
                return result
            
            
            def test_save():
                assert result is not None
            """.trimIndent() + "\n"
        )
    }

    fun testExtractWithUsefixtures() {
        val code = """
            import pytest
            
            @pytest.fixture
            def db():
                return "db"
            
            def test_save(db):
                item = "test"
                result = db + item
                assert result is not None
        """.trimIndent()

        myFixture.configureByText("test_example.py", code)
        myFixture.doHighlighting()

        val text = myFixture.editor.document.text
        val selectionStart = text.indexOf("item = \"test\"")
        val selectionEnd = text.indexOf("result = db + item") + "result = db + item".length
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
        myFixture.editor.caretModel.moveToOffset(selectionStart + 1)

        val file = myFixture.file as PyFile
        val analyzer = ExtractFixtureAnalyzer(file, myFixture.editor.selectionModel)
        val model = analyzer.analyze()!!
        val options = ExtractFixtureOptions(
            fixtureName = "saved_item",
            injectionMode = InjectionMode.USEFIXTURES
        )

        ExtractFixtureProcessor(
            project = project,
            file = file,
            model = model,
            options = options
        ).run()

        myFixture.checkResult(
            """
            import pytest
            
            
            @pytest.fixture
            def db():
                return "db"
            
            
            @pytest.fixture
            def saved_item(db):
                item = "test"
                result = db + item
                return result
            
            
            @pytest.mark.usefixtures("saved_item")
            def test_save():
                assert result is not None
            """.trimIndent() + "\n"
        )
    }
}
