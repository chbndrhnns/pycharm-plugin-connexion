package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/**
 * Analyzes selected code to prepare for extract fixture refactoring.
 */
class ExtractFixtureAnalyzer(
    private val file: PyFile,
    private val selectionModel: SelectionModel
) {
    fun analyze(): ExtractFixtureModel? {
        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd

        if (startOffset == endOffset) return null

        val statements = findSelectedStatements(startOffset, endOffset)
        if (statements.isEmpty()) return null

        val containingFunction = findContainingFunction(statements.first()) ?: return null

        val usedFixtures = findUsedFixtures(statements, containingFunction)
        val usedLocals = findUsedLocalVariables(statements, containingFunction)
        val definedLocals = findDefinedLocalVariables(statements)
        val outputVariables = findOutputVariables(statements, containingFunction, endOffset)

        return ExtractFixtureModel(
            containingFunction = containingFunction,
            statements = statements,
            usedFixtures = usedFixtures,
            usedLocals = usedLocals,
            definedLocals = definedLocals,
            outputVariables = outputVariables,
            startOffset = startOffset,
            endOffset = endOffset
        )
    }

    private fun findSelectedStatements(startOffset: Int, endOffset: Int): List<PyStatement> {
        val result = mutableListOf<PyStatement>()

        // Find the containing function first
        val elementAtStart = file.findElementAt(startOffset) ?: return emptyList()
        val function = PsiTreeUtil.getParentOfType(elementAtStart, PyFunction::class.java) ?: return emptyList()
        val statementList = function.statementList

        for (statement in statementList.statements) {
            val range = statement.textRange
            // Include statement if it overlaps with selection
            if (range.startOffset >= startOffset && range.endOffset <= endOffset) {
                result.add(statement)
            } else if (range.startOffset < endOffset && range.endOffset > startOffset) {
                // Partial overlap - include if mostly selected
                result.add(statement)
            }
        }
        return result
    }

    private fun findContainingFunction(element: PyStatement): PyFunction? {
        return PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
    }

    private fun findUsedFixtures(
        statements: List<PyStatement>,
        function: PyFunction
    ): List<FixtureParameter> {
        val usedNames = collectReferencedNames(statements)
        val fixtureParams = mutableListOf<FixtureParameter>()

        for (param in function.parameterList.parameters) {
            val name = param.name ?: continue
            if (name == "self" || name == "cls") continue
            if (name in usedNames) {
                fixtureParams.add(FixtureParameter(name, null))
            }
        }

        return fixtureParams
    }

    private fun findUsedLocalVariables(
        statements: List<PyStatement>,
        function: PyFunction
    ): Set<String> {
        val usedNames = collectReferencedNames(statements)
        val paramNames = function.parameterList.parameters.mapNotNull { it.name }.toSet()
        val definedInSelection = findDefinedLocalVariables(statements)

        // Local variables are those referenced but not parameters and not defined in selection
        return usedNames - paramNames - definedInSelection
    }

    private fun findDefinedLocalVariables(statements: List<PyStatement>): Set<String> {
        val defined = mutableSetOf<String>()

        for (statement in statements) {
            if (statement is PyAssignmentStatement) {
                for (target in statement.targets) {
                    if (target is PyTargetExpression) {
                        target.name?.let { defined.add(it) }
                    }
                }
            }
            // Also check for loop variables, comprehensions, etc.
            PsiTreeUtil.findChildrenOfType(statement, PyTargetExpression::class.java).forEach { target ->
                target.name?.let { defined.add(it) }
            }
        }

        return defined
    }

    private fun findOutputVariables(
        statements: List<PyStatement>,
        function: PyFunction,
        selectionEnd: Int
    ): List<String> {
        val definedInSelection = findDefinedLocalVariables(statements)
        val usedAfterSelection = mutableSetOf<String>()

        // Find variables used after the selection
        val statementList = function.statementList
        for (statement in statementList.statements) {
            if (statement.textRange.startOffset >= selectionEnd) {
                usedAfterSelection.addAll(collectReferencedNames(listOf(statement)))
            }
        }

        // Output variables are those defined in selection and used after
        return definedInSelection.filter { it in usedAfterSelection }
    }

    private fun collectReferencedNames(statements: List<PyStatement>): Set<String> {
        val names = mutableSetOf<String>()

        for (statement in statements) {
            PsiTreeUtil.findChildrenOfType(statement, PyReferenceExpression::class.java).forEach { ref ->
                // Only collect simple references (not qualified like obj.method)
                if (ref.qualifier == null) {
                    ref.name?.let { names.add(it) }
                }
            }
        }

        return names
    }
}
