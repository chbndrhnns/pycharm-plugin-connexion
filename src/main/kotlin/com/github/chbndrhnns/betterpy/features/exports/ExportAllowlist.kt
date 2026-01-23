package com.github.chbndrhnns.betterpy.features.exports

import com.intellij.psi.PsiNameIdentifierOwner
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction

object ExportAllowlist {
    private val ALLOWLISTED_MODULE_NAME_PREFIXES = listOf("test_", "tests_")
    private val ALLOWLISTED_EXACT_MODULE_NAMES = setOf("tests")
    private val ALLOWLISTED_FUNCTION_NAME_PREFIXES = listOf("test_")
    private val ALLOWLISTED_CLASS_NAME_PREFIXES = listOf("Test")

    fun isAllowlistedModuleName(name: String): Boolean {
        return name in ALLOWLISTED_EXACT_MODULE_NAMES ||
                ALLOWLISTED_MODULE_NAME_PREFIXES.any { name.startsWith(it) }
    }

    fun isAllowlistedFunctionName(name: String): Boolean {
        return ALLOWLISTED_FUNCTION_NAME_PREFIXES.any { name.startsWith(it) }
    }

    fun isAllowlistedClassName(name: String): Boolean {
        return ALLOWLISTED_CLASS_NAME_PREFIXES.any { name.startsWith(it) }
    }

    fun isAllowlistedSymbol(element: PyElement): Boolean {
        val name = (element as? PsiNameIdentifierOwner)?.name ?: return false
        return when (element) {
            is PyFunction -> isAllowlistedFunctionName(name)
            is PyClass -> isAllowlistedClassName(name)
            else -> false
        }
    }
}
