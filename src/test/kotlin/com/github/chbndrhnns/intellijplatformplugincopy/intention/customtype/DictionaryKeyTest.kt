package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.doIntentionTest

class DictionaryKeyTest : TestBase() {

    fun testDictKeyAccess_UpdatesAnnotation() {
        myFixture.doIntentionTest(
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
            "BetterPy: Introduce custom type from str",
            renameTo = "Customstr"
        )
    }
}
