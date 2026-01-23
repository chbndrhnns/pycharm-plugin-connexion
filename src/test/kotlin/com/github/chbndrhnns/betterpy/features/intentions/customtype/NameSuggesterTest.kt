package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

/**
 * Unit tests for [NameSuggester].
 *
 * Verifies name suggestion and uniqueness logic for the "Introduce custom type" feature.
 */
class NameSuggesterTest : TestBase() {

    private val nameSuggester = NameSuggester()

    fun testEnsureUnique_ReturnsBaseNameWhenNoConflict() {
        myFixture.configureByText(
            "a.py",
            """
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val result = nameSuggester.ensureUnique(pyFile, "CustomInt")

        assertEquals("CustomInt", result)
    }

    fun testEnsureUnique_AppendsSuffixWhenClassExists() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass
            
            
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val result = nameSuggester.ensureUnique(pyFile, "CustomInt")

        assertEquals("CustomInt1", result)
    }

    fun testEnsureUnique_IncrementsUntilUnique() {
        myFixture.configureByText(
            "a.py",
            """
            class CustomInt(int):
                pass
            
            
            class CustomInt1(int):
                pass
            
            
            class CustomInt2(int):
                pass
            
            
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val result = nameSuggester.ensureUnique(pyFile, "CustomInt")

        assertEquals("CustomInt3", result)
    }

    fun testEnsureUnique_WorksWithDifferentTypeNames() {
        myFixture.configureByText(
            "a.py",
            """
            class ProductId(int):
                pass
            
            
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val result = nameSuggester.ensureUnique(pyFile, "ProductId")

        assertEquals("ProductId1", result)
    }

    fun testSuggestTypeName_ReturnsPreferredWhenProvided() {
        val result = nameSuggester.suggestTypeName("int", "ProductId")
        assertEquals("ProductId", result)
    }

    fun testSuggestTypeName_ReturnsCustomBuiltinWhenNoPreferred() {
        assertEquals("CustomInt", nameSuggester.suggestTypeName("int", null))
        assertEquals("CustomStr", nameSuggester.suggestTypeName("str", null))
        assertEquals("CustomFloat", nameSuggester.suggestTypeName("float", null))
        assertEquals("CustomBool", nameSuggester.suggestTypeName("bool", null))
        assertEquals("CustomList", nameSuggester.suggestTypeName("list", null))
        assertEquals("CustomDict", nameSuggester.suggestTypeName("dict", null))
    }

    fun testDeriveBaseName_ConvertsToPascalCase() {
        assertEquals("ProductId", nameSuggester.deriveBaseName("product_id"))
        assertEquals("UserId", nameSuggester.deriveBaseName("user_id"))
        assertEquals("MyCustomName", nameSuggester.deriveBaseName("my_custom_name"))
    }

    fun testDeriveBaseName_ReturnsNullForNonSnakeCase() {
        assertNull(nameSuggester.deriveBaseName("productId"))
        assertNull(nameSuggester.deriveBaseName("product"))
    }
}
