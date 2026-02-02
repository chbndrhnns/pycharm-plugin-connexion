package com.github.chbndrhnns.betterpy.core.pytest

import com.jetbrains.python.psi.*

/**
 * Utilities for identifying and working with pytest fixtures.
 */
object PytestFixtureUtil {
    enum class PytestFixtureScope(val value: String, val order: Int) {
        FUNCTION("function", 0),
        CLASS("class", 1),
        MODULE("module", 2),
        PACKAGE("package", 3),
        SESSION("session", 4);

        companion object {
            fun fromValue(value: String): PytestFixtureScope? {
                val normalized = value.trim().lowercase()
                return values().firstOrNull { it.value == normalized }
            }
        }
    }


    /**
     * Check if a decorator is a pytest fixture decorator.
     * Recognizes: @pytest.fixture, @fixture (if imported), @pytest_asyncio.fixture
     */
    fun isFixtureDecorator(decorator: PyDecorator): Boolean {
        return isFixtureDecoratorCallee(decorator.callee)
    }

    /**
     * Check if a function is a pytest fixture.
     */
    fun isFixtureFunction(function: PyFunction): Boolean {
        val decorators = function.decoratorList?.decorators ?: return false
        return decorators.any { isFixtureDecorator(it) }
    }

    /**
     * Get the fixture name for a function.
     * Returns the name= argument value if present, otherwise the function name.
     */
    fun getFixtureName(function: PyFunction): String? {
        if (!isFixtureFunction(function)) return null

        val fixtureDecorator = function.decoratorList?.decorators?.firstOrNull { isFixtureDecorator(it) }
            ?: return null

        // Check for name= argument
        val argumentList = fixtureDecorator.argumentList
        if (argumentList != null) {
            val nameArg = argumentList.getKeywordArgument("name")
            if (nameArg != null) {
                val valueExpr = nameArg.valueExpression
                if (valueExpr is PyStringLiteralExpression) {
                    return valueExpr.stringValue
                }
            }
        }

        // Default to function name
        return function.name
    }

    data class AssignedFixture(
        val fixtureName: String,
        val fixtureFunction: PyFunction
    )

    /**
     * Extract pytest fixture assignments like: my_fixture = pytest.fixture()(_impl)
     */
    fun getAssignedFixture(assignment: PyAssignmentStatement): AssignedFixture? {
        val target = assignment.targets.singleOrNull() as? PyTargetExpression ?: return null
        val fixtureName = target.name ?: return null
        val assignedValue = assignment.assignedValue as? PyCallExpression ?: return null
        val fixtureFunction = resolveFixtureFunctionFromAssignment(assignedValue) ?: return null
        return AssignedFixture(fixtureName, fixtureFunction)
    }

    private fun resolveFixtureFunctionFromAssignment(call: PyCallExpression): PyFunction? {
        val callee = call.callee
        val decoratorCall = callee as? PyCallExpression
        if (decoratorCall != null && isFixtureDecoratorCallee(decoratorCall.callee)) {
            return resolveFixtureFunctionArgument(call)
        }
        if (isFixtureDecoratorCallee(callee)) {
            return resolveFixtureFunctionArgument(call)
        }
        return null
    }

    private fun resolveFixtureFunctionArgument(call: PyCallExpression): PyFunction? {
        val firstArg = call.arguments.firstOrNull() ?: return null
        val resolved = (firstArg as? PyReferenceExpression)?.reference?.resolve()
        return resolved as? PyFunction
    }

    private fun isFixtureDecoratorCallee(callee: PyExpression?): Boolean {
        val qualified = (callee as? PyQualifiedExpression)?.asQualifiedName()?.toString()
        if (qualified == "pytest.fixture" ||
            qualified == "_pytest.fixture" ||
            qualified?.endsWith(".pytest.fixture") == true
        ) {
            return true
        }
        if (qualified == "pytest_asyncio.fixture" ||
            qualified?.endsWith(".pytest_asyncio.fixture") == true
        ) {
            return true
        }
        val reference = callee as? PyReferenceExpression
        if (reference?.name == "fixture") {
            return true
        }
        return false
    }

    /**
     * Get the fixture scope for a function.
     * Returns "function" if no scope is specified.
     */
    fun getFixtureScope(function: PyFunction): PytestFixtureScope? {
        if (!isFixtureFunction(function)) return null

        val fixtureDecorator = function.decoratorList?.decorators?.firstOrNull { isFixtureDecorator(it) }
            ?: return null

        val argumentList = fixtureDecorator.argumentList
        if (argumentList != null) {
            val scopeArg = argumentList.getKeywordArgument("scope")
            if (scopeArg != null) {
                val valueExpr = scopeArg.valueExpression
                if (valueExpr is PyStringLiteralExpression) {
                    return PytestFixtureScope.fromValue(valueExpr.stringValue)
                }
                return null
            }
        }

        return PytestFixtureScope.FUNCTION
    }

    /**
     * Find all fixture decorators on a function.
     */
    fun findFixtureDecorators(function: PyFunction): List<PyDecorator> {
        val decorators = function.decoratorList?.decorators ?: return emptyList()
        return decorators.filter { isFixtureDecorator(it) }
    }
}
