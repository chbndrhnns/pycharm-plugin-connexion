package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionNotAvailable
import fixtures.doRefactoringActionTest

class ForwardRefTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype.IntroduceCustomTypeRefactoringAction"

    fun testAssignment_UnionInString_UpdatesTextInsideString() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            val: "int | str | None" = <caret>2
            """,
            """
            class Customint(int):
                pass
            
            
            val: "Customint | str | None" = Customint(2)
            """,
            actionId,
            renameTo = "Customint"
        )
    }

    fun testParameter_UnionInString_UpdatesTextInsideString() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def do(val: "int | str | None" = <caret>2):
                pass
            """,
            """
            class Customint(int):
                pass
            
            
            def do(val: "Customint | str | None" = Customint(2)):
                pass
            """,
            actionId,
            renameTo = "Customint"
        )
    }

    fun testAssignment_MixedReferencesAndStrings_UpdatesStringPart() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            # This is valid python if types are defined
            val: str | "int" = <caret>2
            """,
            """
            class Customint(int):
                pass
            
            
            # This is valid python if types are defined
            val: str | "Customint" = Customint(2)
            """,
            actionId,
            renameTo = "Customint"
        )
    }

    fun testNotOffered_InForwardRefAnnotationString() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            val: "in<caret>t | str | None" = 2
            """,
            actionId
        )
    }
}
