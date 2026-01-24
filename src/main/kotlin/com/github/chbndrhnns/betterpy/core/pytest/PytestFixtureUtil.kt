package com.github.chbndrhnns.betterpy.core.pytest

import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Utilities for identifying and working with pytest fixtures.
 */
object PytestFixtureUtil {

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
     * Find all fixture decorators on a function.
     */
    fun findFixtureDecorators(function: PyFunction): List<PyDecorator> {
        val decorators = function.decoratorList?.decorators ?: return emptyList()
        return decorators.filter { isFixtureDecorator(it) }
    }
}
