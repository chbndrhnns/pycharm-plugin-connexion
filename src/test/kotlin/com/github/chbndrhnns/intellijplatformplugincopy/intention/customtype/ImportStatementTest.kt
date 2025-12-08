package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class ImportStatementTest : TestBase() {

    fun testIntentionNotOfferedOnImportStatement() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            import <caret>typing
            """,
            "Introduce custom type"
        )
    }

    fun testIntentionNotOfferedOnFromImportStatement() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import <caret>List
            """,
            "Introduce custom type"
        )
    }
    
    fun testIntentionNotOfferedOnImportedConstant() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            from typing import <caret>final
            """,
            "Introduce custom type"
        )
    }
}
