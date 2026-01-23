package com.github.chbndrhnns.betterpy.features.intentions.customtype

import fixtures.TestBase
import fixtures.doRefactoringActionTest

class ParameterTest : TestBase() {

    private val actionId =
        "com.github.chbndrhnns.betterpy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testParameterDefaultValue_UnionType_UpdatesAnnotationAndWrapsValue() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def do(val: int | str | None = <caret>"2"): 
                pass
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            def do(val: int | Customstr | None = Customstr("2")): 
                pass
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testParameterDefaultValue_SimpleType_UpdatesAnnotationAndWrapsValue() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def do(val: int = <caret>1): 
                pass
            """,
            """
            class Customint(int):
                pass
            
            
            def do(val: Customint = Customint(1)): 
                pass
            """,
            actionId,
            renameTo = "Customint"
        )
    }

    fun testParameterAnnotationUpdatedWhenCustomTypeIntroducedOnArgument() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            class C:
                def do(self, val: str):
                    ...

                def other(self):
                    self.do("a<caret>bc")
            """,
            """
            class Customstr(str):
                __slots__ = ()


            class C:
                def do(self, val: Customstr):
                    ...

                def other(self):
                    self.do(Customstr("abc"))
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testParameterAnnotationUpdateWrapsCallSites() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            class C:
                def do(self, val: st<caret>r): ...
            
                def other(self):
                    self.do(str("abc"))
            """,
            """
            class Customstr(str):
                __slots__ = ()
            
            
            class C:
                def do(self, val: Customstr): ...
            
                def other(self):
                    self.do(Customstr(str("abc")))
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

}
