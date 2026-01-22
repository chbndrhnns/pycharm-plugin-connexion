package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionNotAvailable

class ImportStatementTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testIntentionNotOfferedOnImportStatement() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            import <caret>typing
            """,
            actionId
        )
    }

    fun testIntentionNotOfferedOnFromImportStatement() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            from typing import <caret>List
            """,
            actionId
        )
    }

    fun testIntentionNotOfferedOnImportedConstant() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            from typing import <caret>final
            """,
            actionId
        )
    }
}
