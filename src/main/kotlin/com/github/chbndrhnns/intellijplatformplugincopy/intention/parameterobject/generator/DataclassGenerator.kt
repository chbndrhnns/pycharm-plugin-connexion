package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

/**
 * Generator for @dataclass parameter objects.
 */
class DataclassGenerator : ParameterObjectGenerator {

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

        // 1. Basic class shell generation (class ClassName: pass)
        val pyClass = generator.createFromText(languageLevel, PyClass::class.java, "class $className:\n")

        // 2. @dataclass decorator creation and addition
        val decoratorArgs = mutableListOf<String>()
        if (generateFrozen) decoratorArgs.add("frozen=True")
        if (generateSlots) decoratorArgs.add("slots=True")
        if (generateKwOnly) decoratorArgs.add("kw_only=True")

        val decoratorText = if (decoratorArgs.isNotEmpty()) {
            "@dataclass(${decoratorArgs.joinToString(", ")})"
        } else {
            "@dataclass"
        }

        // Decorator list creation
        val decoratorList = generator.createDecoratorList(decoratorText)

        // Add decorator before class definition ('class' keyword)
        val classKeyword = pyClass.firstChild
        pyClass.addBefore(decoratorList, classKeyword)
        // Add a newline between the decorator and the class definition
        pyClass.addBefore(generator.createNewLine(), classKeyword)

        // 3. Add fields
        addFields(pyClass, generator, languageLevel, params)

        return pyClass
    }

    override fun addRequiredImports(file: PyFile, anchor: PsiElement) {
        AddImportHelper.addOrUpdateFromImportStatement(
            file, "typing", "Any", null, AddImportHelper.ImportPriority.BUILTIN, anchor
        )

        AddImportHelper.addOrUpdateFromImportStatement(
            file, "dataclasses", "dataclass", null, AddImportHelper.ImportPriority.BUILTIN, anchor
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
}
