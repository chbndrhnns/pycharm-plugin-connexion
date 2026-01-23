package com.github.chbndrhnns.betterpy.features.intentions.customtype

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

/**
 * Generator strategy for creating NewType aliases.
 *
 * Generates code like: `ProductId = NewType("ProductId", int)`
 *
 * NewType provides static type checking benefits without runtime overhead.
 * At runtime, NewType is essentially an identity function - the value is
 * unchanged, but type checkers treat it as a distinct type.
 */
class NewTypeGenerator : CustomTypeGeneratorStrategy {

    override val kind: CustomTypeKind = CustomTypeKind.NEWTYPE

    override fun determineBaseTypeText(builtinName: String, annotationRef: PyExpression?): String {
        // For NewType, we use the simple builtin name without generic args
        // because NewType("Name", list[int]) is not valid syntax.
        // Generic containers should use the base type: NewType("Name", list)
        return builtinName
    }

    override fun createTypeDefinition(project: Project, name: String, builtin: String): PsiElement {
        val generator = PyElementGenerator.getInstance(project)
        val assignmentText = buildNewTypeText(name, builtin)
        return generator.createFromText(
            LanguageLevel.getLatest(),
            PyAssignmentStatement::class.java,
            assignmentText,
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
        addNewTypeImport(targetFile)
    }

    override fun wrapExpressionText(typeName: String, valueText: String): String {
        // NewType wrapping looks like a function call: TypeName(value)
        // At runtime this is identity, but satisfies type checkers
        return "$typeName($valueText)"
    }

    private fun buildNewTypeText(name: String, builtin: String): String {
        return "$name = NewType(\"$name\", $builtin)"
    }

    private fun addNewTypeImport(targetFile: PyFile) {
        val existingImports = targetFile.importBlock
        val hasNewTypeImport = existingImports.any { statement ->
            when (statement) {
                is PyFromImportStatement -> {
                    statement.importSourceQName?.toString() == "typing" &&
                            statement.importElements.any { it.importedQName?.toString() == "NewType" }
                }

                else -> false
            }
        }

        if (!hasNewTypeImport) {
            val generator = PyElementGenerator.getInstance(targetFile.project)
            val importStatement = generator.createFromText(
                LanguageLevel.getLatest(),
                PyFromImportStatement::class.java,
                "from typing import NewType"
            )

            val lastImport = existingImports.lastOrNull()
            if (lastImport != null) {
                targetFile.addAfter(importStatement, lastImport)
            } else {
                val firstChild = targetFile.firstChild
                if (firstChild != null) {
                    targetFile.addBefore(importStatement, firstChild)
                } else {
                    targetFile.add(importStatement)
                }
            }
        }
    }
}
