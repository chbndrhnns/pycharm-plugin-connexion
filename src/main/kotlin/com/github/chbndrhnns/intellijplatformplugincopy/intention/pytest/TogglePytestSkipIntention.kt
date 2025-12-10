package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import javax.swing.Icon

class TogglePytestSkipIntention : IntentionAction, HighPriorityAction, Iconable {

    override fun getText(): String = "Toggle pytest skip"
    override fun getFamilyName(): String = "Toggle pytest skip"
    override fun getIcon(@Iconable.IconFlags flags: Int): Icon? = null
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableTogglePytestSkipIntention) return false
        if (file !is PyFile) return false

        val element = file.findElementAt(editor.caretModel.offset) ?: return false

        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (pyFunction != null && pyFunction.name?.startsWith("test_") == true) {
            return true
        }

        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null && pyClass.name?.startsWith("Test") == true) {
             return true
        }

        val pyFile = file as PyFile
        // For module level, we are available if it looks like a test file.
        // We only show it if we are not inside a class/function (or we fell through)
        return pyFile.name.startsWith("test_") || pyFile.name.endsWith("_test.py")
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        val pyFile = file as PyFile
        
        val generator = PyElementGenerator.getInstance(project)

        if (pyFunction != null && pyFunction.name?.startsWith("test_") == true) {
            toggleDecorator(pyFunction, generator, pyFile)
            return
        }

        if (pyClass != null && pyClass.name?.startsWith("Test") == true) {
            toggleDecorator(pyClass, generator, pyFile)
            return
        }

        if (pyFile.name.startsWith("test_") || pyFile.name.endsWith("_test.py")) {
             toggleModuleLevelSkip(pyFile, generator)
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.DIFF
    }

    private fun toggleDecorator(element: PyDecoratable, generator: PyElementGenerator, file: PyFile) {
        val decorators = element.decoratorList?.decorators ?: emptyArray()
        val skipDecorator = decorators.firstOrNull { 
            val text = it.text
            text.contains("pytest.mark.skip") // Looser check to handle @pytest.mark.skip()
        }

        if (skipDecorator != null) {
            skipDecorator.delete()
        } else {
            ensurePytestImported(file, generator)
            
            val dummy = generator.createFromText(LanguageLevel.getLatest(), PyFunction::class.java, "@pytest.mark.skip\ndef foo(): pass")
            
            if (element.decoratorList != null) {
                 val newDecorator = dummy.decoratorList!!.decorators[0]
                 element.decoratorList!!.add(newDecorator.copy())
            } else {
                val decoratorList = dummy.decoratorList
                if (decoratorList != null) {
                    element.addBefore(decoratorList.copy(), element.firstChild)
                }
            }
        }
    }

    private fun toggleModuleLevelSkip(file: PyFile, generator: PyElementGenerator) {
        val assignments = file.statements.filterIsInstance<PyAssignmentStatement>()
        val pytestMarkAssignment = assignments.firstOrNull { 
            it.targets.any { target -> target.text == "pytestmark" }
        }

        if (pytestMarkAssignment != null) {
            val value = pytestMarkAssignment.assignedValue
            
            // Extract existing items text
            val items = if (value is PyListLiteralExpression) {
                value.elements.map { it.text }.toMutableList()
            } else if (value != null) {
                mutableListOf(value.text)
            } else {
                mutableListOf()
            }

            val skipMarker = "pytest.mark.skip"
            val hasSkip = items.any { it.contains(skipMarker) }

            if (hasSkip) {
                items.removeIf { it.contains(skipMarker) }
            } else {
                items.add(skipMarker)
            }
            
            ensurePytestImported(file, generator)

            val newListText = "[" + items.joinToString(", ") + "]"
            val newAssignment = generator.createFromText(LanguageLevel.getLatest(), PyAssignmentStatement::class.java, "pytestmark = $newListText")
            pytestMarkAssignment.replace(newAssignment)

        } else {
            ensurePytestImported(file, generator)
            val newAssignment = generator.createFromText(LanguageLevel.getLatest(), PyAssignmentStatement::class.java, "pytestmark = [pytest.mark.skip]")
            
            val imports = file.importBlock
            if (imports.isNotEmpty()) {
                val lastImport = imports.last()
                file.addAfter(newAssignment, lastImport)
                file.addAfter(PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n\n"), lastImport)
            } else {
                 val firstStatement = file.statements.firstOrNull()
                 if (firstStatement != null) {
                     file.addBefore(newAssignment, firstStatement)
                     file.addBefore(PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n\n"), firstStatement)
                 } else {
                     file.add(newAssignment)
                 }
            }
        }
    }

    private fun ensurePytestImported(file: PyFile, generator: PyElementGenerator) {
         val imports = file.importBlock
         val isImported = imports.any { importStmt ->
            if (importStmt is PyImportStatement) {
                importStmt.importElements.any { it.visibleName == "pytest" }
            } else {
                false 
            }
        }
        
        if (!isImported) {
             val newImport = generator.createImportStatement(LanguageLevel.getLatest(), "pytest", null)
             val anchor = imports.firstOrNull() ?: file.statements.firstOrNull()
             if (anchor != null) {
                 file.addBefore(newImport, anchor)
                 file.addBefore(PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n\n"), anchor)
             } else {
                 file.add(newImport)
             }
        }
    }
}
