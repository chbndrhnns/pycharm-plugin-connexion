package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import fixtures.TestBase

class NewPytestMemberActionsTest : TestBase() {

    private val testActionId =
        "com.github.chbndrhnns.betterpy.features.pytest.fixture.NewPytestTestAction"
    private val fixtureActionId =
        "com.github.chbndrhnns.betterpy.features.pytest.fixture.NewPytestFixtureAction"

    fun testActionsAreRegistered() {
        val actionManager = ActionManager.getInstance()
        val testAction = actionManager.getAction(testActionId)
        val fixtureAction = actionManager.getAction(fixtureActionId)

        assertNotNull("New pytest test action should be registered", testAction)
        assertTrue("New pytest test action should be correct type", testAction is NewPytestTestAction)
        assertNotNull("New pytest fixture action should be registered", fixtureAction)
        assertTrue("New pytest fixture action should be correct type", fixtureAction is NewPytestFixtureAction)
    }

    fun testNewPytestTestActionVisibleInTestContext() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            def test_sample():
                <caret>pass
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.update(event)

        assertTrue("Action should be visible in pytest test context", event.presentation.isEnabledAndVisible)
    }

    fun testNewPytestTestActionHiddenOutsideTestContext() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "sample.py",
            """
            def helper():
                <caret>pass
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.update(event)

        assertFalse("Action should be hidden outside pytest test context", event.presentation.isEnabledAndVisible)
    }

    fun testNewPytestTestActionInsertsModuleTest() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            def test_sample():
                <caret>pass
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            def test_sample():
                pass


            def test_new():
                pass
            """
        )
    }

    fun testNewPytestFixtureActionInsertsModuleFixture() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            def test_sample():
                <caret>pass
            """.trimIndent()
        )

        val action = NewPytestFixtureAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            import pytest


            def test_sample():
                pass


            @pytest.fixture
            def new_fixture():
                pass
            """
        )
    }

    fun testNewPytestTestActionInsertsMethodWithSelf() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            class TestSomething:
                def test_existing(self):
                    <caret>pass
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            class TestSomething:
                def test_existing(self):
                    pass

                def test_new(self):
                    pass
            """
        )
    }

    fun testNewPytestFixtureActionInsertsMethodWithSelf() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            import pytest

            class TestSomething:
                def test_existing(self):
                    <caret>pass
            """.trimIndent()
        )

        val action = NewPytestFixtureAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            import pytest

            class TestSomething:
                def test_existing(self):
                    pass

                @pytest.fixture
                def new_fixture(self):
                    pass
            """
        )
    }

    fun testNewPytestTestActionInsertsIntoEmptyClass() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            class TestSomething:
                <caret>
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            class TestSomething:
                def test_new(self):
                    pass
            """
        )
    }

    fun testNewPytestFixtureActionInsertsIntoEmptyClass() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            import pytest

            class TestSomething:
                <caret>
            """.trimIndent()
        )

        val action = NewPytestFixtureAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            import pytest

            class TestSomething:
                @pytest.fixture
                def new_fixture(self):
                    pass
            """
        )
    }

    fun testNewPytestTestActionInsertsAfterMethodInClass() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            class TestSomething:
                def do(self):
                    ...
                <caret>
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            class TestSomething:
                def do(self):
                    ...

                def test_new(self):
                    pass
            """
        )
    }

    fun testNewPytestTestActionInsertsAtModuleLevelAfterClass() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true

        myFixture.configureByText(
            "test_sample.py",
            """
            class TestSomething:
                def do(self):
                    ...

            <caret>
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())
        action.actionPerformed(event)

        checkResult(
            """
            class TestSomething:
                def do(self):
                    ...

            def test_new():
                pass
            """
        )
    }

    fun testNewPytestActionsHiddenWhenDisabled() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = false

        myFixture.configureByText(
            "test_sample.py",
            """
            def test_sample():
                <caret>pass
            """.trimIndent()
        )

        val testAction = NewPytestTestAction()
        val testEvent = TestActionEvent.createTestEvent(testAction, editorDataContext())
        testAction.update(testEvent)

        val fixtureAction = NewPytestFixtureAction()
        val fixtureEvent = TestActionEvent.createTestEvent(fixtureAction, editorDataContext())
        fixtureAction.update(fixtureEvent)

        assertFalse("New pytest test action should be hidden when disabled", testEvent.presentation.isEnabledAndVisible)
        assertFalse(
            "New pytest fixture action should be hidden when disabled",
            fixtureEvent.presentation.isEnabledAndVisible
        )
    }

    fun testNewPytestTestSelections() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true
        myFixture.configureByText(
            "test_sample.py",
            """
            def test_existing():
                pass
            <caret>
            """.trimIndent()
        )

        val action = NewPytestTestAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())

        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)
        action.actionPerformed(event)

        checkResult(
            """
            def test_existing():
                pass
            def test_new():
                pass
            """
        )

        val editor = myFixture.editor
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText
        
        assertEquals("new", selectedText)
        
        val offset = editor.caretModel.offset
        val document = editor.document
        val textAfterCaret = document.charsSequence.subSequence(offset, offset + 1).toString()
        assertEquals("(", textAfterCaret)
    }

    fun testNewPytestFixtureSelections() {
        PluginSettingsState.instance().state.enableNewPytestMemberActions = true
        myFixture.configureByText(
            "test_sample.py",
            """
            import pytest
            
            <caret>
            """.trimIndent()
        )

        val action = NewPytestFixtureAction()
        val event = TestActionEvent.createTestEvent(action, editorDataContext())

        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)
        action.actionPerformed(event)

        checkResult(
            """
            import pytest
            
            @pytest.fixture
            def new_fixture():
                pass
            """
        )

        val editor = myFixture.editor
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText
        
        assertEquals("new_fixture", selectedText)
        
        val offset = editor.caretModel.offset
        val document = editor.document
        val textAfterCaret = document.charsSequence.subSequence(offset, offset + 1).toString()
        assertEquals("(", textAfterCaret)
    }

    private fun editorDataContext() = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, myFixture.editor)
        .add(CommonDataKeys.PSI_FILE, myFixture.file)
        .build()

    private fun checkResult(expected: String) {
        var normalized = expected.trimIndent()
        if (!normalized.endsWith("\n") && myFixture.file.text.endsWith("\n")) {
            normalized += "\n"
        }
        myFixture.checkResult(normalized)
    }
}
