package com.github.chbndrhnns.betterpy.features.refactoring.move

import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.github.chbndrhnns.betterpy.features.intentions.movescope.MoveScopeTextBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

class PyMoveDeclarationsProcessor(
    private val project: Project,
    private val elements: List<PsiElement>,
    private val targetModulePath: String,
    private val searchReferences: Boolean = true
) {

    fun run() {
        WriteCommandAction.runWriteCommandAction(project, "Move Declarations", null, Runnable { performMove() })
    }

    private fun performMove() {
        val pyElements = elements
        if (pyElements.isEmpty()) return

        val sourceFiles = pyElements.mapNotNull { it.containingFile as? PyFile }.distinct()
        if (sourceFiles.size != 1) return

        val sourceFile = sourceFiles.first()
        val oldModulePath = findModulePath(sourceFile) ?: return
        val newModulePath = normalizeModulePath(targetModulePath)

        val targetFile = resolveTargetFile(sourceFile, newModulePath) ?: return

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(targetFile)

        val elementTexts = pyElements.mapNotNull { element ->
            val rendered = renderElementText(element)
            if (rendered.isBlank()) return@mapNotNull null
            when (element) {
                is PyFunction -> generator.createFromText(languageLevel, PyFunction::class.java, rendered)
                is PyClass -> generator.createFromText(languageLevel, PyClass::class.java, rendered)
                else -> null
            }
        }

        val usageMap = if (searchReferences) {
            val scope = GlobalSearchScope.projectScope(project)
            pyElements.associateWith { element ->
                ReferencesSearch.search(element, scope).findAll().toList()
            }
        } else {
            emptyMap()
        }

        elementTexts.forEach { created ->
            insertElementIntoTarget(targetFile, created)
        }

        pyElements.forEach { element ->
            removeElementWithWhitespace(element)
        }

        if (searchReferences) {
            updateReferences(oldModulePath, newModulePath, usageMap)
            addImportsForSourceUsages(sourceFile, targetFile, newModulePath, usageMap)
        }

        ensureTrailingNewline(sourceFile)
        ensureTrailingNewline(targetFile)

        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun updateReferences(
        oldModulePath: String,
        newModulePath: String,
        usageMap: Map<PsiElement, List<PsiReference>>
    ) {
        if (usageMap.isEmpty()) return

        val updatedFromImports = mutableSetOf<PyFromImportStatement>()
        val updatedImportStatements = mutableSetOf<PyImportStatement>()

        for ((element, refs) in usageMap) {
            val name = (element as? PyFunction)?.name ?: (element as? PyClass)?.name ?: continue

            for (ref in refs) {
                val refElement = ref.element
                val fromImport = PsiTreeUtil.getParentOfType(refElement, PyFromImportStatement::class.java, false)
                if (fromImport != null) {
                    if (updatedFromImports.add(fromImport)) {
                        updateFromImportStatement(fromImport, name, oldModulePath, newModulePath)
                    }
                    continue
                }

                val importStatement = PsiTreeUtil.getParentOfType(refElement, PyImportStatement::class.java, false)
                if (importStatement != null) {
                    if (updatedImportStatements.add(importStatement)) {
                        updateImportStatement(importStatement, oldModulePath, newModulePath)
                    }
                    continue
                }

                val referenceExpression = refElement as? PyReferenceExpression ?: continue
                updateQualifiedReference(referenceExpression, oldModulePath, newModulePath, name)
            }
        }
    }

    private fun addImportsForSourceUsages(
        sourceFile: PyFile,
        targetFile: PyFile,
        newModulePath: String,
        usageMap: Map<PsiElement, List<PsiReference>>
    ) {
        if (sourceFile == targetFile) return
        val importService = PyImportService()

        for ((element, refs) in usageMap) {
            val name = (element as? PyFunction)?.name ?: (element as? PyClass)?.name ?: continue
            val hasUnqualifiedSourceUsage = refs.any { ref ->
                val refElement = ref.element
                if (refElement.containingFile != sourceFile) return@any false
                if (PsiTreeUtil.isAncestor(element, refElement, true)) return@any false
                val referenceExpression = refElement as? PyReferenceExpression ?: return@any false
                referenceExpression.qualifier == null
            }

            if (hasUnqualifiedSourceUsage) {
                importService.ensureFromImport(sourceFile, newModulePath, name)
            }
        }
    }

    private fun updateFromImportStatement(
        statement: PyFromImportStatement,
        movedName: String,
        oldModulePath: String,
        newModulePath: String
    ) {
        val sourceQName = statement.importSourceQName?.toString() ?: return
        if (sourceQName != oldModulePath) return

        val moved =
            statement.importElements.filter { it.visibleName == movedName || it.importedQName?.lastComponent == movedName }
        if (moved.isEmpty()) return

        val remaining = statement.importElements.filterNot { it in moved }

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(statement)

        if (remaining.isEmpty()) {
            val replacement = createFromImportStatement(newModulePath, moved, generator, languageLevel)
            statement.replace(replacement)
            return
        }

        val newStatement = createFromImportStatement(newModulePath, moved, generator, languageLevel)
        statement.parent.addAfter(newStatement, statement)

        val updatedOriginal = createFromImportStatement(oldModulePath, remaining, generator, languageLevel)
        statement.replace(updatedOriginal)
    }

    private fun updateImportStatement(statement: PyImportStatement, oldModulePath: String, newModulePath: String) {
        val moved = statement.importElements.filter { it.importedQName?.toString() == oldModulePath }
        if (moved.isEmpty()) return

        val remaining = statement.importElements.filterNot { it in moved }
        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(statement)

        if (remaining.isEmpty()) {
            val replacement = createImportStatement(newModulePath, moved, generator, languageLevel)
            statement.replace(replacement)
            return
        }

        val newStatement = createImportStatement(newModulePath, moved, generator, languageLevel)
        statement.parent.addAfter(newStatement, statement)

        val updatedOriginal = createImportStatementFromElements(remaining, generator, languageLevel)
        statement.replace(updatedOriginal)
    }

    private fun updateQualifiedReference(
        reference: PyReferenceExpression,
        oldModulePath: String,
        newModulePath: String,
        movedName: String
    ) {
        val qualifiedName = reference.asQualifiedName()?.toString() ?: return
        val oldQualified = "$oldModulePath.$movedName"
        if (qualifiedName != oldQualified) return

        val generator = PyElementGenerator.getInstance(project)
        val languageLevel = LanguageLevel.forElement(reference)
        val newExpression = generator.createExpressionFromText(languageLevel, "$newModulePath.$movedName")
        reference.replace(newExpression)
    }

    private fun createFromImportStatement(
        modulePath: String,
        elements: List<PyImportElement>,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ): PyFromImportStatement {
        val imports = elements.joinToString(", ") { renderImportElement(it) }
        val text = "from $modulePath import $imports"
        return generator.createFromText(languageLevel, PyFromImportStatement::class.java, text)
    }

    private fun createImportStatement(
        modulePath: String,
        elements: List<PyImportElement>,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ): PyImportStatement {
        val rendered = elements.joinToString(", ") {
            val alias = it.asName
            if (!alias.isNullOrBlank()) "$modulePath as $alias" else modulePath
        }
        val text = "import $rendered"
        return generator.createFromText(languageLevel, PyImportStatement::class.java, text)
    }

    private fun createImportStatementFromElements(
        elements: List<PyImportElement>,
        generator: PyElementGenerator,
        languageLevel: LanguageLevel
    ): PyImportStatement {
        val rendered = elements.joinToString(", ") { renderImportElement(it, includeModule = true) }
        val text = "import $rendered"
        return generator.createFromText(languageLevel, PyImportStatement::class.java, text)
    }

    private fun renderImportElement(element: PyImportElement, includeModule: Boolean = false): String {
        val baseName = if (includeModule) {
            element.importedQName?.toString() ?: element.visibleName ?: ""
        } else {
            element.importedQName?.lastComponent ?: element.visibleName ?: ""
        }
        val alias = element.asName
        return if (!alias.isNullOrBlank()) "$baseName as $alias" else baseName
    }

    private fun renderElementText(element: PsiElement): String {
        val fileText = element.containingFile?.text ?: return ""
        val startElement = when (element) {
            is PyDecoratable -> element.decoratorList ?: element
            else -> element
        }
        val startOffset = startElement.textRange.startOffset
        val endOffset = element.textRange.endOffset
        return MoveScopeTextBuilder.reindentRange(fileText, startOffset, endOffset, 0)
    }

    private fun insertElementIntoTarget(targetFile: PyFile, element: PsiElement) {
        val statements = targetFile.statements
        if (statements.isEmpty()) {
            targetFile.add(element)
            return
        }

        val anchor = statements.last()
        val doc = PsiDocumentManager.getInstance(project).getDocument(targetFile)
        val text = doc?.text ?: targetFile.text
        val separator = when {
            text.endsWith("\n\n") -> ""
            text.endsWith("\n") -> "\n"
            else -> "\n\n"
        }

        if (separator.isNotEmpty()) {
            val whitespace = PsiParserFacade.getInstance(project).createWhiteSpaceFromText(separator)
            val ws = targetFile.addAfter(whitespace, anchor)
            targetFile.addAfter(element, ws)
        } else {
            targetFile.addAfter(element, anchor)
        }
    }

    private fun removeElementWithWhitespace(element: PsiElement) {
        val file = element.containingFile ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return
        documentManager.doPostponedOperationsAndUnblockDocument(document)

        val startElement = when (element) {
            is PyDecoratable -> element.decoratorList ?: element
            else -> element
        }

        val startOffset = startElement.textRange.startOffset
        val endOffset = element.textRange.endOffset
        val startLine = document.getLineNumber(startOffset)
        var endLine = document.getLineNumber(endOffset)

        if (endLine + 1 < document.lineCount) {
            val nextLineStart = document.getLineStartOffset(endLine + 1)
            val nextLineEnd = document.getLineEndOffset(endLine + 1)
            val nextLineText = document.getText(TextRange(nextLineStart, nextLineEnd))
            if (nextLineText.isBlank()) {
                endLine += 1
            }
        }

        val lineStart = document.getLineStartOffset(startLine)
        var lineEnd = document.getLineEndOffset(endLine)
        if (lineEnd < document.textLength) {
            lineEnd += 1
        }
        document.deleteString(lineStart, lineEnd)
        if (document.textLength == 0) {
            document.insertString(0, "\n")
        }
        documentManager.commitDocument(document)
    }

    private fun findModulePath(file: PyFile): String? {
        return QualifiedNameFinder.findCanonicalImportPath(file, null)?.toString()
            ?: file.virtualFile?.nameWithoutExtension
    }

    private fun normalizeModulePath(rawPath: String): String {
        return rawPath.trim().removeSuffix(".py").trim('.')
    }

    private fun resolveTargetFile(sourceFile: PyFile, modulePath: String): PyFile? {
        val sourceRoot = ProjectRootManager.getInstance(project).fileIndex
            .getSourceRootForFile(sourceFile.virtualFile)
            ?: sourceFile.virtualFile.parent
            ?: return null

        val parts = modulePath.split('.')
        if (parts.isEmpty()) return null
        val fileName = parts.last() + ".py"
        val packageParts = parts.dropLast(1)

        var currentDir = sourceRoot
        for (part in packageParts) {
            currentDir = currentDir.findChild(part) ?: currentDir.createChildDirectory(this, part)
            ensureInitPy(currentDir)
        }

        val targetVFile = currentDir.findChild(fileName) ?: currentDir.createChildData(this, fileName)
        return PsiManager.getInstance(project).findFile(targetVFile) as? PyFile
    }

    private fun ensureInitPy(directory: com.intellij.openapi.vfs.VirtualFile) {
        if (directory.findChild("__init__.py") == null) {
            directory.createChildData(this, "__init__.py")
        }
    }

    private fun ensureTrailingNewline(file: PyFile) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        val text = document.text
        if (!text.endsWith("\n")) {
            document.insertString(text.length, "\n")
        }
    }
}
