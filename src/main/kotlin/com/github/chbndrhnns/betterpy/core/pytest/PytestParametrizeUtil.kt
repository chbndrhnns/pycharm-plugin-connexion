package com.github.chbndrhnns.betterpy.core.pytest

import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.jetbrains.python.psi.*

object PytestParametrizeUtil {

    fun isParametrizeDecorator(decorator: PyDecorator, allowBareName: Boolean = false): Boolean {
        val callee = decorator.callee as? PyQualifiedExpression
        val qName = callee?.asQualifiedName()?.toString()
        if (qName == "pytest.mark.parametrize" ||
            qName == "_pytest.mark.parametrize" ||
            qName?.endsWith(".pytest.mark.parametrize") == true
        ) {
            return true
        }
        return allowBareName && decorator.name == "parametrize"
    }

    fun findParametrizeDecorator(function: PyFunction, allowBareName: Boolean = false): PyDecorator? {
        val decorators = function.decoratorList?.decorators ?: return null
        return decorators.firstOrNull { isParametrizeDecorator(it, allowBareName) }
    }

    fun findParameterValuesListArgument(argumentList: PyArgumentList): PyListLiteralExpression? {
        val args = argumentList.arguments
        if (args.size < 2) return null
        return args[1] as? PyListLiteralExpression
    }

    fun extractParameterNames(namesArg: PyExpression): List<String>? {
        return when (namesArg) {
            is PyStringLiteralExpression -> namesArg.stringValue.split(',').map { it.trim() }
            is PyListLiteralExpression -> namesArg.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            is PyTupleExpression -> namesArg.elements.mapNotNull { (it as? PyStringLiteralExpression)?.stringValue }
            is PyParenthesizedExpression -> {
                val contained = namesArg.containedExpression
                if (contained != null) extractParameterNames(contained) else null
            }
            else -> null
        }
    }

    fun isPytestParamCall(callExpr: PyCallExpression): Boolean {
        val callee = callExpr.callee as? PyReferenceExpression ?: return false
        val qName = callee.asQualifiedName()?.toString() ?: return false
        return qName == "pytest.param" || qName.endsWith(".pytest.param")
    }

    fun canConvertToPlain(callExpr: PyCallExpression): Boolean {
        val args = callExpr.arguments
        if (args.isEmpty()) return false

        val hasKeywordArgs = args.any { it is PyKeywordArgument }
        if (hasKeywordArgs) return false

        val positionalArgs = args.filterNot { it is PyKeywordArgument }
        return positionalArgs.size == 1
    }

    fun collectAllParametrizeNames(function: PyFunction): Set<String> {
        val result = mutableSetOf<String>()
        val decorators = function.decoratorList?.decorators ?: return result
        for (decorator in decorators) {
            if (!isParametrizeDecorator(decorator, allowBareName = true)) continue
            val args = decorator.argumentList?.arguments ?: continue
            if (args.isEmpty()) continue
            val namesArg = if (args[0] !is PyKeywordArgument) {
                args[0]
            } else {
                decorator.getKeywordArgument("argnames") ?: continue
            }
            extractParameterNames(namesArg)?.let { result.addAll(it) }
        }
        return result
    }

    fun ensurePytestImported(file: PyFile) {
        PyImportService().ensureModuleImported(file, "pytest")
    }
}
