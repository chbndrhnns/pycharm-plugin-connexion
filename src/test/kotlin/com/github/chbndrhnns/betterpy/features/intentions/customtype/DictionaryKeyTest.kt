package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.doRefactoringActionTest

class DictionaryKeyTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testDictKeyAccess_UpdatesAnnotation() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            d: dict[str, int] = {}
            val = d["<caret>abc"]
            """,
            """
            class Customstr(str):
                __slots__ = ()


            d: dict[Customstr, int] = {}
            val = d[Customstr("abc")]
            """,
            actionId,
            renameTo = "Customstr"
        )
    }
}
