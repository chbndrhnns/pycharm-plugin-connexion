package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.core.PluginConstants
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.github.chbndrhnns.betterpy.core.util.isOwnCode
import com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState
import com.github.chbndrhnns.betterpy.features.intentions.movescope.MoveScopeTextBuilder
import com.github.chbndrhnns.betterpy.features.intentions.movescope.PyMoveFunctionIntoClassProcessor
import com.github.chbndrhnns.betterpy.features.pytest.testtree.PytestTestContextUtils
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class WrapTestInClassIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Wrap test in class"
    override fun getFamilyName(): String = "Wrap test in class"
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!file.isOwnCode()) return false
        if (!PluginSettingsState.instance().state.enableWrapTestInClassIntention) return false
        if (file !is PyFile) return false

        val function = findTestFunctionAtCaret(editor, file) ?: return false

        // Check if it's a module-level test function (not inside a class)
        val parentClass = PsiTreeUtil.getParentOfType(function, PyClass::class.java)
        if (parentClass != null) return false

        // Check if it's a test function
        return PytestTestContextUtils.isTestFunction(function)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as PyFile
        val function = findTestFunctionAtCaret(editor, pyFile) ?: return
        val functionName = function.name ?: return

        var suggestedClassName = generateClassName(functionName)
        val existingTestClasses = findTestClassesInFile(pyFile)
        val validTargetClasses = existingTestClasses.filter { targetClass ->
            MoveScopeTextBuilder.canInsertMethodIntoClass(functionName, targetClass)
        }

        val existingNames = existingTestClasses.mapNotNull { it.name }.toSet()
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
            sourceFunctionName = functionName,
            existingTestClasses = validTargetClasses
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

            when (settings) {
                is WrapTestInClassSettings.CreateNewClass -> {
                    // Build the complete class with method in one go to get proper formatting
                    val classWithMethodText = MoveScopeTextBuilder.buildClassWithMethod(
                        function,
                        settings.className,
                        MoveScopeTextBuilder.MethodStyle.FORCE_INSTANCE
                    )
                    val testClass = elementGenerator.createFromText(
                        LanguageLevel.getLatest(), PyClass::class.java, classWithMethodText
                    )

                    function.replace(testClass)
                }

                is WrapTestInClassSettings.AddToExistingClass -> {
                    PyMoveFunctionIntoClassProcessor(
                        project,
                        function,
                        settings.targetClass,
                        updateCallSites = false,
                        methodStyle = MoveScopeTextBuilder.MethodStyle.FORCE_INSTANCE
                    ).run()
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


    private fun generateClassName(functionName: String): String {
        // Convert test_user_login -> TestUserLogin
        if (PytestNaming.isTestFunctionName(functionName)) {
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
