package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.populate

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withPopulatePopupSelection

class PopulateNewTypeContainerTest : TestBase() {

    fun testListWithNewType() {
        withPopulatePopupSelection(index = 2) { // Recursive
            myFixture.doIntentionTest(
                "a.py",
                """
                from dataclasses import dataclass
                from typing import NewType

                MyStr = NewType("MyStr", str)

                @dataclass
                class Data:
                    val: list[MyStr]

                d = Data(<caret>)
                """,
                """
                from dataclasses import dataclass
                from typing import NewType

                MyStr = NewType("MyStr", str)

                @dataclass
                class Data:
                    val: list[MyStr]

                d = Data(val=[MyStr(...)])
                """,
                "BetterPy: Populate arguments..."
            )
        }
    }
}
