package com.github.chbndrhnns.betterpy.features.pytest.fixture

import com.github.chbndrhnns.betterpy.core.psi.PyImportService
import com.github.chbndrhnns.betterpy.features.pytest.testtree.PytestTestContextUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

private data class PytestInsertionContext(
    val project: Project,
    val editor: Editor?,
    val file: PyFile,
    val elementAtCaret: PsiElement,
    val targetClass: PyClass?,
    val caretOffset: Int,
    val moduleSeparator: String,
    val useCaretInsertion: Boolean
)

class NewPytestTestAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = PytestMemberActionsFeatureToggle.isEnabled() && resolveContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!PytestMemberActionsFeatureToggle.isEnabled()) return
        val context = resolveContext(e) ?: return
        val anchor = findAnchorStatement(
            context.targetClass?.statementList?.statements?.toList(),
            context.file.statements.toList(),
            context.caretOffset,
            context.elementAtCaret
        )
        val target = context.targetClass?.statementList ?: context.file
        val existingNames = collectExistingNames(context.targetClass, context.file)
        val name = generateUniqueName("test_new", existingNames)

        WriteCommandAction.runWriteCommandAction(context.project, "New Pytest Test", null, {
            val generator = PyElementGenerator.getInstance(context.project)
            val functionText = "def $name():\n    pass"
            val newFunction = generator.createFromText(
                LanguageLevel.forElement(context.file),
                PyFunction::class.java,
                functionText
            )
            if (context.targetClass != null) {
                ensureSelfParameter(generator, newFunction)
            }
            insertFunction(
                context.project,
                target,
                anchor,
                newFunction,
                context.caretOffset,
                context.moduleSeparator,
                context.useCaretInsertion,
                context.editor
            )
        }, context.file)
    }
}

class NewPytestFixtureAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = PytestMemberActionsFeatureToggle.isEnabled() && resolveContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        if (!PytestMemberActionsFeatureToggle.isEnabled()) return
        val context = resolveContext(e) ?: return
        val anchor = findAnchorStatement(
            context.targetClass?.statementList?.statements?.toList(),
            context.file.statements.toList(),
            context.caretOffset,
            context.elementAtCaret
        )
        val target = context.targetClass?.statementList ?: context.file
        val existingNames = collectExistingNames(context.targetClass, context.file)
        val name = generateUniqueName("new_fixture", existingNames)

        WriteCommandAction.runWriteCommandAction(context.project, "New Pytest Fixture", null, {
            val generator = PyElementGenerator.getInstance(context.project)
            val functionText = "@pytest.fixture\ndef $name():\n    pass"
            val newFunction = generator.createFromText(
                LanguageLevel.forElement(context.file),
                PyFunction::class.java,
                functionText
            )
            if (context.targetClass != null) {
                ensureSelfParameter(generator, newFunction)
            }
            insertFunction(
                context.project,
                target,
                anchor,
                newFunction,
                context.caretOffset,
                context.moduleSeparator,
                context.useCaretInsertion,
                context.editor
            )
            PyImportService().ensureModuleImported(context.file, "pytest")
        }, context.file)
    }
}

private fun resolveContext(e: AnActionEvent): PytestInsertionContext? {
    val project = e.project ?: return null
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? PyFile ?: return null
    val caretOffset = editor.caretModel.offset
    val elementAtCaret = findElementAtOrNear(psiFile, caretOffset) ?: psiFile
    val isTestContext = PytestTestContextUtils.isInTestContext(elementAtCaret)
    val isTestFile = PytestTestContextUtils.isTestFile(psiFile) || psiFile.name == "conftest.py"
    if (!isTestContext && !isTestFile) return null
    val targetClass = findTargetClass(psiFile, elementAtCaret, caretOffset, editor)
    val moduleSeparator = computeModuleSeparator(psiFile, elementAtCaret, caretOffset, targetClass == null)
    val useCaretInsertion = isTopLevelCaret(psiFile, caretOffset) && targetClass == null
    val effectiveElement = if (targetClass == null) psiFile else elementAtCaret
    return PytestInsertionContext(
        project,
        editor,
        psiFile,
        effectiveElement,
        targetClass,
        caretOffset,
        moduleSeparator,
        useCaretInsertion
    )
}

