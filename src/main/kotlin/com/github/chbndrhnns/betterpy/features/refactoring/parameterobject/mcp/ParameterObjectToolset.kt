package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.mcp

import com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.*
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.nio.file.Path

class ParameterObjectToolset : McpToolset {

    /**
     * Finds a Python function by name in the given PSI file.
     */
    private fun findFunctionByName(psiFile: PsiFile, functionName: String): PyFunction? {
        return PsiTreeUtil.findChildrenOfType(psiFile, PyFunction::class.java)
            .firstOrNull { it.name == functionName }
    }

    /**
     * Finds a Python function at the given line and column position.
     * Uses 1-based line and column numbers (user-friendly).
     */
    private fun findFunctionAtPosition(
        psiFile: PsiFile,
        document: Document,
        line: Int,
        column: Int
    ): PyFunction? {
        // Validate line number (convert to 0-based)
        if (!DocumentUtil.isValidLine(line - 1, document)) {
            mcpFail("Invalid line number: $line")
        }

        // Calculate offset from line and column (convert to 0-based)
        val lineStartOffset = document.getLineStartOffset(line - 1)
        val offset = lineStartOffset + column - 1

        // Validate offset
        if (!DocumentUtil.isValidOffset(offset, document)) {
            mcpFail("Invalid column number: $column at line: $line")
        }

        // Find element at offset and get containing function
        val element = psiFile.findElementAt(offset)
        return PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
    }

