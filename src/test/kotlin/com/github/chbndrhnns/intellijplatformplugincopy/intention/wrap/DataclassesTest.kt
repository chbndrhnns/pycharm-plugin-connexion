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
}