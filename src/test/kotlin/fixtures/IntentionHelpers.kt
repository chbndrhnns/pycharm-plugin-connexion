package fixtures

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.RenameDialogInterceptor
import com.intellij.ui.UiInterceptors

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
    renameTo: String? = null
) {
    if (renameTo != null) {
        UiInterceptors.register(RenameDialogInterceptor(renameTo))
    }

    configureByText(filename, before.trimIndent())
    doHighlighting()
    val intention = findSingleIntention(intentionName)
    launchAction(intention)

    if (renameTo != null) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    checkResult(after.trimIndent())
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
