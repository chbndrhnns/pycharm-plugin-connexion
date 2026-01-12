package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class MoveTestToClassIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Move test to another class"
    override fun getFamilyName(): String = "Move test to another class"
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        // We reuse the existing setting for now
        if (!PluginSettingsState.instance().state.enableWrapTestInClassIntention) return false
        if (file !is PyFile) return false

        val function = findTestFunctionAtCaret(editor, file) ?: return false

        // Ensure caret is on the function signature (not in the body)
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset)
        if (element != null && PsiTreeUtil.isAncestor(function.statementList, element, false)) {
            return false
        }

        // Check if it is inside a class
        val parentClass = PsiTreeUtil.getParentOfType(function, PyClass::class.java)
        return parentClass != null && isTestFunction(function)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as PyFile
        val function = findTestFunctionAtCaret(editor, pyFile) ?: return
        val currentClass = PsiTreeUtil.getParentOfType(function, PyClass::class.java) ?: return

        var suggestedClassName = generateClassName(function.name ?: "TestClass")
        val allTestClasses = findTestClassesInFile(pyFile)
        val candidateClasses = allTestClasses.filter { it != currentClass }
        val validTargetClasses = candidateClasses.filter { targetClass ->
            targetClass.findMethodByName(function.name, true, null) == null
        }

        val existingNames = allTestClasses.mapNotNull { it.name }.toSet()
        if (suggestedClassName in existingNames) {
            var index = 1
            while ("${suggestedClassName}${index}" in existingNames) {
                index++
            }
            suggestedClassName = "${suggestedClassName}${index}"
        }

        val dialog = WrapTestInClassDialog(
            project,
            suggestedClassName,
            "Move Test to Another Class",
            validTargetClasses,
            sourceFunctionName = function.name
        )
        val dialogResult = if (dialog.isModal) {
            dialog.showAndGet()
        } else {
            // In non-modal context (tests), show() and check exit code
            dialog.show()
            dialog.exitCode == com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
        }
        if (!dialogResult) {
            return // User cancelled
        }
        val settings = dialog.getSettings()

        WriteCommandAction.runWriteCommandAction(project, text, null, {
            val elementGenerator = PyElementGenerator.getInstance(project)
            val parserFacade = com.intellij.psi.PsiParserFacade.getInstance(project)

            when (settings) {
                is WrapTestInClassSettings.CreateNewClass -> {
                    // Create new class with the method
                    val functionText = function.text
                    // Indent the function text
                    val indentedFunction = functionText.lines().joinToString("\n") {
                        if (it.isBlank()) it else "    $it"
                    }
                    val classText = "class ${settings.className}:\n$indentedFunction"

                    val newClass = elementGenerator.createFromText(
                        LanguageLevel.getLatest(), PyClass::class.java, classText
                    )

                    // Insert new class after the current class
                    // If currentClass is top-level, parent is file.
                    val parent = currentClass.parent
                    val addedClass = parent.addAfter(newClass, currentClass)
                    // Add spacing
                    parent.addBefore(parserFacade.createWhiteSpaceFromText("\n\n"), addedClass)

                    function.delete()
                    if (currentClass.statementList.statements.isEmpty()) {
                        currentClass.statementList.add(elementGenerator.createPassStatement())
                    }
                }

                is WrapTestInClassSettings.AddToExistingClass -> {
                    val targetClass = settings.targetClass
                    val statementList = targetClass.statementList
                    statementList.add(function)

                    function.delete()
                    if (currentClass.statementList.statements.isEmpty()) {
                        currentClass.statementList.add(elementGenerator.createPassStatement())
                    }
                }
            }
        }, file)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.DIFF
    }

    private fun findTestFunctionAtCaret(editor: Editor, file: PyFile): PyFunction? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
    }

    private fun isTestFunction(function: PyFunction): Boolean {
        val name = function.name ?: return false
        return name.startsWith("test_")
    }

    private fun generateClassName(functionName: String): String {
        // Convert test_user_login -> TestUserLogin
        if (functionName.startsWith("test_")) {
            val baseName = functionName.substring(5) // Remove "test_"
            val words = baseName.split("_")
            val camelCase = words.joinToString("") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
            return "Test$camelCase"
        }
        return "TestClass"
    }
}
