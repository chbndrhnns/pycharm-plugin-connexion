package com.github.chbndrhnns.intellijplatformplugincopy.intention.populate

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class PopulateLeafImportsTest : TestBase() {

    fun testNewTypeImport() {
        myFixture.addFileToProject(
            "models.py",
            """
            from dataclasses import dataclass
            from typing import NewType

            UserId = NewType('UserId', str)
            
            @dataclass
            class User:
                uid: UserId
            """.trimIndent()
        )

        withPopulatePopupSelection(index = 2) { // 2 = Recursive
            myFixture.doIntentionTest(
                "main.py",
                """
                from .models import User
                
                def test():
                    u = User(<caret>)
                """,
                """
                from .models import User, UserId
                
                def test():
                    u = User(uid=UserId(...))
                """,
                "Populate arguments..."
            )
        }
    }
}
