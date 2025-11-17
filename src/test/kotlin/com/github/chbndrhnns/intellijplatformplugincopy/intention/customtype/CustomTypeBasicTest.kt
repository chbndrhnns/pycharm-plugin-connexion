package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.TestBase

/**
 * Basic behavior for introducing a custom type from a stdlib/builtin type
 * in a simple annotation context.
 */
class CustomTypeBasicTest : TestBase() {

    fun testAnnotatedParam_Int_CreatesSubclassAndRewritesAnnotation() {
        myFixture.configureByText(
            "a.py",
            """
            def f(x: <caret>int) -> None:
                ...
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        val result = myFixture.file.text
        assertTrue("Generated class should subclass the builtin type", result.contains("class CustomInt(int):"))
        assertTrue("Annotation should be rewritten to the new type", result.contains("def f(x: CustomInt) -> None:"))
    }
}
