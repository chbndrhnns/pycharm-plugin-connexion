package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import fixtures.TestBase

class CustomTypePreviewTest : TestBase() {

    fun testIntroduceCustomTypePreviewAcrossFilesDoesNotCrash() {
        myFixture.addFileToProject(
            "b.py",
            """
            import dataclasses

            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent()
        )

        myFixture.configureByText(
            "a.py",
            """
            from b import D

            def do():
                D(product_id=12<caret>3)
            """.trimIndent()
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        val previewText = myFixture.getIntentionPreviewText(intention)

        assertTrue("Preview generation should succeed without exceptions", previewText?.isNotBlank() == true)
    }
}
