package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.TestBase

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

        // After running the intention, the caret should be on the introduced
        // class name so that inline rename can start immediately.
        val caretOffset = myFixture.editor.caretModel.offset

        val result = myFixture.file.text
        println("[DEBUG_LOG] Resulting file:\n$result")
        assertTrue("Generated class should subclass the builtin type", result.contains("class Customint(int):"))
        assertTrue("Annotation should be rewritten to the new type", result.contains("def f(x: Customint) -> None:"))

        val classNameIndex = result.indexOf("Customint(int)")
        assertTrue(
            "Caret should be placed on the new class name for inline rename", classNameIndex != -1 &&
                    caretOffset in classNameIndex until (classNameIndex + "CustomInt".length)
        )
    }
}
