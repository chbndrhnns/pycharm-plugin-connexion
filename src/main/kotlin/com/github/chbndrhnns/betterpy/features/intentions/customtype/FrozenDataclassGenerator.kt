package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

/**
 * Generator strategy for creating frozen dataclass value objects.
 *
 * Generates code like:
 * ```python
 * @dataclass(frozen=True)
 * class ProductId:
 *     value: int
 * ```
 *
 * This type requires `.value` access to get the underlying value.
 */
class FrozenDataclassGenerator : CustomTypeGeneratorStrategy {

    override val kind: CustomTypeKind = CustomTypeKind.FROZEN_DATACLASS

    override val requiresValueAccess: Boolean = true

    override fun determineBaseTypeText(builtinName: String, annotationRef: PyExpression?): String {
        // For value objects, we don't inherit from the builtin, so we just need the type name
        // for the `value` field annotation
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
        val anchor = targetFile.statements.firstOrNull() ?: return
        AddImportHelper.addOrUpdateFromImportStatement(
            targetFile,
            "dataclasses",
            "dataclass",
            null,
            AddImportHelper.ImportPriority.BUILTIN,
            anchor
        )
    }

    override fun wrapExpressionText(typeName: String, valueText: String): String {
        return "$typeName(value=$valueText)"
    }

    private fun buildClassText(name: String, builtin: String): String {
        return """@dataclass(frozen=True)
class $name:
    value: $builtin"""
    }
}
