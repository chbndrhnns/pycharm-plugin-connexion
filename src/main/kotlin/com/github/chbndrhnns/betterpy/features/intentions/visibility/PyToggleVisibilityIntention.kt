package com.github.chbndrhnns.betterpy.features.intentions.visibility

import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.util.Processor
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.search.PyClassInheritorsSearch
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Shared utilities for visibility intentions working on Python symbols.
 */
abstract class PyToggleVisibilityIntention : IntentionAction, HighPriorityAction {

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!isSettingEnabled()) return false
        val symbol = findTargetSymbol(editor, file) ?: return false
        val name = symbol.name ?: return false

        // Ignore conftest.py
        if (file.name == "conftest.py") return false

        // Ignore test functions
        if (symbol is PyFunction && PytestNaming.isTestFunctionName(name)) return false

        // Ignore test classes
        if (symbol is PyClass && PytestNaming.isTestClassName(name, allowLowercasePrefix = false)) return false

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
        val targets = if (element is PyFunction) {
            collectHierarchyMethods(element)
        } else {
            listOf(element)
        }

        val firstTarget = targets.first()
        val processor = RenameProcessor(project, firstTarget, newName, shouldShowPreview(element, newName), false)
        for (i in 1 until targets.size) {
            processor.addElement(targets[i], newName)
        }

        processor.run()
    }

    private fun collectHierarchyMethods(function: PyFunction): List<PsiNamedElement> {
        val name = function.name ?: return listOf(function)
        val project = function.project
        val context = TypeEvalContext.codeAnalysis(project, function.containingFile)

        val allMethods = mutableSetOf<PyFunction>()
        val queue = mutableListOf(function)
        val visited = mutableSetOf(function)

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            allMethods.add(current)

            // Search UP
            PySuperMethodsSearch.search(current, true, context).forEach { superMethod ->
                if (superMethod is PyFunction && visited.add(superMethod)) {
                    queue.add(superMethod)
                }
            }

            // Search DOWN
            val currentClass = current.containingClass
            if (currentClass != null) {
                PyClassInheritorsSearch.search(currentClass, true).forEach { inheritor ->
                    inheritor.findMethodByName(name, false, context)?.let { overridingMethod ->
                        if (overridingMethod.containingClass?.isEquivalentTo(inheritor) == true) {
                            if (visited.add(overridingMethod)) {
                                queue.add(overridingMethod)
                            }
                        }
                    }
                }
            }
        }

        return allMethods.toList()
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
