package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.python.psi.*
import com.jetbrains.python.refactoring.PyReplaceExpressionUtil

object ParameterObjectUtils {
    /**
     * Checks if a name is already taken in the top-level scope of a Python file.
     * Checks for classes, functions, attributes, and imports.
     *
     * @param file The file to check in.
     * @param name The name to check.
     * @return true if the name is already defined or imported in the file.
     */
    fun isNameTaken(file: PyFile, name: String): Boolean {
        if (file.findTopLevelClass(name) != null) return true
        if (file.findTopLevelFunction(name) != null) return true
        if (file.findTopLevelAttribute(name) != null) return true

        for (stmt in file.importBlock) {
            for (element in stmt.importElements) {
                val visibleName = element.asName ?: element.importedQName?.lastComponent
                if (visibleName == name) return true
            }
        }
        return false
    }

    /**
     * Replaces the argument list of a call expression with a new one created from the given text.
     * Logs an error if the argument list cannot be created.
     *
     * @param generator The PyElementGenerator to use for creating the argument list.
     * @param languageLevel The language level to use for parsing.
     * @param call The call expression whose argument list should be replaced.
     * @param newArgsText The text of the new argument list (without parentheses).
     * @param log The logger to use for debug messages.
     */
    fun replaceArgumentList(
        generator: PyElementGenerator,
        languageLevel: LanguageLevel,
        call: PyCallExpression,
        newArgsText: String,
        log: Logger
    ) {
        val newArgListElement = try {
            generator.createArgumentList(languageLevel, "($newArgsText)")
        } catch (e: Exception) {
            log.debug("Failed to create argument list from '$newArgsText'", e)
            null
        }

        if (newArgListElement != null) {
            call.argumentList?.replace(newArgListElement)
        }
    }

    /**
     * Replaces an expression with a new one created from the given text, handling parentheses if needed for precedence.
     *
     * @param generator The PyElementGenerator to use for creating the new expression.
     * @param element The existing element to replace.
     * @param newExpressionText The text of the new expression.
     */
    fun replaceExpression(
        generator: PyElementGenerator,
        element: PyElement,
        newExpressionText: String
    ) {
        val languageLevel = LanguageLevel.forElement(element)
        val newExpr = generator.createExpressionFromText(languageLevel, newExpressionText)

        if (PyReplaceExpressionUtil.isNeedParenthesis(element, newExpr)) {
            val parenthesized = generator.createExpressionFromText(languageLevel, "($newExpressionText)")
            element.replace(parenthesized)
        } else {
            element.replace(newExpr)
        }
    }
}
