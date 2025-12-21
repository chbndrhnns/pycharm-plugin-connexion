package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

/**
 * Generator for pydantic.BaseModel parameter objects.
 *
 * Pydantic models support frozen via model_config, but require an external dependency.
 * All fields are keyword-only by default in Pydantic.
 */
class PydanticGenerator : ParameterObjectGenerator {

    override fun generateClass(
        project: Project,
        languageLevel: LanguageLevel,
        className: String,
        params: List<PyNamedParameter>,
        generateFrozen: Boolean,
        generateSlots: Boolean,
        generateKwOnly: Boolean
    ): PyClass {
        val generator = PyElementGenerator.getInstance(project)

        // Create class inheriting from BaseModel
        val pyClass = generator.createFromText(
            languageLevel,
            PyClass::class.java,
            "class $className(BaseModel):\n"
        )

        // Add fields
        addFields(pyClass, generator, languageLevel, params)

        // Add model_config if frozen is requested
        if (generateFrozen) {
            addModelConfig(pyClass, generator, languageLevel)
        }

        return pyClass
    }

    override fun addRequiredImports(file: PyFile, anchor: PsiElement) {
        AddImportHelper.addOrUpdateFromImportStatement(
            file, "pydantic", "BaseModel", null, AddImportHelper.ImportPriority.THIRD_PARTY, anchor
        )

        AddImportHelper.addOrUpdateFromImportStatement(
            file, "typing", "Any", null, AddImportHelper.ImportPriority.BUILTIN, anchor
        )
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
            val typeText = ann ?: "Any"

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

    private fun addModelConfig(
        pyClass: PyClass,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ) {
        val statementList = pyClass.statementList

        // Add model_config for Pydantic v2 style frozen configuration
        val configStatement = generator.createFromText(
            languageLevel,
            PyStatement::class.java,
            "model_config = {\"frozen\": True}"
        )
        statementList.add(configStatement)
    }
}
