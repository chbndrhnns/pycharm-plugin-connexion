package fixtures

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Executes a refactoring action test:
 * 1. Configures the file with [before] text.
 * 2. Runs highlighting.
 * 3. Finds the action by [actionId].
 * 4. Performs the action.
 * 5. Checks the result against [after] text.
 */
fun CodeInsightTestFixture.doRefactoringTest(
    filename: String,
    before: String,
    after: String,
    actionId: String
) {
    configureByText(filename, before.trimIndent())
    doHighlighting()

    val action = ActionManager.getInstance().getAction(actionId)
        ?: throw AssertionError("Action with ID '$actionId' not found")

    val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .add(CommonDataKeys.PSI_FILE, file)
        .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile)
        .build()

    val event =
        AnActionEvent.createEvent(dataContext, action.templatePresentation.clone(), "unknown", ActionUiKind.NONE, null)
    action.actionPerformed(event)

    var expected = after.trimIndent()
    if (!expected.endsWith("\n") && file.text.endsWith("\n")) {
        expected += "\n"
    }
    checkResult(expected)
}

/**
 * Asserts that a refactoring action is NOT available at the current caret position.
 */
fun CodeInsightTestFixture.assertActionNotAvailable(
    filename: String,
    text: String,
    actionId: String
) {
    configureByText(filename, text.trimIndent())
    doHighlighting()

    val action = ActionManager.getInstance().getAction(actionId)
        ?: throw AssertionError("Action with ID '$actionId' not found")

    val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .add(CommonDataKeys.PSI_FILE, file)
        .build()

    val event =
        AnActionEvent.createEvent(dataContext, action.templatePresentation.clone(), "unknown", ActionUiKind.NONE, null)
    action.update(event)

    if (event.presentation.isEnabledAndVisible) {
        throw AssertionError("Action '$actionId' should NOT be available")
    }
}
