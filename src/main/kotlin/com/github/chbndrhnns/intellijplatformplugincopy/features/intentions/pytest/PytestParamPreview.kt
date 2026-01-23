package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.features.pytest.PytestParametrizeUtil
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyListLiteralExpression

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
            if (expr is PyCallExpression && PytestParametrizeUtil.isPytestParamCall(expr)) {
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
        return PytestParametrizeUtil.isPytestParamCall(callExpr)
    }

    private fun isConvertiblePytestParamCall(callExpr: PyCallExpression): Boolean {
        if (!isPytestParamCall(callExpr)) return false
        return PytestParametrizeUtil.canConvertToPlain(callExpr)
    }
}
