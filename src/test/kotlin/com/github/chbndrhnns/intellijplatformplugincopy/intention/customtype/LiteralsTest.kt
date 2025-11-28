package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class LiteralsTest : TestBase() {

    fun testString_NoAnnotation_WrapsWithCustomStr() {
        myFixture.configureByText(
            "a.py",
            """
            def expect_str(s) -> None:
                ...

            expect_str("ab<caret>c")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            def expect_str(s) -> None:
                ...

            expect_str(Customstr("abc"))
            """.trimIndent()
        )
    }

    fun testString_Annotation_UpdatesAnnotationAfterWrap() {
        myFixture.configureByText(
            "a.py",
            """
            def expect_str(s: str) -> None:
                ...

            expect_str("ab<caret>c")
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            def expect_str(s: Customstr) -> None:
                ...

            expect_str(Customstr("abc"))
            """.trimIndent()
        )
    }

    fun testStringAssignment_NoAnnotation_WrapsWithCustomStr() {
        myFixture.configureByText(
            "a.py",
            """
            val = "s<caret>tr"
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customstr(str):
                pass


            val = Customstr("str")
            """.trimIndent()
        )
    }

    fun testFloatAssignment_UsesFloatAndWrapsValue() {
        myFixture.configureByText(
            "a.py",
            """
            val = 4567.<caret>6
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from float")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class Customfloat(float):
                pass
            
            
            val = Customfloat(4567.6)
            """.trimIndent()
        )
    }

    fun testIntAssignment_WithSnakeCaseName_UsesTargetNameForClass() {
        myFixture.configureByText(
            "a.py",
            """
            product_id = 12<caret>34
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class ProductId(int):
                pass


            product_id = ProductId(1234)
            """.trimIndent()
        )
    }

    fun testKeywordArgument_WithSnakeCaseName_UsesKeywordForClass() {
        myFixture.configureByText(
            "a.py",
            """
            def do(my_arg) -> None:
                ...

            def test_():
                do(my_arg=12<caret>34)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class MyArg(int):
                pass


            def do(my_arg) -> None:
                ...

            def test_():
                do(my_arg=MyArg(1234))
            """.trimIndent()
        )
    }

    fun testDictValue_WhenExpectedTypeIsAlreadyCustom_DoesNotOfferIntention() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass


            val: dict[str, CustomInt] = {"a": <caret>1, "b": 2, "c": 3}
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from int")
        assertEmpty("Should not offer to introduce custom type when expected type is already custom", intentions)
    }

    fun testDictKey_WhenExpectedTypeIsAlreadyCustom_DoesNotOfferIntention() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomStr(str):
                pass


            val: dict[CustomStr, int] = {<caret>"a": 1, "b": 2}
            """.trimIndent()
        )

        myFixture.doHighlighting()

        // We expect NO "Introduce custom type..." intention here
        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from str")
        assertEmpty("Should not offer to introduce custom type when expected type is already custom", intentions)
    }

    fun testParameterDefaultValue_WithSnakeCaseName_UsesParameterNameForClass() {
        myFixture.configureByText(
            "a.py",
            """
            def extract_saved_reels(self, output_dir: str = "saved_ree<caret>ls"):
                pass
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from str")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            class OutputDir(str):
                pass


            def extract_saved_reels(self, output_dir: OutputDir = OutputDir("saved_reels")):
                pass
            """.trimIndent()
        )
    }

    fun testModuleDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.configureByText(
            "a.py",
            """
            \"\"\"Module doc<caret>string\"\"\"

            def f(x: int) -> None:
                ...
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from int")
        assertEmpty("Should not offer custom type introduction on module docstring", intentions)
    }

    fun testFunctionDocstring_DoesNotOfferCustomTypeIntention() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: int) -> None:
                \"\"\"Function doc<caret>string\"\"\"

                return x
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val intentions = myFixture.filterAvailableIntentions("Introduce custom type from int")
        assertEmpty("Should not offer custom type introduction on function docstring", intentions)
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
}
