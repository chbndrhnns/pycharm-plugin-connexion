package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

/**
 * Unit tests for [SubclassTypeGenerator].
 *
 * Verifies that subclass type definitions are generated correctly,
 * maintaining backward compatibility with the original CustomTypeGenerator.
 */
class SubclassTypeGeneratorTest : TestBase() {

    private val generator = SubclassTypeGenerator()

    fun testKind_ReturnsSubclass() {
        assertEquals(CustomTypeKind.SUBCLASS, generator.kind)
    }

    fun testDetermineBaseTypeText_ReturnsBuiltinName() {
        assertEquals("int", generator.determineBaseTypeText("int", null))
        assertEquals("str", generator.determineBaseTypeText("str", null))
    }

    fun testCreateTypeDefinition_GeneratesClassWithPass() {
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("ProductId", pyClass.name)
        assertTrue(pyClass.text.contains("class ProductId(int):"))
        assertTrue(pyClass.text.contains("pass"))
    }

    fun testCreateTypeDefinition_StrUsesSlots() {
        val definition = generator.createTypeDefinition(project, "CustomName", "str")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertTrue(pyClass.text.contains("__slots__ = ()"))
    }

    fun testInsertTypeDefinition_InsertsAfterImports() {
        myFixture.configureByText(
            "a.py",
            """
            import os
            
            
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        var inserted: com.intellij.psi.PsiElement? = null
        WriteCommandAction.runWriteCommandAction(project) {
            inserted = generator.insertTypeDefinition(pyFile, definition)
        }

        assertTrue(inserted is PyClass)
        val fileText = pyFile.text
        assertTrue(fileText.contains("import os"))
        assertTrue(fileText.contains("class ProductId(int):"))
    }

    fun testAddRequiredImports_DoesNothing() {
        myFixture.configureByText(
            "a.py",
            """
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val initialImportCount = pyFile.importBlock.size

        generator.addRequiredImports(pyFile)

        assertEquals("Subclass types don't need imports", initialImportCount, pyFile.importBlock.size)
    }

    fun testWrapExpressionText_GeneratesCallSyntax() {
        assertEquals("ProductId(123)", generator.wrapExpressionText("ProductId", "123"))
        assertEquals("CustomStr(\"hello\")", generator.wrapExpressionText("CustomStr", "\"hello\""))
    }

    fun testRequiresValueAccess_ReturnsFalse() {
        // Subclass types don't require .value access since they inherit from the builtin
        assertFalse(generator.requiresValueAccess)
    }
}
