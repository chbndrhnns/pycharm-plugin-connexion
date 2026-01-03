package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import fixtures.TestBase

/**
 * Unit tests for [PydanticValueObjectGenerator].
 *
 * Verifies that Pydantic value objects are generated correctly with proper
 * syntax, BaseModel inheritance, value field, frozen config, and required imports.
 */
class PydanticValueObjectGeneratorTest : TestBase() {

    private val generator = PydanticValueObjectGenerator()

    fun testKind_ReturnsPydanticValueObject() {
        assertEquals(CustomTypeKind.PYDANTIC_VALUE_OBJECT, generator.kind)
    }

    fun testRequiresValueAccess_ReturnsTrue() {
        // Pydantic value object requires .value access to get the underlying value
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

        // Check inheritance from BaseModel
        val superClasses = pyClass.superClassExpressions
        assertTrue("Should inherit from BaseModel", superClasses.any { it.text == "BaseModel" })

        // Check value field and frozen config exist
        val classText = pyClass.text
        assertTrue("Should contain value field", classText.contains("value: int"))
        assertTrue("Should contain frozen config", classText.contains("model_config") && classText.contains("frozen"))
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
        assertTrue(fileText.contains("class ProductId(BaseModel)"))
    }

    fun testAddRequiredImports_AddsPydanticImports() {
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
        val hasBaseModelImport = imports.any { statement ->
            statement is PyFromImportStatement &&
                    statement.importSourceQName?.toString() == "pydantic" &&
                    statement.importElements.any { it.importedQName?.toString() == "BaseModel" }
        }
        val hasConfigDictImport = imports.any { statement ->
            statement is PyFromImportStatement &&
                    statement.importSourceQName?.toString() == "pydantic" &&
                    statement.importElements.any { it.importedQName?.toString() == "ConfigDict" }
        }
        assertTrue("Should have BaseModel import", hasBaseModelImport)
        assertTrue("Should have ConfigDict import", hasConfigDictImport)
    }

    fun testWrapExpressionText_GeneratesValueKeywordSyntax() {
        assertEquals("ProductId(value=123)", generator.wrapExpressionText("ProductId", "123"))
        assertEquals("CustomStr(value=\"hello\")", generator.wrapExpressionText("CustomStr", "\"hello\""))
    }
}
