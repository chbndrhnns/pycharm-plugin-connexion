package com.github.chbndrhnns.intellijplatformplugincopy.intention.protocol

import fixtures.TestBase

class CallableToProtocolAdvancedTest : TestBase() {

    fun testStringAnnotation() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable
            
            x: "Call<caret>able[[int], str]"
        """.trimIndent()
        )

        if (myFixture.availableIntentions.any { it.text == "BetterPy: Convert callable to Protocol" }) {
            myFixture.launchAction(myFixture.findSingleIntention("BetterPy: Convert callable to Protocol"))

            val hostFile =
                com.intellij.lang.injection.InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
            val actual = hostFile.text
            val expected = """
            from typing import Callable, Protocol
            
            
            class MyProtocol(Protocol):
                def __call__(self, arg0: int) -> str: ...


            x: "MyProtocol"
            """.trimIndent()

            if (actual.trim() != expected.trim()) {
                throw RuntimeException("Mismatch! Actual:\n$actual\n\nExpected:\n$expected")
            }
        } else {
            throw AssertionError("Intention 'BetterPy: Convert callable to Protocol' not found")
        }
    }

    fun testAliasedImport() {
        myFixture.configureByText(
            "a.py", """
            from typing import Callable as C
            
            x: C<caret>[[int], str]
        """.trimIndent()
        )

        val action = myFixture.availableIntentions.find { it.text == "BetterPy: Convert callable to Protocol" }
        if (action != null) {
            myFixture.launchAction(action)
            myFixture.checkResult(
                """
            from typing import Callable as C, Protocol
            
            
            class MyProtocol(Protocol):
                def __call__(self, arg0: int) -> str: ...


            x: MyProtocol
            """.trimIndent() + "\n"
            )
        } else {
            throw AssertionError("Intention 'BetterPy: Convert callable to Protocol' not found for aliased import")
        }
    }
}
