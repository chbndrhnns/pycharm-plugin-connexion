package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.HeavyTestBase

/**
 * Placeholder for a future heavy multi-module variant of the cross-module
 * custom type tests.
 *
 * NOTE: This class now uses [HeavyTestBase], which in turn relies on
 * IdeaTestFixtureFactory to create a **heavy** project. The current
 * implementation still exercises cross-file behaviour within a single module.
 * If we ever need *true* cross-module resolution, this is the natural place to
 * add additional real modules and module dependencies as described in
 * `docs/heavy-fixture.md`.
 */
class CustomTypeHeavyTest : HeavyTestBase() {

    fun testDataclassCrossModule_AddsImportAtUsageSite() {
        myFixture.configureByText(
            "model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent(),
        )

        myFixture.configureByText(
            "usage.py",
            """
            from model import D


            def do():
                D(product_id=12<caret>3)
            """.trimIndent(),
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from model import D, Productid


            class Productid(int):
                pass


            def do():
                D(product_id=Productid(123))
            """.trimIndent()
        )
    }

    fun testDataclassCrossModule_CustomTypeLivesWithDeclaration() {
        myFixture.configureByText(
            "model.py",
            """
            import dataclasses


            @dataclasses.dataclass
            class D:
                product_id: int
            """.trimIndent(),
        )

        myFixture.configureByText(
            "usage.py",
            """
            from model import D


            def do():
                D(product_id=12<caret>3)
            """.trimIndent(),
        )

        myFixture.doHighlighting()
        val intention = myFixture.findSingleIntention("Introduce custom type from int")
        myFixture.launchAction(intention)

        myFixture.checkResult(
            """
            from model import D


            class Productid(int):
                pass


            def do():
                D(product_id=Productid(123))
            """.trimIndent()
        )
    }
}
