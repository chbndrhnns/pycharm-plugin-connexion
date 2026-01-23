package com.github.chbndrhnns.betterpy.features.completion

import fixtures.TestBase

class PyNamedArgumentFromScopeCompletionTest : TestBase() {

    fun testSuggestsParamEqualsParamWhenLocalExists() {
        myFixture.configureByText(
            "test.py", """
            def f(foo, bar):
                pass

            def g():
                foo = 1
                f(fo<caret>)
            """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test.py")
        assertNotNull(variants)
        assertTrue("Should contain 'foo=foo', found: $variants", variants!!.contains("foo=foo"))
    }

    fun testDoesNotSuggestWhenNoLocalSymbolExists() {
        myFixture.configureByText(
            "test.py", """
            def f(foo, bar):
                pass

            def g():
                f(fo<caret>)
            """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'foo=foo', found: $variants", variants!!.contains("foo=foo"))
    }

    fun testInsertReplacesPrefixWithParamEqualsParam() {
        myFixture.configureByText(
            "test.py", """
            def f(foo, bar):
                pass

            def g():
                foo = 1
                f(fo<caret>)
            """.trimIndent()
        )

        myFixture.completeBasic()

        val item = myFixture.lookupElements
            ?.firstOrNull { it.lookupString == "foo=foo" }
            ?: error("Completion item 'foo=foo' not found. Available: ${myFixture.lookupElementStrings}")

        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')

        myFixture.checkResult(
            """
            def f(foo, bar):
                pass

            def g():
                foo = 1
                f(foo=foo)
            """.trimIndent()
        )
    }

    fun testDoesNotSuggestAlreadyPassedKeyword() {
        myFixture.configureByText(
            "test.py", """
            def f(foo, bar):
                pass

            def g():
                foo = 1
                bar = 2
                f(foo=foo, ba<caret>)
            """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test.py")
        assertNotNull(variants)
        assertTrue("Should contain 'bar=bar', found: $variants", variants!!.contains("bar=bar"))
        assertFalse("Should not contain 'foo=foo' because it's already passed", variants.contains("foo=foo"))
    }

    fun testNoSuggestionInsideKeywordValue() {
        myFixture.configureByText(
            "test.py", """
            def f(foo):
                pass

            def g():
                foo = 1
                f(foo=<caret>)
            """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test.py")
        assertTrue(variants == null || !variants.contains("foo=foo"))
    }
}
