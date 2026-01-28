package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

/**
 * Generator for NamedTuple parameter objects.
 *
 * NamedTuple is always immutable and doesn't support frozen, slots, or kw_only options.
 * Default values must be at the end (like function parameters).
 */
class NamedTupleGenerator : ParameterObjectGenerator {

    private var needsAny = false

    override fun generateClass(
        project: Project,
        languageLevel: LanguageLevel,
        className: String,
        params: List<PyNamedParameter>,
        generateFrozen: Boolean,
        generateSlots: Boolean,
        generateKwOnly: Boolean
    ): PyClass {
        needsAny = false
        val generator = PyElementGenerator.getInstance(project)

        // Create class inheriting from NamedTuple
        val pyClass = generator.createFromText(
            languageLevel,
            PyClass::class.java,
            "class $className(NamedTuple):\n"
        )

        // Add fields
        addFields(pyClass, generator, languageLevel, params)

        return pyClass
    }

    override fun addRequiredImports(file: PyFile, anchor: PsiElement) {
        AddImportHelper.addOrUpdateFromImportStatement(
            file, "typing", "NamedTuple", null, AddImportHelper.ImportPriority.BUILTIN, anchor
        )

        if (needsAny) {
            AddImportHelper.addOrUpdateFromImportStatement(
                file, "typing", "Any", null, AddImportHelper.ImportPriority.BUILTIN, anchor
            )
        }
    }

    private fun addFields(
        pyClass: PyClass,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        params: List<PyNamedParameter>
    ) {
        val statementList = pyClass.statementList
        // Remove the initially generated 'pass' statement
        statementList.statements.firstOrNull()?.delete()

        for (p in params) {
            val ann = p.annotation?.value
            val typeText = if (ann != null) {
                if (annotationUsesUnqualifiedAny(ann)) {
                    needsAny = true
                }
                ann.text
            } else {
                needsAny = true
                "Any"
            }

            // Text generation for each field (e.g., name: str = "default")
            val fieldText = StringBuilder().apply {
                append(p.name)
                append(": ")
                append(typeText)
                if (p.defaultValue != null) {
                    append(" = ")
                    append(p.defaultValueText)
                }
            }.toString()

            // Statement PSI Element Creation and Addition to Class Body
            val fieldStatement = generator.createFromText(languageLevel, PyStatement::class.java, fieldText)
            statementList.add(fieldStatement)
        }
    }
}
