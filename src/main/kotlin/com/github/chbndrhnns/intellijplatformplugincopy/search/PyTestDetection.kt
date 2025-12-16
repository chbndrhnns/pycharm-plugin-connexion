package com.github.chbndrhnns.intellijplatformplugincopy.search

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFunction

/**
 * Utility object for detecting test classes and functions.
 * Used to exclude test code from protocol implementation searches.
 */
object PyTestDetection {

    /**
     * Checks if a class is a test class (should be excluded from protocol implementations).
     * Test classes are identified by:
     * - Class name starting with "Test" (e.g., TestMyClass)
     * - Class name starting with "test_" (e.g., test_my_class - less common but possible)
     */
    fun isTestClass(pyClass: PyClass): Boolean {
        val name = pyClass.name ?: return false
        return name.startsWith("Test") || name.startsWith("test_")
    }

    /**
     * Checks if a function is a test function (should be excluded from protocol implementations).
     * Test functions are identified by:
     * - Function name starting with "test_" (e.g., test_my_function)
     */
    fun isTestFunction(pyFunction: PyFunction): Boolean {
        val name = pyFunction.name ?: return false
        return name.startsWith("test_")
    }

    fun isPytestFixture(pyFunction: PyFunction): Boolean {
        val decorators = pyFunction.decoratorList?.decorators ?: return false
        return decorators.any { it.isPytestFixtureDecorator() }
    }

    private fun PyDecorator.isPytestFixtureDecorator(): Boolean {
        val qualifiedName = qualifiedName?.toString() ?: return false
        return qualifiedName == "pytest.fixture" || qualifiedName.endsWith(".fixture") || qualifiedName == "fixture"
    }
}
