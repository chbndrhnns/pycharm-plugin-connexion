package com.github.chbndrhnns.intellijplatformplugincopy.pytest.outcome

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestTreeView
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import java.util.regex.Pattern

class UseActualTestOutcomeFromTreeAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val proxies = e.getData(AbstractTestProxy.DATA_KEYS)
        val visible = proxies != null && proxies.size == 1 && (proxies[0].isDefect)
        e.presentation.isEnabledAndVisible = visible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 1. Get the Test Model and Properties (to access scope)
        val model = e.getData(TestTreeView.MODEL_DATA_KEY) ?: return
        val properties = model.properties
        val scope = properties.scope

        // 2. Get the selected SMTestProxy
        val proxies = e.getData(AbstractTestProxy.DATA_KEYS) ?: return
        val proxy = proxies.firstOrNull() as? SMTestProxy ?: return

        val locationUrl = proxy.locationUrl ?: return
        val testKey = PytestTestKeyFactory.fromTestProxy(locationUrl, proxy.metainfo)

        // 3. Get Test Definition Line
        val location = proxy.getLocation(project, scope) ?: return
        // openFileDescriptor gives the 0-based line number of the test definition (e.g., 'def test_foo():')
        val definitionLine = location.openFileDescriptor?.line

        // 4. Get Failure Line
        // We use the file from the definition location to filter the stacktrace
        val virtualFile = location.virtualFile
        var failureLine: Int? = null

        if (proxy.isDefect && proxy.stacktrace != null && virtualFile != null) {
            failureLine = getFailedLineNumber(proxy.stacktrace!!, virtualFile)
        }

        val targetLine = failureLine ?: definitionLine
        if (targetLine != null && virtualFile != null) {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return

            if (targetLine < 0 || targetLine >= document.lineCount) return

            val startOffset = document.getLineStartOffset(targetLine)
            val endOffset = document.getLineEndOffset(targetLine)
            val text = document.getText(TextRange(startOffset, endOffset))
            val firstNonWsIndex = text.indexOfFirst { !it.isWhitespace() }
            val targetOffset = if (firstNonWsIndex != -1) startOffset + firstNonWsIndex else startOffset

            val descriptor = OpenFileDescriptor(project, virtualFile, targetOffset)
            val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true) ?: return

            val useCase = UseActualOutcomeUseCase(TestOutcomeDiffService.getInstance(project))
            WriteCommandAction.runWriteCommandAction(project) {
                useCase.invoke(project, editor, psiFile, testKey)
            }
        }
    }

    companion object {
        fun getFailedLineNumber(stacktrace: String, testFile: VirtualFile): Int? {
            // Python traceback pattern: File "/path/to/file.py", line 123, in ...
            val patternStandard = Pattern.compile("File \"([^\"]+)\", line (\\d+),")
            var matcher = patternStandard.matcher(stacktrace)

            var lastMatchingLine: Int? = null

            while (matcher.find()) {
                val filePath = matcher.group(1)
                val lineNumber = matcher.group(2).toIntOrNull()

                // We typically want the last frame that matches our test file.
                // Comparing paths can be tricky; checking if the stack path ends with the test file name
                // or contains the test file path is a robust heuristic.
                if (filePath != null && lineNumber != null) {
                    // Check if this frame belongs to the test file
                    // You might need a more robust path comparison depending on how paths are reported
                    if (filePath.endsWith(testFile.name) || filePath.contains(testFile.path)) {
                        lastMatchingLine = lineNumber - 1 // Convert 1-based to 0-based
                    }
                }
            }

            if (lastMatchingLine == null) {
                // Pytest failure summary pattern: test_fail.py:9: AssertionError
                // Group 1: File path/name
                // Group 2: Line number
                val patternPytest = Pattern.compile("([^:\\s]+):(\\d+):")
                matcher = patternPytest.matcher(stacktrace)

                while (matcher.find()) {
                    val filePath = matcher.group(1)
                    val lineNumber = matcher.group(2).toIntOrNull()

                    if (filePath != null && lineNumber != null) {
                        if (filePath.endsWith(testFile.name) || filePath.contains(testFile.path)) {
                            lastMatchingLine = lineNumber - 1 // Convert 1-based to 0-based
                        }
                    }
                }
            }

            return lastMatchingLine
        }
    }
}
