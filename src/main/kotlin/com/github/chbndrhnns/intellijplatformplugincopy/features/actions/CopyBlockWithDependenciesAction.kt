package com.github.chbndrhnns.intellijplatformplugincopy.features.actions

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import java.awt.datatransfer.StringSelection
import java.util.*

class CopyBlockWithDependenciesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        if (!PluginSettingsState.instance().state.enableCopyBlockWithDependenciesAction) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)

        if (project == null || editor == null || file == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val element = file.findElementAt(editor.caretModel.offset)
        if (element == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val target = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, PyClass::class.java)
        e.presentation.isEnabledAndVisible = target != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) as? PyFile ?: return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val target = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, PyClass::class.java) ?: return

        copyBlockWithDependencies(target, file)
    }

    private fun copyBlockWithDependencies(target: PsiElement, file: PyFile) {
        val visited = HashSet<PsiElement>()
        val queue = ArrayDeque<PsiElement>()
        val imports = HashSet<PyImportStatementBase>()
        val localDefinitions = HashSet<PsiElement>()

        queue.add(target)
        localDefinitions.add(target)
        visited.add(target)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            current.accept(object : PyRecursiveElementVisitor() {
                override fun visitPyReferenceExpression(node: PyReferenceExpression) {
                    super.visitPyReferenceExpression(node)

                    val resolved = node.reference.resolve()

                    if (resolved != null) {
                        if (resolved.containingFile == file) {
                            val topLevel = getTopLevelDefinition(resolved)
                            if (topLevel != null && !visited.contains(topLevel)) {
                                if (topLevel !is PyFile) {
                                    visited.add(topLevel)
                                    queue.add(topLevel)
                                    localDefinitions.add(topLevel)
                                }
                            }
                        } else {
                            val importStmt = findImportStatementFor(node, file)
                            if (importStmt != null) {
                                imports.add(importStmt)
                            }
                        }
                    }
                }
            })
        }

        val sortedImports = imports.sortedBy { it.textRange.startOffset }
        val sortedDefinitions = localDefinitions.sortedBy { it.textRange.startOffset }
        
        // Filter out definitions that are contained in other definitions (e.g. methods inside a copied class)
        val uniqueDefinitions = sortedDefinitions.filter { def ->
            sortedDefinitions.none { other -> 
                other !== def && other.textRange.contains(def.textRange) 
            }
        }

        val sb = StringBuilder()
        for (imp in sortedImports) {
            sb.append(imp.text).append("\n")
        }
        if (sortedImports.isNotEmpty()) sb.append("\n")

        for (def in uniqueDefinitions) {
            sb.append(def.text).append("\n")
            sb.append("\n")
        }

        CopyPasteManager.getInstance().setContents(StringSelection(sb.toString().trimEnd()))
    }

    private fun getTopLevelDefinition(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current.parent !is PyFile) {
            current = current.parent
        }
        return current
    }

    private fun findImportStatementFor(reference: PyReferenceExpression, file: PyFile): PyImportStatementBase? {
        val name = reference.name ?: return null

        val imports = PsiTreeUtil.findChildrenOfType(file, PyImportStatementBase::class.java)
        for (imp in imports) {
            if (imp is PyImportStatement) {
                for (elt in imp.importElements) {
                    val visibleName = elt.visibleName
                    if (visibleName == name) {
                        return imp
                    }
                    val asName = elt.asName
                    if (asName == null) {
                        // if import is "import os", visible name is "os".
                        // if import is "import os.path", visible name is "os".
                    }
                }
            } else if (imp is PyFromImportStatement) {
                for (elt in imp.importElements) {
                    val visibleName = elt.visibleName
                    if (visibleName == name) {
                        return imp
                    }
                }
            }
        }
        return null
    }
}
