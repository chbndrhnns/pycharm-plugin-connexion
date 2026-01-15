# MCP Server Context Passing Guide

This document explains how to pass and access context (project, editor, caret position) in MCP tools and how to implement navigation.

## Overview

The MCP (Model Context Protocol) server in IntelliJ Platform provides tools that can interact with the IDE. Context is passed through:
1. **Automatic context** via `CoroutineContext`
2. **Explicit parameters** in tool methods

## 1. Project Context

### Accessing the Project

The project is the primary context automatically available to MCP tools:

```kotlin
import com.intellij.mcpserver.project
import kotlinx.coroutines.currentCoroutineContext

suspend fun my_tool() {
    val project = currentCoroutineContext().project
    // Use project...
}
```

### Project Context Details

- **Extension**: `CoroutineContext.project` (throws if no project)
- **Extension**: `CoroutineContext.projectOrNull` (returns null if no project)
- **Source**: Defined in `McpCallInfo.kt`

The project can be specified by:
- Environment variables
- HTTP headers
- Implicit tool parameter `projectPath`
- If only one project is open, it's used automatically

### Example from RefactoringToolset

```kotlin
suspend fun rename_refactoring(
    pathInProject: String,
    symbolName: String,
    newName: String,
): String {
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)
    // ... rest of implementation
}
```

## 2. Editor Context

### No Automatic Editor Context

**Important**: MCP tools do NOT receive automatic editor or caret context. The current editor state is not passed automatically.

### Accessing Open Editors

To work with editors, use `FileEditorManagerEx`:

```kotlin
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx

suspend fun get_open_editors() {
    val project = currentCoroutineContext().project
    val fileEditorManager = FileEditorManagerEx.getInstanceExAsync(project)
    
    // Get all open files
    val openFiles = fileEditorManager.openFiles
    
    // Get active editor
    val activeEditor = fileEditorManager.selectedEditor
    val activeFile = activeEditor?.file
}
```

### Example from FileToolset

```kotlin
@McpTool
suspend fun get_all_open_file_paths(): OpenFilesInfo {
    val project = currentCoroutineContext().project
    val projectDir = project.projectDirectory
    
    val fileEditorManager = FileEditorManagerEx.getInstanceExAsync(project)
    val openFiles = fileEditorManager.openFiles
    val filePaths = openFiles.mapNotNull { projectDir.relativizeIfPossible(it) }
    val activeFilePath = fileEditorManager.selectedEditor?.file?.toNioPathOrNull()
        ?.let { projectDir.relativize(it).pathString }
    
    return OpenFilesInfo(activeFilePath = activeFilePath, openFiles = filePaths)
}
```

## 3. Caret Position / Offset

### Design Pattern: Explicit Parameters

Caret position is **never** passed automatically. Tools that need position information must accept explicit parameters:

- `line: Int` (1-based line number)
- `column: Int` (1-based column number)

### Converting Line/Column to Offset

```kotlin
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil

suspend fun work_with_position(
    filePath: String,
    line: Int,
    column: Int
) {
    val project = currentCoroutineContext().project
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $filePath")
    
    readAction {
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: mcpFail("Cannot read file: $filePath")
        
        // Validate line (0-based internally)
        if (!DocumentUtil.isValidLine(line - 1, document)) {
            mcpFail("Line number is out of bounds")
        }
        
        // Convert to offset
        val lineStartOffset = document.getLineStartOffset(line - 1)
        val offset = lineStartOffset + column - 1
        
        // Validate offset
        if (!DocumentUtil.isValidOffset(offset, document)) {
            mcpFail("Line and column $line:$column (offset=$offset) is out of bounds")
        }
        
        // Use offset to find PSI elements
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        val psiReference = psiFile?.findReferenceAt(offset)
        val element = psiReference?.resolve()
    }
}
```

### Example from CodeInsightToolset

```kotlin
@McpTool
suspend fun get_symbol_info(
    @McpDescription("Relative path to file from project root")
    filePath: String,
    @McpDescription("1-based line number")
    line: Int,
    @McpDescription("1-based column number")
    column: Int,
): SymbolInfoResult {
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(filePath)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $filePath")
    
    val (documentationTargets, symbolInfo) = readAction {
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: mcpFail("Cannot read file: $filePath")
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?: mcpFail("Cannot get symbol information for file '$filePath'")
        
        if (!DocumentUtil.isValidLine(line - 1, document)) {
            mcpFail("Line number is out of bounds of the document")
        }
        
        val lineStartOffset = document.getLineStartOffset(line - 1)
        val offset = lineStartOffset + column - 1
        
        if (!DocumentUtil.isValidOffset(offset, document)) {
            mcpFail("Line and column $line:$column(offset=$offset) is out of bounds")
        }
        
        val psiReference = psiFile.findReferenceAt(offset)
        val resolvedReference = psiReference?.resolve()
        
        documentationTargets(psiFile, offset).map { it.createPointer() } to 
            resolvedReference?.let { getElementSymbolInfo(it, extraLines = 1) }
    }
    
    // ... rest of implementation
}
```

## 4. Navigation

### Opening Files in Editor

Use `FileEditorManagerEx.openFile()` to open files:

```kotlin
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun open_file(filePath: String) {
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(filePath)
    
    val file = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $filePath")
    
    // Must be called on EDT (Event Dispatch Thread)
    withContext(Dispatchers.EDT) {
        FileEditorManagerEx.getInstanceExAsync(project)
            .openFile(file, options = FileEditorOpenOptions(requestFocus = true))
    }
}
```

### Example from FileToolset

