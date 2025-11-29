package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionAvailable

class StdLibTypesTest : TestBase() {

    fun testDatetime_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            import datetime
            
            def f(d: datetime.date<caret>time):
                pass
            """,
            "Introduce custom type from datetime"
        )
    }

    fun testDate_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            import datetime
            
            def f(d: datetime.da<caret>te):
                pass
            """,
            "Introduce custom type from date"
        )
    }

    fun testUUID_IntentionAvailable() {
        myFixture.assertIntentionAvailable(
            "a.py",
            """
            import uuid
            
            def f(id: uuid.U<caret>UID):
                pass
            """,
            "Introduce custom type from UUID"
        )
    }
}
