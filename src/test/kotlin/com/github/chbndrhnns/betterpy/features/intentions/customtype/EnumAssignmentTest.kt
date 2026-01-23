package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionNotAvailable

class EnumAssignmentTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testEnumAssignment_DoNotOfferCustomType() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            from enum import Enum
            
            class Color(Enum):
                RED = "r<caret>ed"
                GREEN = "green"
            """,
            actionId
        )
    }
}
