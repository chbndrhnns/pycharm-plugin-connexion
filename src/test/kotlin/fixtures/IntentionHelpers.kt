package fixtures

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.RenameDialogInterceptor
import com.intellij.ui.UiInterceptors

/**
 * A UiInterceptor that closes DialogWrapper dialogs with OK.
 */
class DialogOkInterceptor : UiInterceptors.UiInterceptor<DialogWrapper>(DialogWrapper::class.java) {
    override fun doIntercept(component: DialogWrapper) {
        component.close(DialogWrapper.OK_EXIT_CODE)
    }
}

/**
 * Executes a standard intention test:
 * 1. Configures the file with [before] text.
 * 2. Runs highlighting.
 * 3. Finds the intention by [intentionName].
 * 4. Launches the intention.
 * 5. Checks the result against [after] text.
 */
fun CodeInsightTestFixture.doIntentionTest(
    filename: String,
    before: String,
    after: String,
    intentionName: String,
    renameTo: String? = null,
    dialogOk: Boolean = false
) {
    if (renameTo != null) {
        UiInterceptors.register(RenameDialogInterceptor(renameTo))
    }

    if (dialogOk) {
        UiInterceptors.register(DialogOkInterceptor())
    }

    configureByText(filename, before.trimIndent())
    doHighlighting()
    val intention = findSingleIntention(intentionName)
    launchAction(intention)

    if (renameTo != null) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    var expected = after.trimIndent()
    if (!expected.endsWith("\n") && file.text.endsWith("\n")) {
        expected += "\n"
    }
    checkResult(expected)
}

/**
 * Asserts that an intention starting with [intentionName] is NOT available.
 */
fun CodeInsightTestFixture.assertIntentionNotAvailable(
    filename: String,
    text: String,
    intentionName: String
) {
    configureByText(filename, text.trimIndent())
    doHighlighting()
    val intention = availableIntentions.find { it.text.startsWith(intentionName) }
    if (intention != null) {
        throw AssertionError("Intention '$intentionName' should NOT be available")
    }
}

/**
 * Asserts that an intention starting with [intentionName] IS available.
 */
fun CodeInsightTestFixture.assertIntentionAvailable(
    filename: String,
    text: String,
    intentionName: String
) {
    configureByText(filename, text.trimIndent())
    doHighlighting()
    val intention = availableIntentions.find { it.text.startsWith(intentionName) }
    if (intention == null) {
        throw AssertionError("Intention '$intentionName' should be available")
    }
}
