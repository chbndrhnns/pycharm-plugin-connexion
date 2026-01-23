package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

/**
 * Generator strategy for creating simple subclass custom types.
 *
 * Generates code like: `class ProductId(int): pass`
 *
 * This is the original/default behavior of the "Introduce custom type" refactoring.
 */
class SubclassTypeGenerator : CustomTypeGeneratorStrategy {

    override val kind: CustomTypeKind = CustomTypeKind.SUBCLASS

    override fun determineBaseTypeText(builtinName: String, annotationRef: PyExpression?): String {
        val sub = annotationRef?.parent as? PySubscriptionExpression
        return if (sub != null && sub.operand == annotationRef) {
            sub.text
        } else {
            builtinName
        }
    }

    override fun createTypeDefinition(project: Project, name: String, builtin: String): PsiElement {
        val generator = PyElementGenerator.getInstance(project)
        val classText = buildClassText(name, builtin)
        return generator.createFromText(
            LanguageLevel.getLatest(),
            PyClass::class.java,
            classText,
        )
    }

    override fun insertTypeDefinition(targetFile: PyFile, typeDefinition: PsiElement): PsiElement {
        val importAnchor = targetFile.importBlock.lastOrNull()
        val inserted = if (importAnchor != null) {
            targetFile.addAfter(typeDefinition, importAnchor)
        } else {
            val firstChild = targetFile.firstChild
            targetFile.addBefore(typeDefinition, firstChild)
        }
        return inserted
    }

    override fun addRequiredImports(targetFile: PyFile) {
        // Subclass types don't require any additional imports
    }

    override fun wrapExpressionText(typeName: String, valueText: String): String {
        return "$typeName($valueText)"
    }

    private fun buildClassText(name: String, builtin: String): String {
        val body = if (builtin == "str") "__slots__ = ()" else "pass"
        return "class $name($builtin):\n    $body"
    }
}
