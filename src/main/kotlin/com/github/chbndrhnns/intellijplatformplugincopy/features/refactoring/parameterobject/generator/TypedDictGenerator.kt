package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

/**
 * Generator for TypedDict parameter objects.
 *
 * TypedDict is dictionary-like and doesn't support frozen, slots, or kw_only options.
 * Note: Access is via bracket notation (params["field"]) not dot notation.
 */
class TypedDictGenerator : ParameterObjectGenerator {

    private var importsNotRequired = false

    override fun generateClass(
        project: Project,
        languageLevel: LanguageLevel,
        className: String,
        params: List<PyNamedParameter>,
        generateFrozen: Boolean,
        generateSlots: Boolean,
        generateKwOnly: Boolean
    ): PyClass {
        importsNotRequired = false
        val generator = PyElementGenerator.getInstance(project)

        // Create class inheriting from TypedDict
        val pyClass = generator.createFromText(
            languageLevel,
            PyClass::class.java,
            "class $className(TypedDict):\n"
        )

        // Add fields
        addFields(pyClass, generator, languageLevel, params)

        return pyClass
    }

    override fun addRequiredImports(file: PyFile, anchor: PsiElement) {
        AddImportHelper.addOrUpdateFromImportStatement(
            file, "typing", "TypedDict", null, AddImportHelper.ImportPriority.BUILTIN, anchor
        )

        AddImportHelper.addOrUpdateFromImportStatement(
            file, "typing", "Any", null, AddImportHelper.ImportPriority.BUILTIN, anchor
        )

        if (importsNotRequired) {
            AddImportHelper.addOrUpdateFromImportStatement(
                file, "typing", "NotRequired", null, AddImportHelper.ImportPriority.BUILTIN, anchor
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
            val ann = p.annotationValue
            var typeText = ann ?: "Any"

            if (p.defaultValue != null) {
                typeText = "NotRequired[$typeText]"
                importsNotRequired = true
            }

            // TypedDict fields are just type annotations, no default values in the class body
            // Default values would need to be handled at instantiation
            val fieldText = "${p.name}: $typeText"

            // Statement PSI Element Creation and Addition to Class Body
            val fieldStatement = generator.createFromText(languageLevel, PyStatement::class.java, fieldText)
            statementList.add(fieldStatement)
        }
    }
}
