package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

/**
 * Dataclass-related wrap tests.
 */
class WrapDataclassesTest : TestBase() {

    fun testWrapDataclassArgument() {
        myFixture.configureByText(
            "a.py", """
            import dataclasses
            from collections import UserString
            
            
            class OtherStr(UserString):
                ...
            
            
            @dataclasses.dataclass
            class Data:
                val: OtherStr
            
            
            def test_():
                Data(val="<caret>abc")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with OtherStr()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import dataclasses
            from collections import UserString
            
            
            class OtherStr(UserString):
                ...
            
            
            @dataclasses.dataclass
            class Data:
                val: OtherStr
            
            
            def test_():
                Data(val=OtherStr("abc"))
            """.trimIndent()
        )
    }

    fun testWrapDataclassArgumentWhenCaretOnArgName() {
        myFixture.configureByText(
            "a.py", """
            import dataclasses
            from collections import UserString
            
            
            class OtherStr(UserString):
                ...
            
            
            @dataclasses.dataclass
            class Data:
                val: OtherStr
            
            
            def test_():
                Data(v<caret>al="abc")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with OtherStr()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import dataclasses
            from collections import UserString
            
            
            class OtherStr(UserString):
                ...
            
            
            @dataclasses.dataclass
            class Data:
                val: OtherStr
            
            
            def test_():
                Data(val=OtherStr("abc"))
            """.trimIndent()
        )
    }

    fun testWrapDataclassArgumentWithPep604UnionChoosesFirstNonNone() {
        myFixture.configureByText(
            "a.py", """
            import dataclasses
            
            
            @dataclasses.dataclass
            class Data:
                val: int | None
            
            
            def test_():
                Data(val="<caret>3")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap with int()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            import dataclasses
            
            
            @dataclasses.dataclass
            class Data:
                val: int | None
            
            
            def test_():
                Data(val=int("3"))
            """.trimIndent()
        )
    }

    fun testWrapDataclassListOfNewType_wrapsElements() {
        myFixture.configureByText(
            "a.py", """
            from dataclasses import dataclass
            from typing import NewType

            Item = NewType("Item", str)

            @dataclass
            class Data:
                items: list[Item]

            def test_():
                items = ["abc"]
                Data(items=i<caret>tems)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Wrap items with Item()")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from dataclasses import dataclass
            from typing import NewType

            Item = NewType("Item", str)

            @dataclass
            class Data:
                items: list[Item]

            def test_():
                items = ["abc"]
                Data(items=[Item(v) for v in items])
            """.trimIndent()
        )
    }

    fun testWrapDataclassListOfUnionNewType_showsChooserAndAppliesChoice() {
        val fake = FakePopupHost().apply { selectedIndex = 0 }
        com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks.popupHost = fake
        try {
            myFixture.configureByText(
                "a.py", """
            from dataclasses import dataclass
            from typing import NewType

            Item = NewType("Item", str)
            Item2 = NewType("Item2", str)

            @dataclass
            class Data:
                items: list[Item | Item2]

            def test_():
                items = ["abc"]
                Data(items=i<caret>tems)
            """.trimIndent()
            )

            myFixture.doHighlighting()
            val intention = myFixture.findSingleIntention("Wrap with expected union type…")
            myFixture.launchAction(intention)

            myFixture.checkResult(
                """
            from dataclasses import dataclass
            from typing import NewType

            Item = NewType("Item", str)
            Item2 = NewType("Item2", str)

            @dataclass
            class Data:
                items: list[Item | Item2]

            def test_():
                items = ["abc"]
                Data(items=[Item(v) for v in items])
            """.trimIndent()
            )
        } finally {
            com.github.chbndrhnns.intellijplatformplugincopy.intention.WrapWithExpectedTypeIntentionHooks.popupHost =
                null
        }
    }
}