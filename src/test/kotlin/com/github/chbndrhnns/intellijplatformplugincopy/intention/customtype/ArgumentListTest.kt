package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.jetbrains.python.inspections.PyArgumentListInspection
import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class ArgumentListTest : TestBase() {

    fun testIntentionNotAvailable_WhenArgumentListErrorPresent() {
        myFixture.enableInspections(PyArgumentListInspection::class.java)
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def foo(a: int):
                pass

            foo(a=1, b=<caret>2)
            """,
            "Introduce custom type"
        )
    }
}
