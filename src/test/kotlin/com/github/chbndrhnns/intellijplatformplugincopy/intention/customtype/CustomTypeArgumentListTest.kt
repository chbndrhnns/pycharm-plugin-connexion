package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase
import com.jetbrains.python.inspections.PyArgumentListInspection

class CustomTypeArgumentListTest : TestBase() {

    fun testIntentionNotAvailable_WhenArgumentListErrorPresent() {
        myFixture.enableInspections(PyArgumentListInspection::class.java)
        myFixture.configureByText(
            "a.py",
            """
            def foo(a: int):
                pass

            foo(a=1, b=<caret>2)
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type")
        assertEmpty("Intention should not be available when argument list error is present", intentions)
    }
}
