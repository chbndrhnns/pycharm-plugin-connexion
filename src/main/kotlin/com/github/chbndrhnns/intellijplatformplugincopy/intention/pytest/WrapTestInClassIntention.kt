package com.github.chbndrhnns.intellijplatformplugincopy.intention.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class WrapTestInClassIntention : IntentionAction {

    override fun getText(): String = PluginConstants.ACTION_PREFIX + "Wrap test in class"
    override fun getFamilyName(): String = "Wrap test in class"
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!PluginSettingsState.instance().state.enableWrapTestInClassIntention) return false
        if (file !is PyFile) return false

        val function = findTestFunctionAtCaret(editor, file) ?: return false

        // Check if it's a module-level test function (not inside a class)
        val parentClass = PsiTreeUtil.getParentOfType(function, PyClass::class.java)
        if (parentClass != null) return false

        // Check if it's a test function
        return isTestFunction(function)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val pyFile = file as PyFile
        val function = findTestFunctionAtCaret(editor, pyFile) ?: return

        val elementGenerator = PyElementGenerator.getInstance(project)

        // Generate class name from function name
        val className = generateClassName(function.name ?: "TestClass")

        // Build the complete class with method in one go to get proper formatting
        val classWithMethodText = buildClassWithMethod(function, className)
        val testClass = elementGenerator.createFromText(
            LanguageLevel.getLatest(), PyClass::class.java, classWithMethodText
        )

        // Replace function with class in the file
        function.replace(testClass)
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

    private fun buildClassWithMethod(function: PyFunction, className: String): String {
        // Get the function signature
        val parameterList = function.parameterList
        val parameters = parameterList.parameters

        // Build new parameter list with self
        val newParams = if (parameters.isEmpty()) {
            "self"
        } else {
            "self, " + parameters.joinToString(", ") { it.text }
        }

        // Get function name
        val functionName = function.name ?: "test"
        
        // Get return type annotation if present (annotation.value is the type expression without ->)
        val returnAnnotation = function.annotation
        val returnTypeText = if (returnAnnotation != null) {
            val typeExpr = returnAnnotation.value
            if (typeExpr != null) " -> ${typeExpr.text}" else ""
        } else ""

        // Get decorators
        val decoratorList = function.decoratorList
        val decoratorsText = if (decoratorList != null) {
            decoratorList.decorators.joinToString("\n    ", prefix = "    ") { it.text } + "\n"
        } else {
            ""
        }

        // Get function body - need to re-indent each line
        val statementList = function.statementList
        val bodyLines = statementList.text.lines()
        val reindentedBody = bodyLines.joinToString("\n") { line ->
            if (line.isNotEmpty()) "        $line" else line
        }

        // Build the complete class text
        return "class $className:\n${decoratorsText}    def $functionName($newParams)$returnTypeText:\n$reindentedBody"
    }
}
