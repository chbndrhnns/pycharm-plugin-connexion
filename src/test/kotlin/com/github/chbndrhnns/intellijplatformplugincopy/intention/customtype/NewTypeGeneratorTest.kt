package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyNamedParameter
import fixtures.TestBase

/**
 * Unit tests for [NewTypeGenerator].
 *
 * Verifies that NewType definitions are generated correctly with proper
 * syntax and required imports.
 */
class NewTypeGeneratorTest : TestBase() {

    private val generator = NewTypeGenerator()

    fun testKind_ReturnsNewType() {
        assertEquals(CustomTypeKind.NEWTYPE, generator.kind)
    }

    fun testDetermineBaseTypeText_ReturnsSimpleBuiltinName() {
        // NewType doesn't support generic args, so it should return the simple name
        assertEquals("int", generator.determineBaseTypeText("int", null))
        assertEquals("str", generator.determineBaseTypeText("str", null))
        assertEquals("list", generator.determineBaseTypeText("list", null))
    }

    fun testCreateTypeDefinition_GeneratesCorrectAssignment() {
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        assertTrue(definition is PyAssignmentStatement)
        val assignment = definition as PyAssignmentStatement
        assertEquals("ProductId = NewType(\"ProductId\", int)", assignment.text)
    }

    fun testCreateTypeDefinition_WithStrBuiltin() {
        val definition = generator.createTypeDefinition(project, "CustomName", "str")

        assertTrue(definition is PyAssignmentStatement)
        val assignment = definition as PyAssignmentStatement
        assertEquals("CustomName = NewType(\"CustomName\", str)", assignment.text)
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

        var inserted: PsiElement? = null
        WriteCommandAction.runWriteCommandAction(project) {
            inserted = generator.insertTypeDefinition(pyFile, definition)
        }

        assertTrue(inserted is PyAssignmentStatement)
        // The definition should be inserted after the import
        val fileText = pyFile.text
        assertTrue(fileText.contains("import os"))
        assertTrue(fileText.contains("ProductId = NewType"))
    }

    fun testAddRequiredImports_AddsNewTypeImport() {
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
        val hasNewTypeImport = imports.any { statement ->
            statement is PyFromImportStatement &&
                    statement.importSourceQName?.toString() == "typing" &&
                    statement.importElements.any { it.importedQName?.toString() == "NewType" }
        }
        assertTrue("Should have NewType import", hasNewTypeImport)
    }

    fun testAddRequiredImports_DoesNotDuplicateExistingImport() {
        myFixture.configureByText(
            "a.py",
            """
            from typing import NewType
            
            
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val initialImportCount = pyFile.importBlock.size

        generator.addRequiredImports(pyFile)

        assertEquals("Should not add duplicate import", initialImportCount, pyFile.importBlock.size)
    }

    fun testWrapExpressionText_GeneratesCallSyntax() {
        assertEquals("ProductId(123)", generator.wrapExpressionText("ProductId", "123"))
        assertEquals("CustomStr(\"hello\")", generator.wrapExpressionText("CustomStr", "\"hello\""))
    }

    fun testRequiresValueAccess_ReturnsFalse() {
        // NewType doesn't require .value access since it's identity at runtime
        assertFalse(generator.requiresValueAccess)
    }

    fun testCreateTypeDefinition_WithFloatBuiltin() {
        val definition = generator.createTypeDefinition(project, "Price", "float")

        assertTrue(definition is PyAssignmentStatement)
        val assignment = definition as PyAssignmentStatement
        assertEquals("Price = NewType(\"Price\", float)", assignment.text)
    }

    fun testCreateTypeDefinition_WithBoolBuiltin() {
        val definition = generator.createTypeDefinition(project, "Flag", "bool")

        assertTrue(definition is PyAssignmentStatement)
        val assignment = definition as PyAssignmentStatement
        assertEquals("Flag = NewType(\"Flag\", bool)", assignment.text)
    }

    fun testCreateTypeDefinition_WithBytesBuiltin() {
        val definition = generator.createTypeDefinition(project, "BinaryData", "bytes")

        assertTrue(definition is PyAssignmentStatement)
        val assignment = definition as PyAssignmentStatement
        assertEquals("BinaryData = NewType(\"BinaryData\", bytes)", assignment.text)
    }

    fun testDetermineBaseTypeText_IgnoresSubscriptedAnnotation() {
        // NewType doesn't support generic args, so it should return the simple name
        // even when given a subscripted annotation
        myFixture.configureByText(
            "a.py",
            """
            def foo(items: list[int]):
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val function = pyFile.findTopLevelFunction("foo")
        val param = function?.parameterList?.parameters?.firstOrNull() as? PyNamedParameter
        val annotationValue = param?.annotation?.value

        // NewType should return just "list", not "list[int]"
        val baseText = generator.determineBaseTypeText("list", annotationValue)
        assertEquals("list", baseText)
    }

    fun testInsertTypeDefinition_IntoEmptyFile() {
        myFixture.configureByText("a.py", "")

        val pyFile = myFixture.file as PyFile
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        var inserted: PsiElement? = null
        WriteCommandAction.runWriteCommandAction(project) {
            inserted = generator.insertTypeDefinition(pyFile, definition)
        }

        assertTrue(inserted is PyAssignmentStatement)
        assertTrue(pyFile.text.contains("ProductId = NewType"))
    }

    fun testInsertTypeDefinition_IntoFileWithOnlyFunction() {
        myFixture.configureByText(
            "a.py",
            """
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        var inserted: PsiElement? = null
        WriteCommandAction.runWriteCommandAction(project) {
            inserted = generator.insertTypeDefinition(pyFile, definition)
        }

        assertTrue(inserted is PyAssignmentStatement)
        // NewType should be inserted before the function
        val fileText = pyFile.text
        val newTypeIndex = fileText.indexOf("ProductId = NewType")
        val funcIndex = fileText.indexOf("def foo")
        assertTrue("NewType should be before function", newTypeIndex < funcIndex)
    }

    fun testAddRequiredImports_IntoEmptyFile() {
        myFixture.configureByText("a.py", "")

        val pyFile = myFixture.file as PyFile

        WriteCommandAction.runWriteCommandAction(project) {
            generator.addRequiredImports(pyFile)
        }

        val imports = pyFile.importBlock
        val hasNewTypeImport = imports.any { statement ->
            statement is PyFromImportStatement &&
                    statement.importSourceQName?.toString() == "typing" &&
                    statement.importElements.any { it.importedQName?.toString() == "NewType" }
        }
        assertTrue("Should have NewType import", hasNewTypeImport)
    }

    fun testWrapExpressionText_WithComplexExpression() {
        assertEquals("ProductId(x + y)", generator.wrapExpressionText("ProductId", "x + y"))
        assertEquals("ProductId(func())", generator.wrapExpressionText("ProductId", "func()"))
        assertEquals("ProductId(obj.attr)", generator.wrapExpressionText("ProductId", "obj.attr"))
    }
}
