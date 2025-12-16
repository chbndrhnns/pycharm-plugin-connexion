package com.github.chbndrhnns.intellijplatformplugincopy.intention.protocol

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*

class CallableToProtocolIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = "Convert callable to Protocol"

    override fun getFamilyName(): String = "Convert callable to Protocol"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableCallableToProtocolIntention) return false
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java) ?: return false

        // Check if it is a Callable
        val operand = subscription.operand as? PyReferenceExpression ?: return false
        val name = operand.name
        return name == "Callable" // Simplified check, ideally verify import
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java) ?: return

        // Structure of Callable[[Args], ReturnType]
        // subscription.indexExpression is the content inside [...]
        // It is usually a PyTupleExpression: (Args, ReturnType)
        // Args is usually a PyListLiteralExpression or PyTupleExpression (args list)

        val indexExpr = subscription.indexExpression
        var argsElement: PyExpression? = null
        var returnElement: PyExpression? = null

        if (indexExpr is PyTupleExpression) {
            val elements = indexExpr.elements
            if (elements.size >= 2) {
                argsElement = elements[0]
                returnElement = elements[1]
            }
        }

        if (argsElement == null || returnElement == null) return // Can't parse Callable structure

        val argsList = when (argsElement) {
            is PyListLiteralExpression -> argsElement.elements
            else -> emptyArray() // Handle ... or other cases later
        }

        // Generate Protocol Name
        val protocolName = "MyProtocol" // TODO: Unique name generation

        // Generate Protocol Code
        val sb = StringBuilder()
        sb.append("class $protocolName(Protocol):\n")
        sb.append("    def __call__(self")

        argsList.forEachIndexed { index, argType ->
            sb.append(", arg$index: ${argType.text}")
        }

        sb.append(") -> ${returnElement.text}: ...\n\n")

        val generator = PyElementGenerator.getInstance(project)
        val parentFunction = PsiTreeUtil.getParentOfType(subscription, PyFunction::class.java)
        val parentClass = PsiTreeUtil.getParentOfType(subscription, PyClass::class.java)

        // Insertion point: 
        // If in function, insert before function.
        // If in class, insert before class.
        // Fallback: top of file (after imports).

        val anchor = parentFunction ?: parentClass

        val languageLevel = LanguageLevel.forElement(file)
        val protocolClass = generator.createFromText(languageLevel, PyClass::class.java, sb.toString())

        if (anchor != null && anchor.parent is PyFile) {
            file.addBefore(protocolClass, anchor)
        } else {
            // Just add to end of file for now if no context found, or try to be smarter
            file.add(protocolClass)
        }

        // Replace Callable with Protocol Name
        val newRef = generator.createExpressionFromText(LanguageLevel.forElement(file), protocolName)
        subscription.replace(newRef)

        // Import Protocol if needed
        AddImportHelper.addOrUpdateFromImportStatement(
            file,
            "typing",
            "Protocol",
            null,
            AddImportHelper.ImportPriority.BUILTIN,
            anchor
        )

        CodeStyleManager.getInstance(project).reformat(file)
    }

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }
}
