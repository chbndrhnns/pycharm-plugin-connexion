package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*
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

        var inserted: PsiElement? = null
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

    fun testCreateTypeDefinition_WithFloatBuiltin() {
        val definition = generator.createTypeDefinition(project, "Price", "float")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("Price", pyClass.name)
        assertTrue(pyClass.text.contains("value: float"))
        assertTrue(pyClass.text.contains("@dataclass(frozen=True)"))
    }

    fun testCreateTypeDefinition_WithBoolBuiltin() {
        val definition = generator.createTypeDefinition(project, "Flag", "bool")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("Flag", pyClass.name)
        assertTrue(pyClass.text.contains("value: bool"))
    }

    fun testCreateTypeDefinition_WithBytesBuiltin() {
        val definition = generator.createTypeDefinition(project, "BinaryData", "bytes")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("BinaryData", pyClass.name)
        assertTrue(pyClass.text.contains("value: bytes"))
    }

    fun testDetermineBaseTypeText_WithSubscriptedAnnotation() {
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
        // The annotation value for `list[int]` is a PySubscriptionExpression
        // The operand of that subscription is `list` (a PyReferenceExpression)
        // determineBaseTypeText expects the operand (list), and looks at its parent to get the full subscription
        val subscriptionOperand = (annotationValue as? PySubscriptionExpression)?.operand

        // For subscripted annotations, FrozenDataclassGenerator should return the full text
        val baseText = generator.determineBaseTypeText("list", subscriptionOperand)
        assertEquals("list[int]", baseText)
    }

    fun testInsertTypeDefinition_IntoEmptyFile() {
        myFixture.configureByText("a.py", "")

        val pyFile = myFixture.file as PyFile
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        var inserted: PsiElement? = null
        WriteCommandAction.runWriteCommandAction(project) {
            inserted = generator.insertTypeDefinition(pyFile, definition)
        }

        assertTrue(inserted is PyClass)
        assertTrue(pyFile.text.contains("@dataclass(frozen=True)"))
        assertTrue(pyFile.text.contains("class ProductId"))
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

        assertTrue(inserted is PyClass)
        // Class should be inserted before the function
        val fileText = pyFile.text
        val classIndex = fileText.indexOf("class ProductId")
        val funcIndex = fileText.indexOf("def foo")
        assertTrue("Class should be before function", classIndex < funcIndex)
    }

    fun testAddRequiredImports_DoesNotDuplicateExistingImport() {
        myFixture.configureByText(
            "a.py",
            """
            from dataclasses import dataclass
            
            
            def foo():
                pass
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val initialImportCount = pyFile.importBlock.size

        WriteCommandAction.runWriteCommandAction(project) {
            generator.addRequiredImports(pyFile)
        }

        assertEquals("Should not add duplicate import", initialImportCount, pyFile.importBlock.size)
    }

    fun testWrapExpressionText_WithComplexExpression() {
        assertEquals("ProductId(value=x + y)", generator.wrapExpressionText("ProductId", "x + y"))
        assertEquals("ProductId(value=func())", generator.wrapExpressionText("ProductId", "func()"))
        assertEquals("ProductId(value=obj.attr)", generator.wrapExpressionText("ProductId", "obj.attr"))
    }

    fun testCreateTypeDefinition_DoesNotInheritFromBuiltin() {
        val definition = generator.createTypeDefinition(project, "ProductId", "int")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        // Frozen dataclass should NOT inherit from the builtin
        val superClasses = pyClass.superClassExpressions
        assertTrue("Should not inherit from int", superClasses.none { it.text == "int" })
    }
}
