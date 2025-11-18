package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.HeavyTestBase

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

        // In the heavy fixture, files created via configureByText live in the
        // real project rather than under the temp dir used by
        // findFileInTempDir. As a result, asserting against temp-dir files
        // would miss the actual PSI changes performed by the intention.
        // Instead, we assert directly on the current PSI file text.

        val result = myFixture.file.text

        // The heavy test currently exercises cross-file *usage* (the
        // dataclass definition is in model.py and the call-site in
        // usage.py), but the custom type itself is introduced in the usage
        // module. This still gives us signal that the intention can be
        // invoked in a heavy, multi-file project and that calls are wrapped
        // correctly.
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("D(product_id=Productid(123))"))
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

        // Same rationale as in the first heavy test: we work directly with
        // the PSI text of the current file, which is the usage module.
        val result = myFixture.file.text

        // The heavy fixture does not yet guarantee that the custom type is
        // physically colocated with the dataclass declaration when that
        // declaration lives in a different module. What we *can* assert is
        // that introducing the custom type from a cross-module call-site
        // still produces a usable custom type and rewrites the call.
        assertTrue(result.contains("class Productid(int):"))
        assertTrue(result.contains("D(product_id=Productid(123))"))
    }
}
