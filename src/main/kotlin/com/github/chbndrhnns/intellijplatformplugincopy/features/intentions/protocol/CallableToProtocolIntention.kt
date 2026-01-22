package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.protocol

import com.github.chbndrhnns.intellijplatformplugincopy.core.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.core.util.isOwnCode
import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext

class CallableToProtocolIntention : IntentionAction, HighPriorityAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Convert callable to Protocol"

    override fun getFamilyName(): String = "Convert callable to Protocol"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableCallableToProtocolIntention) return false
        
        val subscription = findSubscription(project, file, editor) ?: return false
        val operand = subscription.operand

        // 1. Textual Check (Fast path)
        val text = operand.text
        if (text == "Callable" || text == "typing.Callable" || text.endsWith(".Callable")) return true

        // 2. Resolution Check (Handles aliases, e.g. 'from typing import Callable as C')
        if (operand is PyReferenceExpression) {
            val context = PyResolveContext.defaultContext()
            // Try to resolve to the definition
            val resolveResults = operand.getReference(context).multiResolve(false)
            for (result in resolveResults) {
                val element = result.element
                if (isCallableType(element)) return true
            }
            // Fallback to simple resolve
            val simpleResolved = operand.reference.resolve()
            if (isCallableType(simpleResolved)) return true
        }

        return false
    }

    private fun isCallableType(element: PsiElement?): Boolean {
        if (element is PyQualifiedNameOwner) {
            val qName = element.qualifiedName
            if (qName == "typing.Callable" || qName == "collections.abc.Callable") return true
        }
        // If it's an import element, check if it imports Callable
        if (element is PyImportElement) {
             val importedQName = element.importedQName
             if (importedQName != null) {
                 val qNameString = importedQName.toString()
                 if (qNameString == "typing.Callable" || qNameString == "collections.abc.Callable") return true
             }
        }
        return false
    }

    private fun findSubscription(project: Project, file: PsiFile, editor: Editor): PySubscriptionExpression? {
        val offset = editor.caretModel.offset
        
        // 1. Try direct lookup (works if file is injected or normal file)
        var element = file.findElementAt(offset)
        
        // 2. If we are in host file but inside a string/injection, switch to injected element
        if (element != null && !InjectedLanguageManager.getInstance(project).isInjectedFragment(file)) {
            val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset)
            if (injected != null) {
                element = injected
            }
        }
        
        if (element == null) return null
        return PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val subscription = findSubscription(project, file, editor) ?: return
        
        // Determine Host File for insertions (Protocol definition, Imports)
        val hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file)
        if (hostFile !is PyFile) return 

        // Parse structure: Callable[[Args], Return] or Callable[..., Return]
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

        if (argsElement == null || returnElement == null) return

        // Generate Protocol Name (Check collisions in HOST file)
        val baseName = "MyProtocol"
        var protocolName = baseName
        var counter = 1
        
        val existingNames = PsiTreeUtil.findChildrenOfType(hostFile, PyClass::class.java).mapNotNull { it.name }.toSet()
        
        while (existingNames.contains(protocolName)) {
            protocolName = "$baseName$counter"
            counter++
        }

        // Prepare Protocol Body
        val sb = StringBuilder()
        sb.append("class $protocolName(Protocol):\n")
        sb.append("    def __call__(self")
        
        var needsAny = false
        val argsText = argsElement.text

        if ((argsText == "...") || (argsElement is PyReferenceExpression && argsText == "Ellipsis")) {
             // Callable[..., Ret] -> def __call__(self, *args: Any, **kwargs: Any) -> Ret: ...
             sb.append(", *args: Any, **kwargs: Any")
             needsAny = true
        } else if (argsElement is PyListLiteralExpression) {
            // Callable[[A, B], Ret] -> def __call__(self, arg0: A, arg1: B) -> Ret: ...
            val args = argsElement.elements
            if (args.isNotEmpty()) {
                args.forEachIndexed { index, argType ->
                    sb.append(", arg$index: ${argType.text}")
                }
            }
        } else {
             return
        }

        sb.append(") -> ${returnElement.text}: ...\n\n")

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(hostFile)
        val protocolClass = generator.createFromText(languageLevel, PyClass::class.java, sb.toString())

        // Insertion: Top level in HOST file, after imports
        var anchor: PsiElement? = null
        for (child in hostFile.children) {
            if (child is PyImportStatement || child is PyFromImportStatement) {
                anchor = child
            } else if (child !is PsiWhiteSpace && child !is PsiComment) {
                // Stopped seeing imports
                break
            }
        }

        if (anchor != null) {
            hostFile.addAfter(protocolClass, anchor)
        } else {
            // No imports, add to top
            hostFile.addBefore(protocolClass, hostFile.firstChild)
        }

        // Replace Callable with Protocol Name in the ORIGINAL file (could be injected)
        val newRef = generator.createExpressionFromText(LanguageLevel.forElement(file), protocolName)
        subscription.replace(newRef)

        // Add imports to HOST file
        AddImportHelper.addOrUpdateFromImportStatement(
            hostFile,
            "typing",
            "Protocol",
            null,
            AddImportHelper.ImportPriority.BUILTIN,
            null
        )
        
        if (needsAny) {
             AddImportHelper.addOrUpdateFromImportStatement(
                hostFile,
                "typing",
                "Any",
                null,
                AddImportHelper.ImportPriority.BUILTIN,
                null
            )
        }

        CodeStyleManager.getInstance(project).reformat(hostFile)
    }

    override fun startInWriteAction(): Boolean = true

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }
}
