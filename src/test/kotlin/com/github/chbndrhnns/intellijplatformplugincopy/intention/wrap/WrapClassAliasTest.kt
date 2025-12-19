package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.doIntentionTest

class WrapClassAliasTest : TestBase() {

    fun testWrapDataclassArgumentViaAlias() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from dataclasses import dataclass

            @dataclass
            class Data:
                val: str

            class Test:
                item = Data

                def test_(self):
                    assert self.item(val=<caret>1)
            """,
            """
            from dataclasses import dataclass

            @dataclass
            class Data:
                val: str

            class Test:
                item = Data

                def test_(self):
                    assert self.item(val="1")
            """,
            "BetterPy: Wrap with str()"
        )
    }
}
