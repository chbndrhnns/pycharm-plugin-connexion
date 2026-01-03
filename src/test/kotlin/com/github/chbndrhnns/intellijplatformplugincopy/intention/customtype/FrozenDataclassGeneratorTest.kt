package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import fixtures.TestBase

/**
 * Unit tests for [FrozenDataclassGenerator].
 *
 * Verifies that frozen dataclass value objects are generated correctly with proper
 * syntax, decorator, value field, and required imports.
 */
class FrozenDataclassGeneratorTest : TestBase() {

    private val generator = FrozenDataclassGenerator()

    fun testKind_ReturnsFrozenDataclass() {
        assertEquals(CustomTypeKind.FROZEN_DATACLASS, generator.kind)
    }

    fun testRequiresValueAccess_ReturnsTrue() {
        // Frozen dataclass requires .value access to get the underlying value
        assertTrue(generator.requiresValueAccess)
    }

    fun testDetermineBaseTypeText_ReturnsSimpleBuiltinName() {
        assertEquals("int", generator.determineBaseTypeText("int", null))
        assertEquals("str", generator.determineBaseTypeText("str", null))
        assertEquals("float", generator.determineBaseTypeText("float", null))
    }

    fun testCreateTypeDefinition_GeneratesCorrectClass() {
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("ProductId", pyClass.name)

        // Check decorator
        val decorators = pyClass.decoratorList?.decorators
        assertNotNull(decorators)
        assertEquals(1, decorators!!.size)
        assertTrue(decorators[0].text.contains("@dataclass(frozen=True)"))

        // Check value field exists
        val classText = pyClass.text
        assertTrue("Should contain value field", classText.contains("value: int"))
    }

    fun testCreateTypeDefinition_WithStrBuiltin() {
        val definition = generator.createTypeDefinition(project, "CustomName", "str")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("CustomName", pyClass.name)
        assertTrue(pyClass.text.contains("value: str"))
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
        assertTrue(fileText.contains("@dataclass(frozen=True)"))
        assertTrue(fileText.contains("class ProductId"))
    }

    fun testAddRequiredImports_AddsDataclassImport() {
        myFixture.configureByText(
            "a.py",
            """
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile

        WriteCommandAction.runWriteCommandAction(project) {
            generator.addRequiredImports(pyFile)
        }

        val imports = pyFile.importBlock
        val hasDataclassImport = imports.any { statement ->
            statement is PyFromImportStatement &&
                    statement.importSourceQName?.toString() == "dataclasses" &&
                    statement.importElements.any { it.importedQName?.toString() == "dataclass" }
        }
        assertTrue("Should have dataclass import", hasDataclassImport)
    }

    fun testWrapExpressionText_GeneratesValueKeywordSyntax() {
        assertEquals("ProductId(value=123)", generator.wrapExpressionText("ProductId", "123"))
        assertEquals("CustomStr(value=\"hello\")", generator.wrapExpressionText("CustomStr", "\"hello\""))
    }
}
