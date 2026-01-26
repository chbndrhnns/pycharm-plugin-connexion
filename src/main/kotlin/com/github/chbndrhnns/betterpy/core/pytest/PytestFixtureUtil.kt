package com.github.chbndrhnns.betterpy.core.pytest

import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

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
        val callee = decorator.callee as? PyQualifiedExpression
        val qName = callee?.asQualifiedName()?.toString()

        // Check for pytest.fixture or _pytest.fixture
        if (qName == "pytest.fixture" ||
            qName == "_pytest.fixture" ||
            qName?.endsWith(".pytest.fixture") == true
        ) {
            return true
        }

        // Check for pytest_asyncio.fixture
        if (qName == "pytest_asyncio.fixture" ||
            qName?.endsWith(".pytest_asyncio.fixture") == true
        ) {
            return true
        }

        // Check for bare "fixture" name (imported)
        if (decorator.name == "fixture") {
            return true
        }

        return false
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
