package com.github.chbndrhnns.betterpy.features.intentions

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.psi.*

class CaptureUnusedParametersIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Capture unused parameters"

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Capture unused parameters on _"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!PluginSettingsState.instance().state.enableCaptureUnusedParametersIntention) return false

        val file = findFileAtCaret(project, editor, element) ?: return false
        if (file !is PyFile) return false
        if (!file.isOwnCode()) return false

        val function = findFunctionAtCaret(file, editor) ?: element.parentOfType() ?: return false
        if (isInsideDecorator(element, function)) return false

        return findUnusedParameters(function).isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = findFileAtCaret(project, editor, element) ?: return
        val function = findFunctionAtCaret(file, editor) ?: element.parentOfType() ?: return
        val unusedParameters = findUnusedParameters(function)
        if (unusedParameters.isEmpty()) return

        val statementList = function.statementList
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(function)

        val statements = statementList.statements
        val docstringStatement = statements.firstOrNull()?.takeIf { isDocstringStatement(it) }
        val names = unusedParameters.mapNotNull { it.name }
        if (names.isEmpty()) return

        val assignment = generator.createFromText(
            languageLevel,
            PyAssignmentStatement::class.java,
            "_ = ${names.joinToString(", ")}"
        )

        if (docstringStatement != null) {
            statementList.addAfter(assignment, docstringStatement)
        } else {
            val anchor = statements.firstOrNull()
            if (anchor != null) {
                statementList.addBefore(assignment, anchor)
            } else {
                statementList.add(assignment)
            }
        }
    }

    override fun startInWriteAction(): Boolean = true

    private fun findUnusedParameters(function: PyFunction): List<PyNamedParameter> {
        val usedNames = mutableSetOf<String>()
        val visitor = object : PyRecursiveElementVisitor() {
            override fun visitPyFunction(node: PyFunction) {
                if (node !== function) return
                super.visitPyFunction(node)
            }

            override fun visitPyLambdaExpression(node: PyLambdaExpression) {
                // Skip nested scopes to avoid counting their references.
            }

            override fun visitPyReferenceExpression(node: PyReferenceExpression) {
                if (!node.isQualified) {
                    node.name?.let { usedNames.add(it) }
                }
                super.visitPyReferenceExpression(node)
            }
        }
        function.statementList.statements.forEach { it.accept(visitor) }

        return function.parameterList.parameters
            .asSequence()
            .filterIsInstance<PyNamedParameter>()
            .filter { param ->
                val name = param.name ?: return@filter false
                if (name == "_" || name.startsWith("_")) return@filter false
                if (name == "self" || name == "cls") return@filter false
                name !in usedNames
            }
            .toList()
    }

    private fun isInsideDecorator(element: PsiElement, function: PyFunction): Boolean {
        val decoratorList = function.decoratorList ?: return false
        return decoratorList.textRange.contains(element.textRange)
    }

    private fun findFileAtCaret(project: Project, editor: Editor?, element: PsiElement): PsiFile? {
        return editor?.let { PsiDocumentManager.getInstance(project).getPsiFile(it.document) }
            ?: element.containingFile
    }

    private fun findFunctionAtCaret(file: PsiFile, editor: Editor?): PyFunction? {
        if (editor == null) return null
        val leaf = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, PyFunction::class.java)
    }

    private fun isDocstringStatement(statement: PyStatement): Boolean {
        val exprStatement = statement as? PyExpressionStatement ?: return false
        return exprStatement.expression is PyStringLiteralExpression
    }
}
