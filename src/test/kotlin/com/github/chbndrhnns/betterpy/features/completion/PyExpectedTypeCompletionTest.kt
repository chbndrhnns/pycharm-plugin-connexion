package com.github.chbndrhnns.betterpy.features.completion

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
        assertEquals("MyType() should be the first suggestion", "MyType()", variants[0])
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
        assertEquals("MyType() should be first", "MyType()", variants[0])
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
        assertEquals("MyArg() should be first", "MyArg()", variants[0])
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
        assertEquals("MyArg() should be first", "MyArg()", variants[0])
    }

    fun testSetElementCompletion() {
        myFixture.configureByText(
            "test_set_elem.py", """
            class MyType2: pass
            
            vals: set[MyType2] = {<caret>}
        """.trimIndent()
        )
        val variants = myFixture.getCompletionVariants("test_set_elem.py")
        assertNotNull(variants)
        assertTrue("Should contain 'MyType2', found: $variants", variants!!.contains("MyType2"))
        assertEquals("MyType2() should be first", "MyType2()", variants[0])
    }

    fun testListElementCompletion() {
        myFixture.configureByText(
            "test_list_elem.py", """
            class MyType3: pass
            
            vals: list[MyType3] = [<caret>]
        """.trimIndent()
        )
        val variants = myFixture.getCompletionVariants("test_list_elem.py")
        assertNotNull(variants)
        assertTrue("Should contain 'MyType3', found: $variants", variants!!.contains("MyType3"))
        assertEquals("MyType3() should be first", "MyType3()", variants[0])
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
        assertEquals("MyResult() should be the first suggestion", "MyResult()", variants[0])
    }

    fun testNoExpectedTypeAfterDotInReturn() {
        myFixture.configureByText(
            "test_return_after_dot.py", """
            class MyType: pass

            def foo() -> MyType:
                item = MyType()
                return item.<caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_return_after_dot.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'MyType', found: $variants", variants!!.contains("MyType"))
    }

    fun testNoExpectedTypeAfterDotInAssignment() {
        myFixture.configureByText(
            "test_assign_after_dot.py", """
            class MyType: pass

            item = MyType()
            x: MyType = item.<caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_assign_after_dot.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'MyType', found: $variants", variants!!.contains("MyType"))
    }

    fun testNoExpectedTypeAfterDotInArgument() {
        myFixture.configureByText(
            "test_arg_after_dot.py", """
            class MyArg: pass

            item = MyArg()

            def func(a: MyArg):
                pass

            func(a=item.<caret>)
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_arg_after_dot.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'MyArg', found: $variants", variants!!.contains("MyArg"))
    }

    fun testNoUnionMembersAfterDotInReturn() {
        myFixture.configureByText(
            "test_union_after_dot.py", """
            def foo() -> int | str:
                item = "x"
                return item.<caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_union_after_dot.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'int', found: $variants", variants!!.contains("int"))
        assertFalse("Should not contain 'str', found: $variants", variants.contains("str"))
    }

    fun testNoExpectedTypeOnAttributeAccessInReturn() {
        myFixture.configureByText(
            "test_return_attr_access.py", """
            class MyType: pass

            def foo() -> MyType:
                item = MyType()
                return item.a<caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_return_attr_access.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'MyType', found: $variants", variants!!.contains("MyType"))
    }

    fun testNoExpectedTypeOnAttributeAccessInAssignment() {
        myFixture.configureByText(
            "test_assign_attr_access.py", """
            class MyType: pass

            item = MyType()
            x: MyType = item.a<caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_assign_attr_access.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'MyType', found: $variants", variants!!.contains("MyType"))
    }

    fun testNoExpectedTypeOnAttributeAccessInArgument() {
        myFixture.configureByText(
            "test_arg_attr_access.py", """
            class MyArg: pass

            item = MyArg()

            def func(a: MyArg):
                pass

            func(a=item.a<caret>)
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_arg_attr_access.py")
        assertNotNull(variants)
        assertFalse("Should not contain 'MyArg', found: $variants", variants!!.contains("MyArg"))
    }

    fun testSkipLiteralStringExpectedTypeInReturnCompletion() {
        myFixture.configureByText(
            "test_literal_string.py", """
            import typing

            def foo() -> typing.LiteralString:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_literal_string.py")
        assertNotNull(variants)
        assertFalse(
            "Should not contain 'LiteralString' (or qualified variants), found: $variants",
            variants!!.any { it.contains("LiteralString", ignoreCase = true) }
        )
        assertFalse(
            "Should not contain 'LiteralStr' (or qualified variants), found: $variants",
            variants.any { it.contains("LiteralStr", ignoreCase = true) }
        )
        assertFalse(
            "Should not contain literal suggestions like Literal[...], found: $variants",
            variants.any { it.startsWith("Literal[") }
        )
    }

    fun testUnionKeepsOtherMembersButSkipsLiteralString() {
        myFixture.configureByText(
            "test_union_literal_string.py", """
            import typing

            def foo() -> int | typing.LiteralString:
                return <caret>
        """.trimIndent()
        )

        val variants = myFixture.getCompletionVariants("test_union_literal_string.py")
        assertNotNull(variants)
        assertTrue("Should contain 'int', found: $variants", variants!!.contains("int"))
        assertFalse(
            "Should not contain 'LiteralString' (or qualified variants), found: $variants",
            variants.any { it.contains("LiteralString", ignoreCase = true) }
        )
        assertFalse(
            "Should not contain literal suggestions like Literal[...], found: $variants",
            variants.any { it.startsWith("Literal[") }
        )
    }

    fun testDoNotOfferBuiltinsAsExpectedTypeCallables() {
        myFixture.configureByText(
            "test_builtins.py", """
            def do(arg: str | int):
                ...

            def test_():
                do(<caret>)
        """.trimIndent()
        )

        myFixture.completeBasic()
        val lookupElements = myFixture.lookupElements ?: emptyArray()

        val expectedTypeSuggestions = lookupElements.filter {
            it.allLookupStrings.contains("str") || it.allLookupStrings.contains("int")
        }.filter {
            // Check if it's our suggestion by looking at the type text
            val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
            it.renderElement(presentation)
            presentation.typeText == "Expected type"
        }

        assertTrue(
            "Should not contain 'str' or 'int' as 'Expected type', found: ${expectedTypeSuggestions.map { it.lookupString }}",
            expectedTypeSuggestions.isEmpty()
        )
    }
}
