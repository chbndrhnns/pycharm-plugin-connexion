package com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.testtree

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

object PytestTestContextUtils {

    fun isInTestContext(element: PsiElement): Boolean {
        val file = element.containingFile as? PyFile ?: return false
        if (!isTestFile(file)) return false

        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
        if (function != null && isTestFunction(function)) return true

        val clazz = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        if (clazz != null && isTestClass(clazz)) return true

        return false
    }

    fun isTestFile(file: PyFile): Boolean {
        val name = file.name
        return name.startsWith("test_") || name.endsWith("_test.py")
    }

    fun isTestFunction(function: PyFunction): Boolean {
        val name = function.name ?: return false
        if (name.startsWith("test_")) return true

        val clazz = PsiTreeUtil.getParentOfType(function, PyClass::class.java, false)
        return clazz != null && isTestClass(clazz)
    }

    fun isTestClass(clazz: PyClass): Boolean {
        val name = clazz.name ?: return false
        return name.startsWith("Test")
    }
}
