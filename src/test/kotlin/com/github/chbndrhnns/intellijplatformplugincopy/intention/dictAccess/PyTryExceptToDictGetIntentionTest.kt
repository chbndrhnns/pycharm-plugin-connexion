package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import fixtures.TestBase

class PyTryExceptToDictGetIntentionTest : TestBase() {
    override fun getTestDataPath(): String = "src/test/testData/intention/dictAccess/tryExcept"

    fun testTryExceptAssignment() {
        doTest("Replace try-except with dict.get")
    }

    fun testTryExceptPass() {
        doTest("Replace try-except with dict.get")
    }

    fun testTryExceptReturn() {
        doTest("Replace try-except with dict.get")
    }

    fun testTryExceptNone() {
        doTest("Replace try-except with dict.get")
    }

    fun testTryExceptComplexDefault() {
        doTestNotAvailable("Replace try-except with dict.get")
    }

    fun testTryExceptNested() {
        doTestNotAvailable("Replace try-except with dict.get")
    }

    fun testTryExceptMixed() {
        doTestNotAvailable("Replace try-except with dict.get")
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
