package com.github.chbndrhnns.intellijplatformplugincopy.completion

import fixtures.TestBase

class PyExpectedTypeCompletionTest : TestBase() {

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

    fun testReturnPriority() {
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

    fun testAssignmentCompletion() {
        myFixture.configureByText(
            "test_assign.py", """
            class MyType: pass
            
            x: MyType = <caret>
        """.trimIndent()
        )
        val variants = myFixture.getCompletionVariants("test_assign.py")
        assertNotNull(variants)
        assertTrue("Should contain 'MyType', found: $variants", variants!!.contains("MyType"))
        // Check priority - it should be high up
        assertEquals("MyType should be first", "MyType", variants[0])
    }

    fun testArgumentCompletion() {
        myFixture.configureByText(
            "test_arg.py", """
            class MyArg: pass
            
            def func(a: MyArg): pass
            
            func(a=<caret>)
        """.trimIndent()
        )
        val variants = myFixture.getCompletionVariants("test_arg.py")
        assertNotNull(variants)
        assertTrue("Should contain 'MyArg', found: $variants", variants!!.contains("MyArg"))
        assertEquals("MyArg should be first", "MyArg", variants[0])
    }

    fun testPositionalArgumentCompletion() {
        myFixture.configureByText(
            "test_pos_arg.py", """
            class MyArg: pass
            
            def func(a: MyArg): pass
            
            func(<caret>)
        """.trimIndent()
        )
        val variants = myFixture.getCompletionVariants("test_pos_arg.py")
        assertNotNull(variants)
        assertTrue("Should contain 'MyArg', found: $variants", variants!!.contains("MyArg"))
        // For positional, it might be first or high up. Let's assert it's first for now as we set high priority.
        assertEquals("MyArg should be first", "MyArg", variants[0])
    }

    fun testAsyncReturnCompletion() {
        myFixture.configureByText(
            "test_async.py", """
            class MyResult: pass
            
            async def foo() -> MyResult:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_async.py")
        assertNotNull(variants)
        // Should contain MyResult.
        assertTrue("Should contain 'MyResult', found: ${variants?.take(5)}", variants!!.contains("MyResult"))
        assertEquals("MyResult should be the first suggestion", "MyResult", variants[0])
    }
}
