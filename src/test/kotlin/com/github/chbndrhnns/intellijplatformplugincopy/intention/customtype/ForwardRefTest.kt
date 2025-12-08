package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class ForwardRefTest : TestBase() {

    fun testAssignment_UnionInString_UpdatesTextInsideString() {
        myFixture.doIntentionTest(
            "a.py",
            """
            val: "int | str | None" = <caret>2
            """,
            """
            class Customint(int):
                pass
            
            
            val: "Customint | str | None" = Customint(2)
            """,
            "Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testParameter_UnionInString_UpdatesTextInsideString() {
        myFixture.doIntentionTest(
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
            "Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testAssignment_MixedReferencesAndStrings_UpdatesStringPart() {
        myFixture.doIntentionTest(
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
            "Introduce custom type from int",
            renameTo = "Customint"
        )
    }

    fun testNotOffered_InForwardRefAnnotationString() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            val: "in<caret>t | str | None" = 2
            """,
            "Introduce custom type"
        )
    }
}
