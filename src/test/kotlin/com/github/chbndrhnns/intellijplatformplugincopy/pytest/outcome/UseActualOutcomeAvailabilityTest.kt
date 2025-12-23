package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.openapi.roots.ProjectRootManager
import fixtures.TestBase

class UseActualOutcomeAvailabilityTest : TestBase() {

    override fun setUp() {
        super.setUp()
        TestOutcomeDiffService.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        val file = myFixture.file
        val root = ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(file.virtualFile)
            ?: ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file.virtualFile)
        val path = root?.path ?: ""
        val key = "python<$path>://$qName"
        TestOutcomeDiffService.getInstance(project).put(key, OutcomeDiff(expected, actual))
    }

    fun `test intention is NOT available for assert False`() {
        myFixture.configureByText(
            "test_false.py",
            """
            def test_false():
                assert Fal<caret>se
            """.trimIndent(),
        )

        setDiffData("test_false.test_false", "True", "False")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNull("Intention should not be available for 'assert False'", intention)
    }

    fun `test intention is NOT available for binary operations other than comparison`() {
        myFixture.configureByText(
            "test_binary.py",
            """
            def test_binary():
                assert 1 +<caret> 1
            """.trimIndent(),
        )

        setDiffData("test_binary.test_binary", "2", "2")

        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNull(
            "Intention should not be available for binary expressions that are not equality comparisons",
            intention
        )
    }
}