private fun findElementAtOrNear(file: PsiElement, offset: Int): PsiElement? {
    if (file !is PyFile) return null
    if (offset < 0 || offset > file.textLength) return null
    return file.findElementAt(offset)
        ?: if (offset > 0) file.findElementAt(offset - 1) else null
            ?: if (offset + 1 <= file.textLength) file.findElementAt(offset + 1) else null
}

private fun findTargetClass(file: PyFile, element: PsiElement, caretOffset: Int, editor: Editor?): PyClass? {
    val direct = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
    if (direct != null) return direct
    val prevLeaf = PsiTreeUtil.prevLeaf(element, true)
    val fromPrev = prevLeaf?.let { PsiTreeUtil.getParentOfType(it, PyClass::class.java, false) }
    if (fromPrev != null && caretOffset <= fromPrev.textRange.endOffset) return fromPrev
    val document = editor?.document ?: return file.topLevelClasses.firstOrNull { caretOffset in it.textRange }
    val caretLine = document.getLineNumber(caretOffset)
    val caretLineStart = document.getLineStartOffset(caretLine)
    val caretLineEnd = document.getLineEndOffset(caretLine)
    val caretIndent = lineIndent(document.charsSequence, caretLineStart, caretLineEnd)

    val candidate = file.topLevelClasses
        .filter { it.textRange.startOffset < caretOffset }
        .maxByOrNull { it.textRange.startOffset }
        ?: return null

    val classLine = document.getLineNumber(candidate.textRange.startOffset)
    val classLineStart = document.getLineStartOffset(classLine)
    val classLineEnd = document.getLineEndOffset(classLine)
    val classIndent = lineIndent(document.charsSequence, classLineStart, classLineEnd)

    if (caretLine <= classLine) return null
    if (caretIndent <= classIndent) return null

    val nextTopLevel = file.statements
        .filter { it.textRange.startOffset > candidate.textRange.startOffset }
        .minByOrNull { it.textRange.startOffset }
    if (nextTopLevel != null && caretOffset >= nextTopLevel.textRange.startOffset) return null

    return candidate
}

private fun lineIndent(text: CharSequence, start: Int, end: Int): Int {
    var count = 0
    var index = start
    while (index < end) {
        val ch = text[index]
        if (ch == ' ' || ch == '\t') {
            count += 1
            index += 1
            continue
        }
        break
    }
    return count
}

private fun collectExistingNames(targetClass: PyClass?, file: PyFile): Set<String> {
    return targetClass?.methods?.mapNotNull { it.name }?.toSet() ?: file.topLevelFunctions.mapNotNull { it.name }
        .toSet()
}

private fun generateUniqueName(baseName: String, existing: Set<String>): String {
    if (!existing.contains(baseName)) return baseName
    var counter = 2
    while (existing.contains("${baseName}_$counter")) {
        counter += 1
    }
    return "${baseName}_$counter"
}

private fun findAnchorStatement(
    classStatements: List<PyStatement>?,
    fileStatements: List<PyStatement>,
    caretOffset: Int,
    elementAtCaret: PsiElement
): PsiElement? {
    val statements = classStatements ?: fileStatements
    if (elementAtCaret is PsiWhiteSpace) {
        val statementList = PsiTreeUtil.getParentOfType(elementAtCaret, PyStatementList::class.java, false)
        val owner = statementList?.parent
        if (classStatements != null) {
            if (owner is PyClass) {
                return elementAtCaret
            }
        } else if (owner == null) {
            return elementAtCaret
        }
    }
    return statements.firstOrNull { it.textRange.startOffset >= caretOffset }
}

