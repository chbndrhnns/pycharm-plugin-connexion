package com.github.chbndrhnns.intellijplatformplugincopy.intention.dictAccess

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyDictAccessIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = "Toggle dictionary access"

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        // 1. Check Bracket Access: d[k]
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java)
        if (subscription != null && subscription.indexExpression != null) {
            // Check context: Assignment target?
            val parent = subscription.parent
            if (parent is PyAssignmentStatement) {
                if (parent.targets.any { it == subscription }) return false
            }
            if (parent is PyAugAssignmentStatement && parent.target == subscription) return false
            if (parent is PyDelStatement) return false

            // Check type
            val context = TypeEvalContext.codeAnalysis(project, element.containingFile)
            val operand = subscription.operand
            val type = context.getType(operand)
            if (isDictType(type, context)) {
                text = "Replace 'dict[key]' with 'dict.get(key)'"
                return true
            }
        }

        // 2. Check Get Access: d.get(k)
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
        if (call != null) {
            val callee = call.callee as? PyReferenceExpression
            if (callee != null && "get" == callee.name) {
                // Check arguments: must be 1
                if (call.arguments.size == 1) {
                    val qualifier = callee.qualifier
                    if (qualifier != null) {
                        val context = TypeEvalContext.codeAnalysis(project, element.containingFile)
                        val type = context.getType(qualifier)
                        if (isDictType(type, context)) {
                            text = "Replace 'dict.get(key)' with 'dict[key]'"
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun isDictType(type: com.jetbrains.python.psi.types.PyType?, context: TypeEvalContext): Boolean {
        if (type == null) return false

        if (type is PyCollectionType) {
            val name = type.name
            if (name == "dict" || name == "Dict" || name == "Mapping" || name == "MutableMapping") return true
        }

        if (type is PyClassType) {
            val qName = type.classQName
            if (qName == "builtins.dict" || qName == "typing.Dict" || qName == "typing.Mapping" || qName == "typing.MutableMapping") return true

            // Check ancestors
            val ancestors = type.getAncestorTypes(context)
            for (ancestor in ancestors) {
                val ancestorQName = ancestor.classQName
                if (ancestorQName == "builtins.dict" || ancestorQName == "typing.Dict" || ancestorQName == "typing.Mapping" || ancestorQName == "typing.MutableMapping") return true
            }
        }

        return false
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val generator = PyElementGenerator.getInstance(project)

        // Try Bracket -> Get
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java)
        if (subscription != null) {
            val operand = subscription.operand

            // Extract content between [ and ]
            val lBracket = subscription.node.findChildByType(PyTokenTypes.LBRACKET)
            val rBracket = subscription.node.findChildByType(PyTokenTypes.RBRACKET)

            if (lBracket != null && rBracket != null) {
                val start = lBracket.textRange.endOffset
                val end = rBracket.textRange.startOffset
                if (start < end) {
                    val content = subscription.containingFile.text.substring(start, end)
                    val newText = "${operand.text}.get($content)"
                    val expression = generator.createExpressionFromText(LanguageLevel.forElement(element), newText)
                    subscription.replace(expression)
                }
            }
            return
        }

        // Try Get -> Bracket
        val call = PsiTreeUtil.getParentOfType(element, PyCallExpression::class.java)
        if (call != null) {
            val callee = call.callee as? PyReferenceExpression ?: return
            val qualifier = callee.qualifier ?: return
            val argumentList = call.argumentList ?: return

            val lPar = argumentList.node.findChildByType(PyTokenTypes.LPAR)
            val rPar = argumentList.node.findChildByType(PyTokenTypes.RPAR)

            if (lPar != null && rPar != null) {
                val start = lPar.textRange.endOffset
                val end = rPar.textRange.startOffset
                if (start < end) {
                    val content = call.containingFile.text.substring(start, end)
                    val newText = "${qualifier.text}[$content]"
                    val expression = generator.createExpressionFromText(LanguageLevel.forElement(element), newText)
                    call.replace(expression)
                }
            }
        }
    }
}
