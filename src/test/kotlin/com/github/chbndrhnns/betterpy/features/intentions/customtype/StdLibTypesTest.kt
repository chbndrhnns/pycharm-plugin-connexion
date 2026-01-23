package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionAvailable

class StdLibTypesTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testDatetime_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            import datetime
            
            def f(d: datetime.date<caret>time):
                pass
            """,
            actionId
        )
    }

    fun testDate_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            import datetime
            
            def f(d: datetime.da<caret>te):
                pass
            """,
            actionId
        )
    }

    fun testUUID_IntentionAvailable() {
        myFixture.assertRefactoringActionAvailable(
            "a.py",
            """
            import uuid
            
            def f(id: uuid.U<caret>UID):
                pass
            """,
            actionId
        )
    }
}
