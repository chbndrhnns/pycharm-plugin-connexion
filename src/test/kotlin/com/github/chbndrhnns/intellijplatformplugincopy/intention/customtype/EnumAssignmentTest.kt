package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class EnumAssignmentTest : TestBase() {

    fun testEnumAssignment_DoNotOfferCustomType() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from enum import Enum
            
            class Color(Enum):
                RED = "r<caret>ed"
                GREEN = "green"
            """,
            "BetterPy: Introduce custom type from str"
        )
    }
}