    /**
     * Opens a file in the editor and optionally navigates to a specific line.
     */
    private suspend fun openFileInEditor(virtualFile: VirtualFile, line: Int? = null) {
        withContext(Dispatchers.EDT) {
            val project = currentCoroutineContext().project
            if (line != null) {
                // Navigate to specific line (convert to 0-based)
                OpenFileDescriptor(project, virtualFile, line - 1, 0).navigate(true)
            } else {
                // Just open the file
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }
    }

    @McpTool
    @McpDescription(
        """
        Introduces a parameter object refactoring for a Python function.
        Groups multiple function parameters into a dataclass or TypedDict.
        
        This refactoring helps reduce parameter lists by creating a cohesive parameter object.
        Use this tool when a function has many related parameters that should be grouped together.
        
        Note: Either functionName or line/column must be provided to locate the function.
    """
    )
    suspend fun introduce_parameter_object(
        @McpDescription("Relative path to Python file from project root")
        pathInProject: String,
        @McpDescription("Name of the function to refactor (optional if line/column provided)")
        functionName: String? = null,
        @McpDescription("Name for the new parameter object class")
        className: String,
        @McpDescription("Name for the new parameter in function signature")
        parameterName: String,
        @McpDescription("Comma-separated list of parameter names to group")
        selectedParameters: String,
        @McpDescription("Type of parameter object: 'dataclass' or 'typeddict'")
        baseType: String = "dataclass",
        @McpDescription("1-based line number where the function is located (optional)")
        line: Int? = null,
        @McpDescription("1-based column number within the line (optional)")
        column: Int? = null,
        @McpDescription("Whether to open the file in editor after refactoring")
        openInEditor: Boolean = false
    ): String {
        val project = currentCoroutineContext().project
        val projectPath = Path.of(project.basePath ?: mcpFail("Project path not found"))
        val filePath = projectPath.resolve(pathInProject.removePrefix("/"))

        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(filePath)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
            ?: mcpFail("File not found: $pathInProject")

        val document = readAction {
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
                ?: mcpFail("Cannot read file: $pathInProject")
        }

        val psiFile = readAction {
            PsiDocumentManager.getInstance(project).getPsiFile(document)
        } ?: mcpFail("Cannot get PSI file: $pathInProject")

        // Find function by position or name
        val function = readAction {
            when {
                line != null && column != null -> {
                    // Use position-based finding
                    findFunctionAtPosition(psiFile, document, line, column)
                        ?: mcpFail("No function found at line $line, column $column in $pathInProject")
                }

                functionName != null -> {
                    // Use name-based finding
                    findFunctionByName(psiFile, functionName)
                        ?: mcpFail("Function '$functionName' not found in $pathInProject")
                }

                else -> {
                    mcpFail("Either functionName or line/column must be provided")
                }
            }
        }

        val paramNames = selectedParameters.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (paramNames.isEmpty()) {
            mcpFail("No parameters specified for grouping")
        }

        val allParams = readAction {
            val parameters = function.parameterList.parameters
                .filterIsInstance<PyNamedParameter>()
                .filter { !it.isSelf && !it.isPositionalContainer && !it.isKeywordContainer }

            val isClassMethod = function.decoratorList?.findDecorator("classmethod") != null
            if (isClassMethod && parameters.isNotEmpty() && parameters.first().name == "cls") {
                parameters.drop(1)
            } else {
                parameters
            }
        }
        val selectedParams = allParams.filter { it.name in paramNames }

        if (selectedParams.size != paramNames.size) {
            val found = selectedParams.mapNotNull { it.name }
            val missing = paramNames.filter { it !in found }
            mcpFail("Some parameters not found in function: ${missing.joinToString(", ")}")
        }

        val baseTypeEnum = when (baseType.lowercase()) {
            "dataclass" -> ParameterObjectBaseType.DATACLASS
            "typeddict" -> ParameterObjectBaseType.TYPED_DICT
            else -> mcpFail("Invalid baseType: $baseType. Must be 'dataclass' or 'typeddict'")
        }

        val settings = IntroduceParameterObjectSettings(
            selectedParameters = selectedParams,
            className = className,
            parameterName = parameterName,
            baseType = baseTypeEnum
        )

        writeAction {
            PyIntroduceParameterObjectProcessor(
                function = function,
                configSelector = { _, _ -> settings }
            ).run()
        }

        // Open file in editor if requested
        if (openInEditor) {
            val functionLine = readAction {
                val offset = function.textOffset
                document.getLineNumber(offset) + 1 // Convert to 1-based
            }
            openFileInEditor(virtualFile, functionLine)
        }

        val actualFunctionName = readAction { function.name } ?: "unknown"
        return "Successfully introduced parameter object '$className' in function '$actualFunctionName'. " +
                "Grouped ${selectedParams.size} parameters: ${paramNames.joinToString(", ")}"
    }

    @McpTool
    @McpDescription(
        """
        Inlines a parameter object back into individual parameters.
        Reverses the introduce parameter object refactoring.
        
        This refactoring expands a parameter object back into separate function parameters.
        Use this tool when you want to undo a parameter object refactoring or simplify a function signature.
        
        Note: Either functionName or line/column must be provided to locate the function.
    """
    )
    suspend fun inline_parameter_object(
        @McpDescription("Relative path to Python file from project root")
        pathInProject: String,
        @McpDescription("Name of the function containing parameter object (optional if line/column provided)")
        functionName: String? = null,
        @McpDescription("Whether to inline in all functions using this parameter object")
        inlineAllOccurrences: Boolean = true,
        @McpDescription("Whether to remove the parameter object class definition")
        removeClass: Boolean = true,
        @McpDescription("1-based line number where the function is located (optional)")
        line: Int? = null,
        @McpDescription("1-based column number within the line (optional)")
        column: Int? = null,
        @McpDescription("Whether to open the file in editor after refactoring")
        openInEditor: Boolean = false
    ): String {
        val project = currentCoroutineContext().project
        val projectPath = Path.of(project.basePath ?: mcpFail("Project path not found"))
        val filePath = projectPath.resolve(pathInProject.removePrefix("/"))

        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(filePath)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
            ?: mcpFail("File not found: $pathInProject")

        val document = readAction {
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
                ?: mcpFail("Cannot read file: $pathInProject")
        }

        val psiFile = readAction {
            PsiDocumentManager.getInstance(project).getPsiFile(document)
        } ?: mcpFail("Cannot get PSI file: $pathInProject")

        // Find function by position or name
        val function = readAction {
            when {
                line != null && column != null -> {
                    // Use position-based finding
                    findFunctionAtPosition(psiFile, document, line, column)
                        ?: mcpFail("No function found at line $line, column $column in $pathInProject")
                }

                functionName != null -> {
                    // Use name-based finding
                    findFunctionByName(psiFile, functionName)
                        ?: mcpFail("Function '$functionName' not found in $pathInProject")
                }

                else -> {
                    mcpFail("Either functionName or line/column must be provided")
                }
            }
        }

        val settings = InlineParameterObjectSettings(
            inlineAllOccurrences = inlineAllOccurrences,
            removeClass = removeClass
        )

        val usageCount = readAction {
            PyInlineParameterObjectProcessor(function, function).countUsages()
        }

        // Don't wrap in writeAction - the processor handles write actions internally
        // and uses runWithModalProgressBlocking which cannot be called from within a write action
        PyInlineParameterObjectProcessor(function, function).run(settings)

        // Open file in editor if requested
        if (openInEditor) {
            val functionLine = readAction {
                val offset = function.textOffset
                document.getLineNumber(offset) + 1 // Convert to 1-based
            }
            openFileInEditor(virtualFile, functionLine)
        }

        val actualFunctionName = readAction { function.name } ?: "unknown"
        return "Successfully inlined parameter object in function '$actualFunctionName'. " +
                "Affected $usageCount function(s). " +
                "Class ${if (removeClass) "removed" else "kept"}."
    }
}
