package com.github.chbndrhnns.betterpy.features.intentions.dictAccess

import fixtures.TestBase

class PyDictAccessIntentionTest : TestBase() {
    override fun getTestDataPath(): String = "src/test/testData/intention/dictAccess"

    fun testBracketToGet() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testGetToBracket() {
        doTest("BetterPy: Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testNoQuickFixOnAssignment() {
        doTestNotAvailable("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testSkipOnDefaultArg() {
        doTestNotAvailable("BetterPy: Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testPreserveComments() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testTypedDictOptional() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testShadowedGet() {
        doTestNotAvailable("BetterPy: Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testVariableKey() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testComplexOperand() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testTupleKey() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testExistingParens() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testKeywordArg() {
        doTest("BetterPy: Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testInheritance() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testInsideExpression() {
        doTest("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testAugmentedAssignment() {
        doTestNotAvailable("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testDeletion() {
        doTestNotAvailable("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testUnpackingTarget() {
        doTestNotAvailable("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testNoArgs() {
        doTestNotAvailable("BetterPy: Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testMultipleArgs() {
        doTestNotAvailable("BetterPy: Replace 'dict.get(key)' with 'dict[key]'")
    }

    fun testNonMapping() {
        doTestNotAvailable("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
    }

    fun testNonMappingList() {
        doTestNotAvailable("BetterPy: Replace 'dict[key]' with 'dict.get(key)'")
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
