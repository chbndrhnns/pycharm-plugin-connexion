package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.services.TestFailureState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class ReplaceExpectedWithActualIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = "Use actual test outcome"
    override fun getFamilyName(): String = "Use actual test outcome"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false

        // Only available on assert statements
        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return false

        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return false

        // Check if it looks like a test
        val name = pyFunction.name ?: return false
        if (!name.startsWith("test_")) return false

        val locationUrl = calculateLocationUrl(pyFunction) ?: return false
        val diffData = TestFailureState.getInstance(project).getDiffData(locationUrl)

        return diffData != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is PyFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val assertStatement = PsiTreeUtil.getParentOfType(element, PyAssertStatement::class.java) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(assertStatement, PyFunction::class.java) ?: return

        val locationUrl = calculateLocationUrl(pyFunction) ?: return
        val diffData = TestFailureState.getInstance(project).getDiffData(locationUrl) ?: return

        val expected = diffData.expected
        val actual = diffData.actual

        // Strategy: find an expression in the function that matches 'expected'
        // and replace it with 'actual'.

        var replaced = false
        assertStatement.accept(object : PyRecursiveElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
                if (replaced) return
                if (matches(node.stringValue, expected) || matches(node.text, expected)) {
                    replaceElement(node, actual, project)
                    replaced = true
                }
                super.visitPyStringLiteralExpression(node)
            }

            override fun visitPyNumericLiteralExpression(node: PyNumericLiteralExpression) {
                if (replaced) return
                if (matches(node.text, expected)) {
                    replaceElement(node, actual, project)
                    replaced = true
                }
                super.visitPyNumericLiteralExpression(node)
            }
        })
    }

    private fun matches(codeValue: String, diffValue: String): Boolean {
        // Simple exact match first
        if (codeValue == diffValue) return true

        // Handle potential quote differences if diffValue is raw string
        // If code is "foo" and diff is foo
        if (codeValue == "\"$diffValue\"" || codeValue == "'$diffValue'") return true

        return false
    }

    private fun replaceElement(element: PyElement, newValue: String, project: Project) {
        val generator = PyElementGenerator.getInstance(project)

        val newExpression = if (element is PyStringLiteralExpression) {
            // If newValue is not quoted, quote it.
            // Be careful if newValue already has quotes.
            if (newValue.startsWith("\"") || newValue.startsWith("'")) {
                generator.createExpressionFromText(LanguageLevel.getDefault(), newValue)
            } else {
                // It's a raw string value, quote it.
                generator.createStringLiteralAlreadyEscaped("\"$newValue\"")
            }
        } else {
            generator.createExpressionFromText(LanguageLevel.getDefault(), newValue)
        }

        element.replace(newExpression)
    }

    private fun calculateLocationUrl(function: PyFunction): String? {
        val qName = function.qualifiedName ?: return null
        // Standard python location URL often looks like: python:test_module.TestClass.test_method
        return "python:$qName"
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF
}
