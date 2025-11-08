package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

class DataclassesTest : TestBase() {

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
}