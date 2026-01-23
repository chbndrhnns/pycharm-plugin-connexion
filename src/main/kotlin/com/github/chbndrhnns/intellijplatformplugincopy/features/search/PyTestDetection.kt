package com.github.chbndrhnns.intellijplatformplugincopy.features.search

import com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.PytestNaming
import com.jetbrains.python.psi.PyClass
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
        return PytestNaming.isTestClass(pyClass, allowLowercasePrefix = true)
    }

    /**
     * Checks if a function is a test function (should be excluded from protocol implementations).
     * Test functions are identified by:
     * - Function name starting with "test_" (e.g., test_my_function)
     */
    fun isTestFunction(pyFunction: PyFunction): Boolean {
        return PytestNaming.isTestFunction(pyFunction)
    }

    fun isPytestFixture(pyFunction: PyFunction): Boolean {
        return PytestNaming.isPytestFixture(pyFunction)
    }
}
