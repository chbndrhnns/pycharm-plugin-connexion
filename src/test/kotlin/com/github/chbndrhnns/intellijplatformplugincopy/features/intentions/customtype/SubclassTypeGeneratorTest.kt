package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PySubscriptionExpression
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

        var inserted: PsiElement? = null
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

    fun testCreateTypeDefinition_WithFloatBuiltin() {
        val definition = generator.createTypeDefinition(project, "Price", "float")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("Price", pyClass.name)
        assertTrue(pyClass.text.contains("class Price(float):"))
        assertTrue(pyClass.text.contains("pass"))
    }

    fun testCreateTypeDefinition_WithBoolBuiltin() {
        val definition = generator.createTypeDefinition(project, "Flag", "bool")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("Flag", pyClass.name)
        assertTrue(pyClass.text.contains("class Flag(bool):"))
    }

    fun testCreateTypeDefinition_WithBytesBuiltin() {
        val definition = generator.createTypeDefinition(project, "BinaryData", "bytes")

        assertTrue(definition is PyClass)
        val pyClass = definition as PyClass
        assertEquals("BinaryData", pyClass.name)
        assertTrue(pyClass.text.contains("class BinaryData(bytes):"))
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

        // For subscripted annotations, SubclassTypeGenerator should return the full text
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
        assertTrue(pyFile.text.contains("class ProductId(int):"))
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

    fun testWrapExpressionText_WithComplexExpression() {
        assertEquals("ProductId(x + y)", generator.wrapExpressionText("ProductId", "x + y"))
        assertEquals("ProductId(func())", generator.wrapExpressionText("ProductId", "func()"))
        assertEquals("ProductId(obj.attr)", generator.wrapExpressionText("ProductId", "obj.attr"))
    }
}
