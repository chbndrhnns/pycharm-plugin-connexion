package com.github.chbndrhnns.intellijplatformplugincopy.completion

import fixtures.TestBase

class PyReturnCompletionTest : TestBase() {

    fun testSimpleReturnCompletion() {
        myFixture.configureByText(
            "test_simple.py", """
            def foo() -> int:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_simple.py")
        assertNotNull(variants)
        assertTrue("Should contain 'int', found: $variants", variants!!.contains("int"))
    }

    fun testUnionReturnCompletion() {
        myFixture.configureByText(
            "test_union.py", """
            def foo() -> int | str:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_union.py")
        assertNotNull(variants)
        assertTrue("Should contain 'int'", variants!!.contains("int"))
        assertTrue("Should contain 'str'", variants.contains("str"))
    }

    fun testUnionReturnCompletionWithOldSyntax() {
        myFixture.configureByText(
            "test_union_old.py", """
            from typing import Union
            def foo() -> Union[int, str]:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_union_old.py")
        assertNotNull(variants)
        assertTrue("Should contain 'int'", variants!!.contains("int"))
        assertTrue("Should contain 'str'", variants.contains("str"))
    }

    fun testPriority() {
        myFixture.configureByText(
            "test_priority.py", """
            class MyType: pass
            
            def foo() -> MyType:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_priority.py")
        assertNotNull(variants)
        assertTrue("List should be non-empty", variants!!.isNotEmpty())
        assertEquals("MyType should be the first suggestion", "MyType", variants[0])
    }
}