private fun insertFunction(
    project: Project,
    target: PsiElement,
    anchor: PsiElement?,
    function: PyFunction,
    caretOffset: Int,
    moduleSeparator: String,
    useCaretInsertion: Boolean,
    editor: Editor?
) {
    val parserFacade = PsiParserFacade.getInstance(project)
    if (anchor != null) {
        if (anchor is PsiWhiteSpace && target is PyFile) {
            val text = anchor.text
            val splitIndex = (caretOffset - anchor.textRange.startOffset).coerceIn(0, text.length)
            val beforeText = text.substring(0, splitIndex)
            val afterText = text.substring(splitIndex)
            if (beforeText.isNotEmpty()) {
                target.addBefore(parserFacade.createWhiteSpaceFromText(beforeText), anchor)
            }
            target.addBefore(function, anchor)
            if (afterText.isNotEmpty()) {
                target.addBefore(parserFacade.createWhiteSpaceFromText(afterText), anchor)
            }
            anchor.delete()
            return
        }
        val inserted = target.addBefore(function, anchor)
        target.addAfter(parserFacade.createWhiteSpaceFromText("\n\n"), inserted)
        if (anchor is PsiWhiteSpace) {
            anchor.delete()
        }
        return
    }

    if (target is PyFile && useCaretInsertion && editor != null) {
        val document = editor.document
        document.insertString(caretOffset, function.text)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return
    }

    val lastStatement = when (target) {
        is PyFile -> target.statements.lastOrNull()
        is PyStatementList -> target.statements.lastOrNull()
        else -> null
    }
    if (lastStatement != null) {
        val inserted = target.addAfter(function, lastStatement)
        val separator = if (target is PyFile) moduleSeparator else "\n\n"
        val sibling = lastStatement.nextSibling
        if (sibling is PsiWhiteSpace) {
            sibling.replace(parserFacade.createWhiteSpaceFromText(separator))
        } else {
            target.addBefore(parserFacade.createWhiteSpaceFromText(separator), inserted)
        }
        if (target is PyStatementList) {
            val trailing = PsiTreeUtil.nextLeaf(inserted, false)
            if (trailing is PsiWhiteSpace && trailing.text.isBlank()) {
                trailing.delete()
            }
        }
    } else {
        target.add(function)
    }
}

private fun computeModuleSeparator(
    file: PyFile,
    elementAtCaret: PsiElement,
    caretOffset: Int,
    isModuleLevel: Boolean
): String {
    if (!isModuleLevel) return "\n\n\n"
    val functionParent = PsiTreeUtil.getParentOfType(elementAtCaret, PyFunction::class.java, false)
    if (functionParent != null) return "\n\n\n"
    val previous = file.statements
        .filter { it.textRange.endOffset <= caretOffset }
        .maxByOrNull { it.textRange.endOffset }
        ?: return "\n\n\n"
    val slice = file.text.substring(previous.textRange.endOffset, caretOffset.coerceAtMost(file.textLength))
    val newlines = slice.count { it == '\n' }
    if (previous is PyClass) {
        return "\n"
    }
    val normalizedCount = maxOf(2, newlines)
    return "\n".repeat(normalizedCount)
}

private fun isTopLevelCaret(file: PyFile, caretOffset: Int): Boolean {
    return file.statements.none { caretOffset in it.textRange }
}

private fun ensureSelfParameter(generator: PyElementGenerator, function: PyFunction) {
    val params = function.parameterList
    val parameters = params.parameters
    if (parameters.isEmpty()) {
        params.addParameter(generator.createParameter("self"))
        return
    }
    val first = parameters.first()
    if (first.name == "self" || first.name == "cls") return
    val selfParam = generator.createParameter("self")
    params.addBefore(selfParam, first)
    val comma = generator.createComma().psi
    params.addBefore(comma, first)
}

 
