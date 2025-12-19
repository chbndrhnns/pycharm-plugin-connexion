package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import fixtures.TestBase

class PyDictGetToTryExceptIntentionTest : TestBase() {
    override fun getTestDataPath(): String = "src/test/testData/intention/dictAccess/getToTryExcept"

    fun testAssignment() {
        doTest("BetterPy: Replace 'dict.get(key, default)' with try/except KeyError")
    }

    fun testReturn() {
        doTest("BetterPy: Replace 'dict.get(key, default)' with try/except KeyError")
    }

    fun testNoDefault() {
        doTestNotAvailable("BetterPy: Replace 'dict.get(key, default)' with try/except KeyError")
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