```kotlin
@McpTool
@McpDescription("""
    Opens the specified file in the JetBrains IDE editor.
    The file path can be absolute or relative to the project root.
""")
suspend fun open_file_in_editor(
    @McpDescription("Relative path to file from project root")
    filePath: String,
) {
    currentCoroutineContext().reportToolActivity(
        McpServerBundle.message("tool.activity.opening.file", filePath)
    )
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(filePath)
    
    val file = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
    
    if (file == null || !file.exists() || !file.isFile) {
        mcpFail("File $filePath doesn't exist or can't be opened")
    }
    
    withContext(Dispatchers.EDT) {
        FileEditorManagerEx.getInstanceExAsync(project)
            .openFile(file, options = FileEditorOpenOptions(requestFocus = true))
    }
}
```

### Navigation to Specific Position

While not directly exposed in current MCP toolsets, you can navigate to a specific position using `OpenFileDescriptor`:

```kotlin
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun navigate_to_position(
    filePath: String,
    line: Int,
    column: Int
) {
    val project = currentCoroutineContext().project
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $filePath")
    
    withContext(Dispatchers.EDT) {
        // OpenFileDescriptor uses 0-based line and column
        val descriptor = OpenFileDescriptor(project, virtualFile, line - 1, column - 1)
        descriptor.navigate(true) // true = request focus
    }
}
```

## 5. Available Context from CoroutineContext

From `McpCallInfo.kt`, the following context is available:

```kotlin
// Project context
val project: Project = currentCoroutineContext().project
val projectOrNull: Project? = currentCoroutineContext().projectOrNull

// Client information
val clientInfo: ClientInfo = currentCoroutineContext().clientInfo
// Contains: name, version

// Tool descriptor
val toolDescriptor: McpToolDescriptor = currentCoroutineContext().currentToolDescriptor

// Full call info
val callInfo: McpCallInfo = currentCoroutineContext().mcpCallInfo
// Contains: callId, clientInfo, project, mcpToolDescriptor, rawArguments, meta, 
//           mcpSessionOptions, headers
```

## 6. Common Patterns

### Pattern 1: File-based Tool

```kotlin
@McpTool
suspend fun my_file_tool(
    @McpDescription("Relative path to file from project root")
    pathInProject: String
): String {
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)
    
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $pathInProject")
    
    return readAction {
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: mcpFail("Cannot read file: $pathInProject")
        
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?: mcpFail("Cannot get PSI file: $pathInProject")
        
        // Work with psiFile...
        "Success"
    }
}
```

### Pattern 2: Position-based Tool

```kotlin
@McpTool
suspend fun my_position_tool(
    @McpDescription("Relative path to file from project root")
    pathInProject: String,
    @McpDescription("1-based line number")
    line: Int,
    @McpDescription("1-based column number")
    column: Int
): String {
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)
    
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $pathInProject")
    
    return readAction {
        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: mcpFail("Cannot read file: $pathInProject")
        
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?: mcpFail("Cannot get PSI file: $pathInProject")
        
        // Validate and convert to offset
        if (!DocumentUtil.isValidLine(line - 1, document)) {
            mcpFail("Line number is out of bounds")
        }
        
        val lineStartOffset = document.getLineStartOffset(line - 1)
        val offset = lineStartOffset + column - 1
        
        if (!DocumentUtil.isValidOffset(offset, document)) {
            mcpFail("Position $line:$column is out of bounds")
        }
        
        // Work with offset...
        val element = psiFile.findElementAt(offset)
        "Success"
    }
}
```

### Pattern 3: Editor Manipulation Tool

```kotlin
@McpTool
suspend fun my_editor_tool(
    @McpDescription("Relative path to file from project root")
    pathInProject: String
): String {
    val project = currentCoroutineContext().project
    val resolvedPath = project.resolveInProject(pathInProject)
    
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(resolvedPath)
        ?: mcpFail("File not found: $pathInProject")
    
    // Open file in editor
    withContext(Dispatchers.EDT) {
        FileEditorManagerEx.getInstanceExAsync(project)
            .openFile(virtualFile, options = FileEditorOpenOptions(requestFocus = true))
    }
    
    return "File opened successfully"
}
```

## 7. Key Takeaways

1. **Project context** is automatically available via `currentCoroutineContext().project`
2. **Editor context** is NOT automatic - use `FileEditorManagerEx` to access editors
3. **Caret position** is NOT automatic - accept `line` and `column` as explicit parameters
4. **Navigation** is done via `FileEditorManagerEx.openFile()` or `OpenFileDescriptor.navigate()`
5. **File paths** should be relative to project root and passed as parameters
6. **Position parameters** should be 1-based (line and column) for user-friendliness
7. **Offset calculations** are 0-based internally (convert from 1-based parameters)
8. **EDT thread** is required for UI operations like opening files
9. **Read actions** are required for PSI operations
10. **Write actions** are required for modifying files

## 8. References

### Key Files in intellij-community/plugins/mcp-server

- `McpCallInfo.kt` - Context definitions and extensions
- `McpTool.kt` - Tool interface
- `McpToolset.kt` - Toolset interface
- `toolsets/general/FileToolset.kt` - File operations and editor access
- `toolsets/general/CodeInsightToolset.kt` - Position-based operations
- `toolsets/general/TextToolset.kt` - Text manipulation
- `toolsets/general/RefactoringToolset.kt` - Refactoring operations

### IntelliJ Platform APIs Used

- `com.intellij.openapi.project.Project` - Project context
- `com.intellij.openapi.fileEditor.ex.FileEditorManagerEx` - Editor management
- `com.intellij.openapi.fileEditor.FileDocumentManager` - Document access
- `com.intellij.psi.PsiDocumentManager` - PSI/Document bridge
- `com.intellij.openapi.vfs.LocalFileSystem` - Virtual file system
- `com.intellij.util.DocumentUtil` - Document utilities
- `com.intellij.openapi.fileEditor.OpenFileDescriptor` - Navigation
