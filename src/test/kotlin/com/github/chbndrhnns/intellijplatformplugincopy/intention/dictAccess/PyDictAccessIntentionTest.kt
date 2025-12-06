package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import fixtures.TestBase

class PyDictAccessIntentionTest : TestBase() {
    override fun getTestDataPath(): String = "src/test/testData/intention/dictAccess"

    fun testBracketToGet() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testGetToBracket() {
        doTest("Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testNoQuickFixOnAssignment() {
        doTestNotAvailable("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testSkipOnDefaultArg() {
        doTestNotAvailable("Replace 'dict.get(key)' with 'dict[key]'")
    }

    /*
    fun testPreserveComments() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }
    */

    fun testTypedDictOptional() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testShadowedGet() {
        doTestNotAvailable("Replace 'dict.get(key)' with 'dict[key]'")
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
