package com.github.chbndrhnns.intellijplatformplugincopy.intention.visibility

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringFactory
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import javax.swing.Icon

/**
 * Shared utilities for visibility intentions working on Python symbols.
 */
abstract class PyToggleVisibilityIntention : IntentionAction, HighPriorityAction, Iconable {

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = AllIcons.Actions.IntentionBulb

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!isSettingEnabled()) return false
        val symbol = findTargetSymbol(editor, file) ?: return false
        val name = symbol.name ?: return false

        // Ignore conftest.py
        if (file.name == "conftest.py") return false

        // Ignore test functions
        if (symbol is PyFunction && name.startsWith("test_")) return false

        // Ignore test classes
        if (symbol is PyClass && name.startsWith("Test_")) return false

        if (isDunder(name)) return false

        return isAvailableForSymbol(symbol)
    }

    protected open fun isSettingEnabled(): Boolean = PluginSettingsState.instance().state.enableChangeVisibilityIntention

    protected open fun isAvailableForSymbol(symbol: PsiNamedElement): Boolean {
        val name = symbol.name ?: return false
        return isAvailableForName(name)
    }

    protected fun findTargetSymbol(editor: Editor, file: PsiFile): PsiNamedElement? {
        val offset = editor.caretModel.offset
        val atCaret = file.findElementAt(offset) ?: return null
        val named = PsiTreeUtil.getParentOfType(
            atCaret,
            PyFunction::class.java,
            PyClass::class.java,
            PyTargetExpression::class.java
        ) as? PsiNamedElement ?: return null

        if (named is PsiNameIdentifierOwner) {
            val nameId = named.nameIdentifier
            if (nameId != null && (nameId === atCaret || PsiTreeUtil.isAncestor(nameId, atCaret, false))) {
                return named
            }
        }
        return null
    }

    protected fun isDunder(name: String): Boolean = name.length >= 4 && name.startsWith("__") && name.endsWith("__")

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val symbol = findTargetSymbol(editor, file) ?: return
        val name = symbol.name ?: return
        val newName = calcNewName(name) ?: return
        performRename(project, symbol, newName)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val symbol = findTargetSymbol(editor, file) ?: return IntentionPreviewInfo.EMPTY
        val name = symbol.name ?: return IntentionPreviewInfo.EMPTY
        val newName = calcNewName(name) ?: return IntentionPreviewInfo.EMPTY

        IntentionPreviewUtils.write<RuntimeException> { symbol.setName(newName) }
        return IntentionPreviewInfo.DIFF
    }

    /** Return true when intention should be shown for [name]. */
    protected abstract fun isAvailableForName(name: String): Boolean

    protected abstract fun calcNewName(name: String): String?

    protected open fun performRename(project: Project, element: PsiNamedElement, newName: String) {
        val factory = RefactoringFactory.getInstance(project)
        val rename = factory.createRename(element, newName, false, false)

        if (shouldShowPreview(element, newName)) {
            rename.isPreviewUsages = true
        }

        rename.run()
    }

    protected open fun shouldShowPreview(element: PsiNamedElement, newName: String): Boolean {
        val currentName = element.name
        val isMakePrivate = currentName != null && !currentName.startsWith("_") && newName.startsWith("_")

        if (!isMakePrivate) return false

        val containingFile = element.containingFile ?: return true
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val containingClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)

        var hasExternalUsages = false

        runReadAction {
            ReferencesSearch.search(element, scope).forEach(Processor { ref ->
                val refElement = ref.element
                if (refElement.containingFile != null && !refElement.containingFile.isEquivalentTo(containingFile)) {
                    hasExternalUsages = true
                    false
                } else if (containingClass != null && !PsiTreeUtil.isAncestor(containingClass, refElement, false)) {
                    hasExternalUsages = true
                    false
                } else {
                    true
                }
            })
        }

        return hasExternalUsages
    }
}
