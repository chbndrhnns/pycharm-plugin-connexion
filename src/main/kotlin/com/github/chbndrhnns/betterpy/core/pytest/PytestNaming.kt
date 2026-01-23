package com.github.chbndrhnns.betterpy.core.pytest

import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

object PytestNaming {

    fun isTestFile(file: PyFile): Boolean {
        val name = file.name
        return name.startsWith("test_") || name.endsWith("_test.py")
    }

    fun isTestClass(pyClass: PyClass, allowLowercasePrefix: Boolean = true): Boolean {
        val name = pyClass.name ?: return false
        return isTestClassName(name, allowLowercasePrefix)
    }

    fun isTestClassName(name: String, allowLowercasePrefix: Boolean = true): Boolean {
        return name.startsWith("Test") || (allowLowercasePrefix && name.startsWith("test_"))
    }

    fun isTestFunction(pyFunction: PyFunction): Boolean {
        val name = pyFunction.name ?: return false
        return isTestFunctionName(name)
    }

    fun isTestFunctionName(name: String): Boolean {
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