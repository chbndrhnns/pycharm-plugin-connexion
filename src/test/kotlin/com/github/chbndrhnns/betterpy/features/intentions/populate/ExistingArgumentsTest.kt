package com.github.chbndrhnns.betterpy.features.intentions.populate

import fixtures.TestBase
import fixtures.assertIntentionNotAvailable

class ExistingArgumentsTest : TestBase() {

    fun testMapWithArguments_NoPopulateOffered() {
        // Caret inside
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            x = map(<caret>str, range(5))
            """,
            "Populate required arguments with '...'"
        )
    }

    fun testMapWithArguments_NoPopulateRecursiveOffered() {
        // Caret inside
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            x = map(str, <caret>range(5))
            """,
            "Populate missing arguments recursively with '...'"
        )
    }

    fun testMapWithArguments_NoPopulateKwOnlyOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            x = map(str, range(5))<caret>
            """,
            "Populate missing arguments with '...'"
        )
    }

    fun testCustomFunctionAllArgs_NoPopulateOffered() {
        myFixture.assertIntentionNotAvailable(
            "a.py",
            """
            def foo(a, b): pass
            foo(1, 2)<caret>
            """,
            "Populate required arguments with '...'"
        )
    }

//    fun testCustomFunctionMissingArg_PopulateOffered() {
//         myFixture.configureByText("a.py", """
//            def foo(a, b): pass
//            foo(1)<caret>
//        """)
//    }
}
