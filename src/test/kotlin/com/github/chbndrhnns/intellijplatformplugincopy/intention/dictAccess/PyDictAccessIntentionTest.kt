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

    fun testPreserveComments() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testTypedDictOptional() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testShadowedGet() {
        doTestNotAvailable("Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testVariableKey() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testComplexOperand() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testTupleKey() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testExistingParens() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testKeywordArg() {
        doTest("Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testInheritance() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testInsideExpression() {
        doTest("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testAugmentedAssignment() {
        doTestNotAvailable("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testDeletion() {
        doTestNotAvailable("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testUnpackingTarget() {
        doTestNotAvailable("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testNoArgs() {
        doTestNotAvailable("Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testMultipleArgs() {
        doTestNotAvailable("Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testNonMapping() {
        doTestNotAvailable("Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testNonMappingList() {
        doTestNotAvailable("Replace 'dict[key]' with 'dict.get(key)'")
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
