package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.github.chbndrhnns.betterpy.core.pytest.PytestNaming
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.SimpleListCellRenderer
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
import com.jetbrains.python.codeInsight.override.PyOverrideMethodsHandler
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.types.TypeEvalContext

class PytestFixtureOverrideMethodsHandler : LanguageCodeInsightActionHandler {
    private val delegate = PyOverrideMethodsHandler()
    private val log = Logger.getInstance(PytestFixtureOverrideMethodsHandler::class.java)

    override fun isValidFor(editor: Editor, file: PsiFile): Boolean {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return delegate.isValidFor(editor, file)
        }
        val pyFile = file as? PyFile ?: return delegate.isValidFor(editor, file)
        if (!isPytestOverrideContext(pyFile)) {
            return delegate.isValidFor(editor, file)
        }

        if (!isValidCaretPosition(editor, pyFile)) {
            return delegate.isValidFor(editor, file)
        }

        val element = pyFile.findElementAt(editor.caretModel.offset) ?: pyFile
        val context = TypeEvalContext.codeAnalysis(pyFile.project, pyFile)
        val caretClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        val targetClass = caretClass ?: PyOverrideImplementUtil.getContextClass(editor, pyFile)
        val candidates = if (targetClass != null) {
            PytestFixtureOverrideUtil.collectOverridableFixturesInClass(targetClass, pyFile, context)
        } else {
            PytestFixtureOverrideUtil.collectOverridableFixtures(element, context)
        }
        if (log.isDebugEnabled) {
            log.debug("PytestFixtureOverrideMethodsHandler.isValidFor: candidates=${candidates.size}")
        }
        return candidates.isNotEmpty() || delegate.isValidFor(editor, file)
    }

    private fun invokeDelegateWithProgress(project: Project, editor: Editor, file: PsiFile) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            delegate.invoke(project, editor, file)
        } else {
            runWithModalProgressBlocking(ModalTaskOwner.project(project), "Override Methods") {
                delegate.invoke(project, editor, file)
            }
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (!PytestFixtureFeatureToggle.isEnabled()) {
            return invokeDelegateWithProgress(project, editor, file)
        }
        val pyFile = file as? PyFile ?: return invokeDelegateWithProgress(project, editor, file)
        if (!isPytestOverrideContext(pyFile)) {
            return invokeDelegateWithProgress(project, editor, file)
        }

        if (!isValidCaretPosition(editor, pyFile)) {
            return invokeDelegateWithProgress(project, editor, file)
        }

        val element = pyFile.findElementAt(editor.caretModel.offset) ?: pyFile
        val context = TypeEvalContext.codeAnalysis(project, pyFile)
        val caretClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        val targetClass = caretClass ?: PyOverrideImplementUtil.getContextClass(editor, pyFile)
        val candidates = if (targetClass != null) {
            PytestFixtureOverrideUtil.collectOverridableFixturesInClass(targetClass, pyFile, context)
        } else {
            PytestFixtureOverrideUtil.collectOverridableFixtures(element, context)
        }
        if (candidates.isEmpty()) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureOverrideMethodsHandler.invoke: no fixtures found, delegating to default override")
            }
            return invokeDelegateWithProgress(project, editor, file)
        }
        if (log.isDebugEnabled) {
            log.debug("PytestFixtureOverrideMethodsHandler.invoke: showing ${candidates.size} fixture override(s)")
        }

        val items = buildPopupItems(candidates, targetClass)

        if (ApplicationManager.getApplication().isUnitTestMode) {
            if (log.isDebugEnabled) {
                log.debug("PytestFixtureOverrideMethodsHandler.invoke: unit test mode, inserting first fixture")
            }
            insertFixtureAtCaret(project, editor, pyFile, targetClass, candidates.first())
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("Override Pytest Fixture")
            .setNamerForFiltering { item -> item.displayText }
            .setItemChosenCallback { item ->
                when (item) {
                    is PopupItem.Fixture -> insertFixtureAtCaret(project, editor, pyFile, targetClass, item.link)
                    PopupItem.DefaultOverride -> invokeDelegateWithProgress(project, editor, file)
                }
            }
            .setRenderer(SimpleListCellRenderer.create<PopupItem> { label, value, _ ->
                label.text = value.displayText
            })
            .createPopup()
            .showInBestPositionFor(editor)
    }

    override fun startInWriteAction(): Boolean = false

    private fun isPytestOverrideContext(file: PyFile): Boolean {
        return PytestNaming.isTestFile(file) || file.name == "conftest.py"
    }

    private fun insertFixtureAtCaret(
        project: Project,
        editor: Editor,
        file: PyFile,
        targetClass: PyClass?,
        link: FixtureLink
    ) {
        val newFunction = buildFixtureOverride(project, targetClass, link)

        if (log.isDebugEnabled) {
            log.debug(
                "PytestFixtureOverrideMethodsHandler.insertFixtureAtCaret: inserting fixture '${link.fixtureName}' " +
                        "into ${targetClass?.name ?: "module"}"
            )
        }
        WriteCommandAction.runWriteCommandAction(project, "Override Pytest Fixture", null, {
            ensureFixtureImports(file, link.fixtureFunction)

            val caretOffset = editor.caretModel.offset
            val classStatements = targetClass?.statementList?.statements?.toList()
            val anchor = if (targetClass == null) {
                val lastImport = file.importBlock.lastOrNull()
                if (lastImport != null && caretOffset <= lastImport.textRange.endOffset) {
                    file.statements.firstOrNull { it.textRange.startOffset > lastImport.textRange.endOffset }
                } else {
                    findAnchorStatement(null, file.statements.toList(), caretOffset)
                }
            } else {
                findAnchorStatement(classStatements, file.statements.toList(), caretOffset)
            }
            val target = targetClass?.statementList ?: file

            if (anchor != null) {
                val inserted = target.addBefore(newFunction, anchor)
                target.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n\n"), inserted)
            } else {
                target.add(newFunction)
            }
        }, file)
    }

    private fun findAnchorStatement(
        classStatements: List<PyStatement>?,
        fileStatements: List<PyStatement>,
        caretOffset: Int
    ): PyStatement? {
        val statements = classStatements ?: fileStatements
        return statements.firstOrNull { it.textRange.startOffset >= caretOffset }
    }

    private fun buildFixtureOverride(
        project: Project,
        targetClass: PyClass?,
        link: FixtureLink
    ): PyFunction {
        val generator = PyElementGenerator.getInstance(project)
        val original = link.fixtureFunction
        val paramsText = buildFixtureParameterText(original, targetClass, link.fixtureName)
        val decoratorsText = original.decoratorList?.decorators
            ?.joinToString("\n") { it.text.trimStart() }
            ?.let { "$it\n" }
            ?: ""
        val asyncPrefix = if (original.isAsync) "async " else ""
        val annotationText = original.annotation?.text?.trim()?.removePrefix("->")?.trim()
        val returnAnnotation = annotationText?.takeIf { it.isNotEmpty() }?.let { " -> $it" } ?: ""
        val functionText = buildString {
            append(decoratorsText)
            append(asyncPrefix)
            append("def ")
            append(original.name ?: link.fixtureName)
            append("(")
            append(paramsText)
            append(")")
            append(returnAnnotation)
            append(":\n    return ")
            append(link.fixtureName)
        }
        return generator.createFromText(
            LanguageLevel.forElement(original),
            PyFunction::class.java,
            functionText
        )
    }

    private fun buildFixtureParameterText(
        original: PyFunction,
        targetClass: PyClass?,
        fixtureName: String
    ): String {
        val originalParams = original.parameterList.parameters.map { it.text.trim() }.toMutableList()
        val result = mutableListOf<String>()
        if (targetClass != null) {
            val first = originalParams.firstOrNull()
            if (first == "self" || first == "cls") {
                result.add(first)
                originalParams.removeAt(0)
            } else {
                result.add("self")
            }
        }
        if (fixtureName !in result) {
            result.add(fixtureName)
        }
        originalParams.filter { it != fixtureName }.forEach { result.add(it) }
        return result.joinToString(", ")
    }

    private fun ensureFixtureImports(file: PyFile, function: PyFunction) {
        val importService = PyImportService()
        val decorators = function.decoratorList?.decorators ?: emptyArray()
        var needsPytest = false
        var needsPytestAsyncio = false
        var needsFixtureFrom = false

        for (decorator in decorators) {
            val qName = decorator.qualifiedName?.toString()
            when {
                qName == "pytest_asyncio.fixture" || qName?.endsWith(".pytest_asyncio.fixture") == true -> {
                    needsPytestAsyncio = true
                }

                qName == "pytest.fixture" || qName?.endsWith(".pytest.fixture") == true -> {
                    needsPytest = true
                }

                qName == "fixture" -> {
                    needsFixtureFrom = true
                }
            }
        }

        if (needsFixtureFrom) {
            importService.ensureFromImport(file, "pytest", "fixture")
        }
        if (needsPytestAsyncio) {
            importService.ensureModuleImported(file, "pytest_asyncio")
        }
        if (needsPytest || (!needsFixtureFrom && !needsPytestAsyncio)) {
            importService.ensureModuleImported(file, "pytest")
        }
    }

    private sealed class PopupItem {
        abstract val displayText: String

        data class Fixture(
            val link: FixtureLink,
            override val displayText: String
        ) : PopupItem() {
        }

        data object DefaultOverride : PopupItem() {
            override val displayText: String = "Override methods..."
        }
    }

    internal fun buildPopupDisplayTexts(
        candidates: List<FixtureLink>,
        targetClass: PyClass?
    ): List<String> {
        return buildPopupItems(candidates, targetClass).map { it.displayText }
    }

    private fun buildPopupItems(
        candidates: List<FixtureLink>,
        targetClass: PyClass?
    ): List<PopupItem> {
        return buildList {
            addAll(candidates.map { PopupItem.Fixture(it, formatFixtureDisplayText(it)) })
            if (targetClass != null) {
                add(PopupItem.DefaultOverride)
            }
        }
    }

    private fun formatFixtureDisplayText(link: FixtureLink): String {
        val container = fixtureContainer(link)
        return if (container != null) "${link.fixtureName} ($container)" else link.fixtureName
    }

    private fun fixtureContainer(link: FixtureLink): String? {
        val function = link.fixtureFunction
        val fileModule = moduleName(function.containingFile)

        val classFqn = function.containingClass?.let { cls ->
            val classChain = buildClassChain(cls)
            fileModule?.let { "$it.$classChain" } ?: classChain
        }

        return classFqn ?: fileModule
    }

    private fun buildClassChain(cls: PyClass): String {
        val classNames = mutableListOf<String>()
        var current: PyClass? = cls
        while (current != null) {
            classNames.add(current.name ?: PyNames.UNNAMED_ELEMENT)
            current = PsiTreeUtil.getParentOfType(current, PyClass::class.java, true)
        }
        return classNames.asReversed().joinToString(".")
    }

    private fun moduleName(file: PsiFile?): String? {
        file ?: return null
        return QualifiedNameFinder.findCanonicalImportPath(file, null)?.toString()
            ?: file.name.removeSuffix(".py")
    }

    internal fun isValidCaretPosition(editor: Editor, file: PyFile): Boolean {
        val offset = editor.caretModel.offset
        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        if (lineText.trim().isNotEmpty()) {
            return false
        }

        val element = file.findElementAt(offset) ?: file
        if (PsiTreeUtil.getParentOfType(element, PyFunction::class.java) != null) {
            return false
        }
        return true
    }
}
