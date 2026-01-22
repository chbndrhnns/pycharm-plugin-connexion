package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.jetbrains.python.psi.*

/**
 * Builds lightweight intention previews (without mutating PSI) for pytest.param conversions.
 */
object PytestParamPreview {

    fun buildConvertToPytestParam(
        file: PyFile,
        decorator: PyDecorator,
        valuesList: PyListLiteralExpression
    ): IntentionPreviewInfo {
        val importLine = renderPytestImportLineIfMissing(file)
        val afterListText = renderPytestParamWrappedList(valuesList)
        return buildDecoratorDiff(file, decorator, valuesList, afterListText, importLine)
    }

    fun buildConvertToPlain(
        file: PyFile,
        decorator: PyDecorator,
        valuesList: PyListLiteralExpression
    ): IntentionPreviewInfo {
        val afterListText = renderPlainList(valuesList)
        return buildDecoratorDiff(file, decorator, valuesList, afterListText, importLine = null)
    }

    private fun buildDecoratorDiff(
        file: PyFile,
        decorator: PyDecorator,
        originalList: PyListLiteralExpression,
        afterListText: String,
        importLine: String?
    ): IntentionPreviewInfo {
        val beforeDecoratorText = decorator.text
        val afterDecoratorText = beforeDecoratorText.replaceFirst(originalList.text, afterListText)

        val before = buildString {
            if (importLine != null) append(importLine).append('\n')
            append(beforeDecoratorText)
        }
        val after = buildString {
            if (importLine != null) append(importLine).append('\n')
            append(afterDecoratorText)
        }
        return IntentionPreviewInfo.CustomDiff(
            file.fileType,
            file.name,
            before,
            after
        )
    }

    private fun renderPytestImportLineIfMissing(file: PyFile): String? {
        // PSI-based import analysis would be ideal, but for preview we keep it simple and robust.
        val text = file.text
        if (text.contains("import pytest") || text.contains("from pytest")) return null
        return "import pytest"
    }

    private fun renderPytestParamWrappedList(listExpr: PyListLiteralExpression): String {
        val rendered = listExpr.elements.joinToString(", ") { expr ->
            if (expr is PyCallExpression && isPytestParamCall(expr)) {
                expr.text
            } else {
                "pytest.param(${expr.text})"
            }
        }
        return "[$rendered]"
    }

    private fun renderPlainList(listExpr: PyListLiteralExpression): String {
        val rendered = listExpr.elements.joinToString(", ") { expr ->
            val callExpr = expr as? PyCallExpression
            if (callExpr != null && isConvertiblePytestParamCall(callExpr)) {
                val firstArg = callExpr.arguments.firstOrNull()
                firstArg?.text ?: expr.text
            } else {
                expr.text
            }
        }
        return "[$rendered]"
    }

    private fun isPytestParamCall(callExpr: PyCallExpression): Boolean {
        val callee = callExpr.callee as? PyReferenceExpression ?: return false
        val qName = callee.asQualifiedName()?.toString() ?: return false
        return qName == "pytest.param" || qName.endsWith(".pytest.param")
    }

    private fun isConvertiblePytestParamCall(callExpr: PyCallExpression): Boolean {
        if (!isPytestParamCall(callExpr)) return false

        val args = callExpr.arguments
        if (args.isEmpty()) return false

        val hasKeywordArgs = args.any { it is PyKeywordArgument }
        if (hasKeywordArgs) return false

        val positionalArgs = args.filterNot { it is PyKeywordArgument }
        return positionalArgs.size == 1
    }
}
