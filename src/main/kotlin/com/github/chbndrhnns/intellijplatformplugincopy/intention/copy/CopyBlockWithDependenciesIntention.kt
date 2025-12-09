package com.github.chbndrhnns.intellijplatformplugincopy.intention.copy

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import java.awt.datatransfer.StringSelection
import java.util.*

class CopyBlockWithDependenciesIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Copy element with dependencies"
    override fun getText(): String = familyName

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (editor == null) return false
        val target = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, PyClass::class.java)
        return target != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        val target = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, PyClass::class.java) ?: return
        val file = target.containingFile as? PyFile ?: return

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
            // Add extra newline only if it's not the last one, or maybe just generally double newline separator for top-level?
            // Spec test cases show standard spacing. Let's just append "\n" if it's not the very last char? 
            // Actually, copying the text usually includes the newline if it was selected.
            // But we are getting `text` of the element.
            // Let's add an extra newline for separation between functions.
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

        // Check if the reference name matches the visible name of an imported element
        val imports = PsiTreeUtil.findChildrenOfType(file, PyImportStatementBase::class.java)
        for (imp in imports) {
            if (imp is PyImportStatement) {
                for (elt in imp.importElements) {
                    val visibleName = elt.visibleName
                    if (visibleName == name) {
                        return imp
                    }
                    // Handle "import os.path" where usage is "os"
                    val asName = elt.asName
                    if (asName == null) {
                        // if import is "import os", visible name is "os".
                        // if import is "import os.path", visible name is "os". 
                        // Wait, visibleName for "import os.path" is "os"? 
                        // usually "os" is the top level package.

                        // Let's rely on visibleName.
                        // If I have "import os", visibleName="os". usage="os". MATCH.
                        // If I have "import os.path", visibleName="os". usage="os". MATCH.
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
