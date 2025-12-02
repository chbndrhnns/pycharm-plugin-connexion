package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable
import fixtures.doIntentionTest

class LiteralsTest : TestBase() {

    fun testString_NoAnnotation_WrapsWithCustomStr() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def expect_str(s) -> None:
                ...

            expect_str("ab<caret>c")
            """,
            """
            class Customstr(str):
                pass


            def expect_str(s: Customstr) -> None:
                ...

            expect_str(Customstr("abc"))
            """,
            "Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testString_Annotation_UpdatesAnnotationAfterWrap() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def expect_str(s: str) -> None:
                ...

            expect_str("ab<caret>c")
            """,
            """
            class Customstr(str):
                pass


            def expect_str(s: Customstr) -> None:
                ...

            expect_str(Customstr("abc"))
            """,
            "Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testStringAssignment_NoAnnotation_WrapsWithCustomStr() {
        myFixture.doIntentionTest(
            "a.py",
            """
            val = "s<caret>tr"
            """,
            """
            class Customstr(str):
                pass


            val = Customstr("str")
            """,
            "Introduce custom type from str",
            renameTo = "Customstr"
        )
    }

    fun testFloatAssignment_UsesFloatAndWrapsValue() {
        myFixture.doIntentionTest(
            "a.py",
            """
            val = 4567.<caret>6
            """,
            """
            class Customfloat(float):
                pass
            
            
            val = Customfloat(4567.6)
            """,
            "Introduce custom type from float",
            renameTo = "Customfloat"
        )
    }

    fun testIntAssignment_WithSnakeCaseName_UsesTargetNameForClass() {
        myFixture.doIntentionTest(
            "a.py",
            """
            product_id = 12<caret>34
            """,
            """
            class ProductId(int):
                pass


            product_id = ProductId(1234)
            """,
            "Introduce custom type from int"
        )
    }

    fun testKeywordArgument_WithSnakeCaseName_UsesKeywordForClass() {
        myFixture.doIntentionTest(
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


            def do(my_arg) -> None:
                ...

            def test_():
                do(my_arg=MyArg(1234))
            """,
            "Introduce custom type from int"
        )
    }

    fun testDictValue_WhenExpectedTypeIsAlreadyCustom_DoesNotOfferIntention() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class CustomInt(int):
                pass


            val: dict[str, CustomInt] = {"a": <caret>1, "b": 2, "c": 3}
            """,
            "Introduce custom type from int"
        )
    }

    fun testDictKey_WhenExpectedTypeIsAlreadyCustom_DoesNotOfferIntention() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class CustomStr(str):
                pass


            val: dict[CustomStr, int] = {<caret>"a": 1, "b": 2}
            """,
            "Introduce custom type from str"
        )
    }

    fun testParameterDefaultValue_WithSnakeCaseName_UsesParameterNameForClass() {
        myFixture.doIntentionTest(
            "a.py",
            """
            def extract_saved_reels(self, output_dir: str = "saved_ree<caret>ls"):
                pass
            """,
            """
            class OutputDir(str):
                pass


            def extract_saved_reels(self, output_dir: OutputDir = OutputDir("saved_reels")):
                pass
            """,
            "Introduce custom type from str"
        )
    }

    fun testModuleDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            \"\"\"Module doc<caret>string\"\"\"

            def f(x: int) -> None:
                ...
            """,
            "Introduce custom type from int"
        )
    }

    fun testFunctionDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def f(x: int) -> None:
                \"\"\"Function doc<caret>string\"\"\"

                return x
            """,
            "Introduce custom type from int"
        )
    }

    fun testClassDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.configureByText(
            "a.py",
            """
            class C:
                \"\"\"Class doc<caret>string\"\"\"

                def __init__(self, x: int) -> None:
                    self.x = x
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from int")
        assertEmpty("Should not offer custom type introduction on class docstring", intentions)
    }

    fun testClassDocstring_NotFirstStatement_DoesNotOfferCustomTypeIntention() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            class EnumWithList: pass
            
            class MyVariant(str, EnumWithList):
                __slots__ = ()
                ""<caret>"Some documentation""${'"'}
            """,
            "Introduce custom type from str"
        )
    }
}
