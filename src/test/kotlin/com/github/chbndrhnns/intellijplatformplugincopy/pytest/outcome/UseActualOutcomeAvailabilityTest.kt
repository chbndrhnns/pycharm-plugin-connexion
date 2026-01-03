package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import fixtures.TestBase

class UseActualOutcomeAvailabilityTest : TestBase() {

    override fun setUp() {
        super.setUp()
        TestOutcomeDiffService.getInstance(myFixture.project).clearAll()
    }

    private fun setDiffData(qName: String, expected: String, actual: String) {
        val file = myFixture.file
        // SMTestProxy.locationUrl uses the parent directory path in angle brackets
        // and prefixes the qName with the parent directory name (module name)
        val parentDir = file.virtualFile.parent
        val parentDirPath = parentDir.path
        val parentDirName = parentDir.name
        val key = "python<$parentDirPath>://$parentDirName.$qName"
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

    fun `test intention is available on assert statement without diff data`() {
        myFixture.configureByText(
            "test_no_diff.py",
            """
            def test_no_diff():
                ass<caret>ert 1 == 2
            """.trimIndent(),
        )

        // No diff data set - intention should still be available
        val intention = myFixture.getAvailableIntention("BetterPy: Use actual test outcome")
        assertNotNull("Intention should be available on assert with == even without diff data", intention)
    }
}
