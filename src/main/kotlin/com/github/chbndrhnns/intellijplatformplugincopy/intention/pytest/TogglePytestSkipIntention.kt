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
import com.intellij.util.ArrayUtilRt
import com.jetbrains.python.codeInsight.imports.AddImportHelper
import com.jetbrains.python.psi.*
import javax.swing.Icon

class TogglePytestSkipIntention : IntentionAction, HighPriorityAction, Iconable {

    private enum class Scope(val label: String) {
        FUNCTION("function"),
        CLASS("class"),
        MODULE("module"),
    }

    @Volatile
    private var cachedText: String = "Toggle pytest skip"

    override fun getText(): String = cachedText
    override fun getFamilyName(): String = "Toggle pytest skip"
    override fun getIcon(@Iconable.IconFlags flags: Int): Icon? = null
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableTogglePytestSkipIntention) return false
        if (file !is PyFile) return false

        val scope = determineScope(editor, file) ?: return false
        cachedText = "Toggle pytest skip (${scope.label})"
        return true
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        val pyFile = file as PyFile

        val generator = PyElementGenerator.getInstance(project)

        if (pyFunction != null && pyFunction.name?.startsWith("test_") == true) {
            toggleDecorator(pyFunction, file)
            return
        }

        if (pyClass != null && pyClass.name?.startsWith("Test") == true) {
            toggleDecorator(pyClass, file)
            return
        }

        if (pyFile.name.startsWith("test_") || pyFile.name.endsWith("_test.py")) {
            toggleModuleLevelSkip(pyFile, generator)
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.DIFF
    }

    private fun determineScope(editor: Editor, file: PyFile): Scope? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null

        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (pyFunction != null) {
            return if (pyFunction.name?.startsWith("test_") == true) Scope.FUNCTION else null
        }

        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null) {
            if (pyClass.name?.startsWith("Test") != true) return null
            val nameIdentifier = pyClass.nameIdentifier ?: return null
            return if (nameIdentifier.textRange.containsOffset(offset)) Scope.CLASS else null
        }

        return if (file.name.startsWith("test_") || file.name.endsWith("_test.py")) Scope.MODULE else null
    }

    private fun toggleDecorator(element: PyDecoratable, file: PyFile) {
        val skipMarker = "pytest.mark.skip"

        // Check for existing decorator using qualified name or text fallback
        val existingDecorator = element.decoratorList?.decorators?.firstOrNull {
            it.qualifiedName.toString() == skipMarker || it.text.contains(skipMarker)
        }

        if (existingDecorator != null) {
            existingDecorator.delete()
        } else {
            AddImportHelper.addImportStatement(file, "pytest", null, AddImportHelper.ImportPriority.THIRD_PARTY, null)

            if (element is PyFunction) {
                // Use existing helper for functions
                PyUtil.addDecorator(element, "@$skipMarker")
            } else {
                // Manual handling for PyClass (similar logic to PyUtil.addDecorator)
                addDecoratorToDecoratable(element, "@$skipMarker")
            }
        }
    }

    /**
     * Helper to add a decorator to any PyDecoratable (PyClass or PyFunction).
     * Replicates logic from [PyUtil.addDecorator] but for the general interface.
     */
    private fun addDecoratorToDecoratable(element: PyDecoratable, decoratorText: String) {
        val currentDecoratorList = element.decoratorList
        val decoTexts = ArrayList<String>()
        decoTexts.add(decoratorText)

        if (currentDecoratorList != null) {
            for (deco in currentDecoratorList.decorators) {
                decoTexts.add(deco.text)
            }
        }

        val generator = PyElementGenerator.getInstance(element.project)
        val newDecoratorList = generator.createDecoratorList(*ArrayUtilRt.toStringArray(decoTexts))

        if (currentDecoratorList != null) {
            currentDecoratorList.replace(newDecoratorList)
        } else {
            element.addBefore(newDecoratorList, element.firstChild)
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

            AddImportHelper.addImportStatement(file, "pytest", null, AddImportHelper.ImportPriority.THIRD_PARTY, null)

            val newListText = "[" + items.joinToString(", ") + "]"
            val newAssignment = generator.createFromText(LanguageLevel.getLatest(), PyAssignmentStatement::class.java, "pytestmark = $newListText")
            pytestMarkAssignment.replace(newAssignment)

        } else {
            AddImportHelper.addImportStatement(file, "pytest", null, AddImportHelper.ImportPriority.THIRD_PARTY, null)

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
                    file.addBefore(
                        PsiParserFacade.getInstance(file.project).createWhiteSpaceFromText("\n\n"),
                        firstStatement
                    )
                } else {
                    file.add(newAssignment)
                }
            }
        }
    }
}