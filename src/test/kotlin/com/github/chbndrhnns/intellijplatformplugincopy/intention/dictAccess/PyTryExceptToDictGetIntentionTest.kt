package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import fixtures.TestBase

class PyTryExceptToDictGetIntentionTest : TestBase() {
    override fun getTestDataPath(): String = "src/test/testData/intention/dictAccess/tryExcept"

    fun testTryExceptAssignment() {
        doTest("BetterPy: Replace try-except with dict.get")
    }

    fun testTryExceptPass() {
        doTest("BetterPy: Replace try-except with dict.get")
    }

    fun testTryExceptReturn() {
        doTest("BetterPy: Replace try-except with dict.get")
    }

    fun testTryExceptNone() {
        doTest("BetterPy: Replace try-except with dict.get")
    }

    fun testTryExceptComplexDefault() {
        doTestNotAvailable("BetterPy: Replace try-except with dict.get")
    }

    fun testTryExceptNested() {
        doTestNotAvailable("BetterPy: Replace try-except with dict.get")
    }

    fun testTryExceptMixed() {
        doTestNotAvailable("BetterPy: Replace try-except with dict.get")
    }

    private fun doTest(intentionName: String) {
        myFixture.configureByFile(getTestName(false) + ".py")
        val intention = myFixture.findSingleIntention(intentionName)
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(getTestName(false) + "_after.py")
    }

    private fun doTestNotAvailable(intentionName: String) {
        myFixture.configureByFile(getTestName(false) + ".py")
        assertEmpty(myFixture.filterAvailableIntentions(intentionName))
    }
}
