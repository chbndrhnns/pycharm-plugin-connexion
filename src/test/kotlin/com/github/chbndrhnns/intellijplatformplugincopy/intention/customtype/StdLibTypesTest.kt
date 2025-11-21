package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

class StdLibTypesTest : TestBase() {

    fun testDatetime_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            import datetime
            
            def f(d: datetime.date<caret>time):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from datetime")
        assertNotEmpty(intentions)
    }

    fun testDate_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            import datetime
            
            def f(d: datetime.da<caret>te):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from date")
        assertNotEmpty(intentions)
    }

    fun testUUID_IntentionAvailable() {
        myFixture.configureByText(
            "a.py",
            """
            import uuid
            
            def f(id: uuid.U<caret>UID):
                pass
            """.trimIndent()
        )

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from UUID")
        assertNotEmpty(intentions)
    }
}
