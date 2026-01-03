package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.command.WriteCommandAction
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile
import fixtures.TestBase

/**
 * Integration tests for value object wrapping transformations.
 *
 * Verifies that when using Frozen Dataclass or Pydantic Value Object types,
 * expressions are wrapped with `TypeName(value=expr)` syntax instead of
 * the simple `TypeName(expr)` syntax used for subclass types.
 */
class ValueObjectWrappingTest : TestBase() {

    private val rewriter = UsageRewriter()

    fun testWrapExpression_WithFrozenDataclass_UsesValueSyntax() {
        myFixture.configureByText(
            "a.py",
            """
            val = 123
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val generator = PyElementGenerator.getInstance(project)
        val frozenDataclassStrategy = FrozenDataclassGenerator()

        // Find the expression to wrap
        val expr = pyFile.statements.first().children.last() as PyExpression

        WriteCommandAction.runWriteCommandAction(project) {
            rewriter.wrapExpression(expr, "ProductId", generator, frozenDataclassStrategy)
        }

        myFixture.checkResult(
            """
            val = ProductId(value=123)
            """.trimIndent()
        )
    }

    fun testWrapExpression_WithPydanticValueObject_UsesValueSyntax() {
        myFixture.configureByText(
            "a.py",
            """
            val = "hello"
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val generator = PyElementGenerator.getInstance(project)
        val pydanticStrategy = PydanticValueObjectGenerator()

        // Find the expression to wrap
        val expr = pyFile.statements.first().children.last() as PyExpression

        WriteCommandAction.runWriteCommandAction(project) {
            rewriter.wrapExpression(expr, "CustomStr", generator, pydanticStrategy)
        }

        myFixture.checkResult(
            """
            val = CustomStr(value="hello")
            """.trimIndent()
        )
    }

    fun testWrapExpression_WithSubclass_UsesSimpleSyntax() {
        myFixture.configureByText(
            "a.py",
            """
            val = 123
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val generator = PyElementGenerator.getInstance(project)
        val subclassStrategy = SubclassTypeGenerator()

        // Find the expression to wrap
        val expr = pyFile.statements.first().children.last() as PyExpression

        WriteCommandAction.runWriteCommandAction(project) {
            rewriter.wrapExpression(expr, "ProductId", generator, subclassStrategy)
        }

        myFixture.checkResult(
            """
            val = ProductId(123)
            """.trimIndent()
        )
    }

    fun testWrapExpression_WithNewType_UsesSimpleSyntax() {
        myFixture.configureByText(
            "a.py",
            """
            val = 456
            """.trimIndent()
        )

        val pyFile = myFixture.file as PyFile
        val generator = PyElementGenerator.getInstance(project)
        val newTypeStrategy = NewTypeGenerator()

        // Find the expression to wrap
        val expr = pyFile.statements.first().children.last() as PyExpression

        WriteCommandAction.runWriteCommandAction(project) {
            rewriter.wrapExpression(expr, "ProductId", generator, newTypeStrategy)
        }

        myFixture.checkResult(
            """
            val = ProductId(456)
            """.trimIndent()
        )
    }

    fun testFrozenDataclassGenerator_RequiresValueAccess() {
        val generator = FrozenDataclassGenerator()
        assertTrue("Frozen dataclass should require value access", generator.requiresValueAccess)
    }

    fun testPydanticValueObjectGenerator_RequiresValueAccess() {
        val generator = PydanticValueObjectGenerator()
        assertTrue("Pydantic value object should require value access", generator.requiresValueAccess)
    }

    fun testSubclassGenerator_DoesNotRequireValueAccess() {
        val generator = SubclassTypeGenerator()
        assertFalse("Subclass should not require value access", generator.requiresValueAccess)
    }

    fun testNewTypeGenerator_DoesNotRequireValueAccess() {
        val generator = NewTypeGenerator()
        assertFalse("NewType should not require value access", generator.requiresValueAccess)
    }
}
