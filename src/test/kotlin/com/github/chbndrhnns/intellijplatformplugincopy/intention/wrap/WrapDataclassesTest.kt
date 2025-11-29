package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import fixtures.TestBase
import fixtures.doIntentionTest
import fixtures.withWrapPopupSelection

/**
 * Dataclass-related wrap tests.
 */
class WrapDataclassesTest : TestBase() {

    fun testWrapDataclassArgument() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import dataclasses
            from collections import UserString
            
            
            class OtherStr(UserString):
                ...
            
            
            @dataclasses.dataclass
            class Data:
                val: OtherStr
            
            
            def test_():
                Data(val="<caret>abc")
            """,
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
            """,
            "Wrap with OtherStr()"
        )
    }

    fun testWrapDataclassArgumentWhenCaretOnArgName() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import dataclasses
            from collections import UserString
            
            
            class OtherStr(UserString):
                ...
            
            
            @dataclasses.dataclass
            class Data:
                val: OtherStr
            
            
            def test_():
                Data(v<caret>al="abc")
            """,
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
            """,
            "Wrap with OtherStr()"
        )
    }

    fun testWrapDataclassArgumentWithPep604UnionChoosesFirstNonNone() {
        myFixture.doIntentionTest(
            "a.py",
            """
            import dataclasses
            
            
            @dataclasses.dataclass
            class Data:
                val: int | None
            
            
            def test_():
                Data(val="<caret>3")
            """,
            """
            import dataclasses
            
            
            @dataclasses.dataclass
            class Data:
                val: int | None
            
            
            def test_():
                Data(val=int("3"))
            """,
            "Wrap with int()"
        )
    }

    fun testWrapDataclassListOfNewType_wrapsElements() {
        myFixture.doIntentionTest(
            "a.py",
            """
            from dataclasses import dataclass
            from typing import NewType

            Item = NewType("Item", str)

            @dataclass
            class Data:
                items: list[Item]

            def test_():
                items = ["abc"]
                Data(items=i<caret>tems)
            """,
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
            """,
            "Wrap items with Item()"
        )
    }

    fun testWrapDataclassListOfUnionNewType_showsChooserAndAppliesChoice() {
        withWrapPopupSelection(0) { fake ->
            myFixture.doIntentionTest(
                "a.py",
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
                    Data(items=i<caret>tems)
                """,
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
                """,
                "Wrap with expected union type…"
            )
        }
    }
}