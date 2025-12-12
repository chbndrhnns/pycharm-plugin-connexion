package com.github.chbndrhnns.intellijplatformplugincopy.intention.signature

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import fixtures.TestBase

class StripSignatureTypeAnnotationsIntentionTest : TestBase() {

    fun testStripsParameterAndReturnAnnotationsFromFunction() {
        myFixture.configureByText(
            "a.py",
            """
            def <caret>foo(x: int, y: str = "a") -> bool:
                return True
            """.trimIndent()
        )

        myFixture.launchAction(StripSignatureTypeAnnotationsIntention())

        myFixture.checkResult(
            """
            def foo(x, y="a"):
                return True
        """.trimIndent() + "\n"
        )
    }

    fun testStripsAnnotationsFromMethodIncludingVarArgsKwArgs() {
        myFixture.configureByText(
            "a.py",
            """
            class A:
                def <caret>bar(self, x: int, *args: str, **kwargs: int) -> None:
                    pass
            """.trimIndent()
        )
        myFixture.launchAction(StripSignatureTypeAnnotationsIntention())

        myFixture.checkResult(
            """
            class A:
                def bar(self, x, *args, **kwargs):
                    pass
        """.trimIndent() + "\n"
        )

        val text = myFixture.file.text
        assertTrue(
            "Expected annotations to be removed without changing varargs/kwargs",
            Regex("def bar\\(self, x, \\*args, \\*\\*kwargs\\):").containsMatchIn(text)
        )
        assertFalse(text.contains(": int"))
        assertFalse(text.contains(": str"))
        assertFalse(text.contains("->"))
    }

    fun testNotAvailableWhenNoSignatureAnnotations() {
        myFixture.configureByText(
            "a.py",
            """
            def <caret>foo(x, y=1):
                return True
            """.trimIndent()
        )

        assertNull(myFixture.availableIntentions.find { it.familyName == "Strip signature type annotations" })
    }

    fun testDisabledViaSettingsToggle() {
        PluginSettingsState.instance().state.enableStripSignatureTypeAnnotationsIntention = false
        myFixture.configureByText(
            "a.py",
            """
            def <caret>foo(x: int) -> int:
                return x
            """.trimIndent()
        )

        assertNull(myFixture.availableIntentions.find { it.familyName == "Strip signature type annotations" })
    }
}
