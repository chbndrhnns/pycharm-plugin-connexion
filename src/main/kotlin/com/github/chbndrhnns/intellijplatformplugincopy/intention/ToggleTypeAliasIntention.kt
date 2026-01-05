package com.github.chbndrhnns.intellijplatformplugincopy.intention

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class ToggleTypeAliasIntention : IntentionAction, HighPriorityAction, DumbAware {
    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Toggle type alias"
    override fun getFamilyName(): String = "Toggle type alias"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableToggleTypeAliasIntention) return false
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false

        // Only available if caret is on a type annotation
        return findAnnotation(element) != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val typeAlias = findTypeAlias(element)
        if (typeAlias != null) {
            val (name, valueText) = when (typeAlias) {
                is PyTypeAliasStatement -> typeAlias.name to typeAlias.typeExpression?.text
                is PyTargetExpression -> typeAlias.name to (typeAlias.parent as? PyAssignmentStatement)?.assignedValue?.text
                else -> null to null
            }
            if (name == null || valueText == null) return

            val references = try {
                com.intellij.platform.ide.progress.runWithModalProgressBlocking(project, "Finding references") {
                    com.intellij.psi.search.searches.ReferencesSearch.search(typeAlias).findAll().toList()
                }
            } catch (e: Exception) {
                if (e is com.intellij.openapi.progress.ProcessCanceledException || e is java.util.concurrent.CancellationException) {
                    return
                }
                throw e
            }

            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                inlineTypeAlias(typeAlias, valueText, references)
            }
            return
        }

        val annotation = findAnnotation(element)
        if (annotation != null) {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                extractTypeAlias(annotation)
            }
            return
        }
    }

    private fun findTypeAlias(element: PsiElement): PsiElement? {
        // 1. Check if we are on a definition
        val statement = PsiTreeUtil.getParentOfType(element, PyTypeAliasStatement::class.java)
        if (statement != null) return statement

        val assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement::class.java)
        if (assignment != null) {
            val left = assignment.leftHandSideExpression as? PyTargetExpression
            if (left != null && assignment.assignedValue != null) {
                if (PsiTreeUtil.isAncestor(left, element, false) || PsiTreeUtil.isAncestor(
                        assignment.assignedValue,
                        element,
                        false
                    )
                ) {
                    return left
                }
            }
        }

        // 2. Check if we are on a reference to an alias
        val reference = PsiTreeUtil.getParentOfType(element, PyReferenceExpression::class.java)
        if (reference != null) {
            val resolved = reference.reference.resolve()
            if (resolved is PyTargetExpression || resolved is PyTypeAliasStatement) {
                // Double check if the resolved element is actually a type alias
                if (resolved is PyTypeAliasStatement) return resolved
                if (resolved is PyTargetExpression && (resolved.parent as? PyAssignmentStatement)?.assignedValue != null) {
                    return resolved
                }
            }
        }

        return null
    }

    private fun findAnnotation(element: PsiElement): PyAnnotation? {
        return PsiTreeUtil.getParentOfType(element, PyAnnotation::class.java)
    }

    private fun extractTypeAlias(annotation: PyAnnotation) {
        val project = annotation.project
        val generator = PyElementGenerator.getInstance(project)
        val value = annotation.value ?: return
        val aliasName = "MyAlias"
        val languageLevel = LanguageLevel.forElement(annotation)

        val pyFile = annotation.containingFile as PyFile
        val aliasDef = if (languageLevel.isAtLeast(LanguageLevel.PYTHON312)) {
            generator.createFromText(languageLevel, PyTypeAliasStatement::class.java, "type $aliasName = ${value.text}")
        } else {
            generator.createFromText(languageLevel, PyAssignmentStatement::class.java, "$aliasName = ${value.text}")
        }

        ReadonlyStatusHandler.ensureFilesWritable(project, pyFile.virtualFile)

        val importBlock = pyFile.importBlock
        if (importBlock.isNotEmpty()) {
            val lastImport = importBlock.last()
            pyFile.addAfter(aliasDef, lastImport)
            pyFile.addAfter(generator.createNewLine(), lastImport)
        } else {
            pyFile.addBefore(aliasDef, pyFile.statements.firstOrNull() ?: pyFile.firstChild)
            pyFile.addBefore(generator.createNewLine(), pyFile.statements.firstOrNull() ?: pyFile.firstChild)
        }

        val isReturnAnnotation = annotation.parent is PyFunction
        val dummyFunctionText =
            if (isReturnAnnotation) "def foo() -> $aliasName: pass" else "def foo(x: $aliasName): pass"
        val newFunction = generator.createFromText(languageLevel, PyFunction::class.java, dummyFunctionText)
        val newAnnotation = if (isReturnAnnotation) {
            newFunction.annotation!!
        } else {
            PsiTreeUtil.getChildOfType(newFunction.parameterList.parameters[0], PyAnnotation::class.java)!!
        }
        annotation.replace(newAnnotation)
    }


    private fun inlineTypeAlias(
        typeAlias: PsiElement,
        valueText: String,
        references: List<com.intellij.psi.PsiReference>
    ) {
        val project = typeAlias.project
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(typeAlias)

        ReadonlyStatusHandler.ensureFilesWritable(project, typeAlias.containingFile.virtualFile)

        references.forEach { ref ->
            val element = ref.element
            if (element is PyReferenceExpression) {
                val replacement = generator.createExpressionFromText(languageLevel, valueText)
                element.replace(replacement)
            }
        }

        // Remove the alias definition
        val statement = typeAlias as? PyTypeAliasStatement ?: typeAlias.parent
        statement?.delete()
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.DIFF

    override fun startInWriteAction(): Boolean = false
}
