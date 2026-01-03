package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertRefactoringActionNotAvailable
import fixtures.doRefactoringActionTest

class LiteralsTest : TestBase() {

    private val actionId = "com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype.IntroduceCustomTypeRefactoringAction"

    fun testString_NoAnnotation_WrapsWithCustomStr() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def expect_str(s) -> None:
                ...

            expect_str("ab<caret>c")
            """,
            """
            class Customstr(str):
                __slots__ = ()


            def expect_str(s: Customstr) -> None:
                ...

            expect_str(Customstr("abc"))
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testString_Annotation_UpdatesAnnotationAfterWrap() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def expect_str(s: str) -> None:
                ...

            expect_str("ab<caret>c")
            """,
            """
            class Customstr(str):
                __slots__ = ()


            def expect_str(s: Customstr) -> None:
                ...

            expect_str(Customstr("abc"))
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testStringAssignment_NoAnnotation_WrapsWithCustomStr() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            val = "s<caret>tr"
            """,
            """
            class Customstr(str):
                __slots__ = ()


            val = Customstr("str")
            """,
            actionId,
            renameTo = "Customstr"
        )
    }

    fun testFloatAssignment_UsesFloatAndWrapsValue() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            val = 4567.<caret>6
            """,
            """
            class Customfloat(float):
                pass
            
            
            val = Customfloat(4567.6)
            """,
            actionId,
            renameTo = "Customfloat"
        )
    }

    fun testIntAssignment_WithSnakeCaseName_UsesTargetNameForClass() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            product_id = 12<caret>34
            """,
            """
            class ProductId(int):
                pass


            product_id = ProductId(1234)
            """,
            actionId
        )
    }

    fun testKeywordArgument_WithSnakeCaseName_UsesKeywordForClass() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def do(my_arg) -> None:
                ...

            def test_():
                do(my_arg=12<caret>34)
            """,
            """
            class MyArg(int):
                pass
            
            
            def do(my_arg: MyArg) -> None:
                ...
            
            def test_():
                do(my_arg=MyArg(1234))
            """.trimIndent(),
            actionId
        )
    }

    fun testDictValue_WhenExpectedTypeIsAlreadyCustom_DoesNotOfferIntention() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            class CustomInt(int):
                pass


            val: dict[str, CustomInt] = {"a": <caret>1, "b": 2, "c": 3}
            """,
            actionId
        )
    }

    fun testDictKey_WhenExpectedTypeIsAlreadyCustom_DoesNotOfferIntention() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            class CustomStr(str):
                pass


            val: dict[CustomStr, int] = {<caret>"a": 1, "b": 2}
            """,
            actionId
        )
    }

    fun testParameterDefaultValue_WithSnakeCaseName_UsesParameterNameForClass() {
        myFixture.doRefactoringActionTest(
            "a.py",
            """
            def extract_saved_reels(self, output_dir: str = "saved_ree<caret>ls"):
                pass
            """,
            """
            class OutputDir(str):
                __slots__ = ()


            def extract_saved_reels(self, output_dir: OutputDir = OutputDir("saved_reels")):
                pass
            """,
            actionId
        )
    }

    fun testModuleDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            \"\"\"Module doc<caret>string\"\"\"

            def f(x: int) -> None:
                ...
            """,
            actionId
        )
    }

    fun testFunctionDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            def f(x: int) -> None:
                \"\"\"Function doc<caret>string\"\"\"

                return x
            """,
            actionId
        )
    }

    fun testClassDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            class C:
                \"\"\"Class doc<caret>string\"\"\"

                def __init__(self, x: int) -> None:
                    self.x = x
            """,
            actionId
        )
    }

    fun testClassDocstring_NotFirstStatement_DoesNotOfferCustomTypeIntention() {
        myFixture.assertRefactoringActionNotAvailable(
            "a.py",
            """
            class EnumWithList: pass
            
            class MyVariant(str, EnumWithList):
                __slots__ = ()
                ""<caret>"Some documentation""${'"'}
            """,
            actionId
        )
    }
}
