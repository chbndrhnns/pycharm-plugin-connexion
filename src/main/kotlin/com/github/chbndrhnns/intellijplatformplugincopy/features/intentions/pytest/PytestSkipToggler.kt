package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.pytest

import com.github.chbndrhnns.intellijplatformplugincopy.core.psi.PyImportService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.ArrayUtilRt
import com.jetbrains.python.psi.*

class PytestSkipToggler(private val generator: PyElementGenerator) {
    private val importService = PyImportService()

    fun toggleOnFunction(function: PyFunction, file: PyFile) {
        toggleDecorator(function, file)
    }

    fun toggleOnClass(clazz: PyClass, file: PyFile) {
        toggleDecorator(clazz, file)
    }

    fun toggleOnModule(file: PyFile) {
        toggleModuleLevelSkip(file)
    }

    fun toggleOnSimpleParam(element: PsiElement, file: PyFile) {
        var target = element
        // Walk up to find the list item (expression inside PySequenceExpression)
        if (target !is PsiComment) {
            while (target.parent != null && target.parent !is PyFile) {
                val parent = target.parent
                if (parent is PySequenceExpression && isPytestParametrizeOrFixtureParams(parent)) {
                    break
                }
                target = parent
            }
            if (target.parent !is PySequenceExpression) {
                return
            }
        }

        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

        if (target is PsiComment) {
            val (startComment, endComment) = getRangeToUncomment(target)
            val commentsToUncomment = mutableListOf<PsiComment>()
            var cur: PsiElement? = startComment
            while (cur != null) {
                if (cur is PsiComment) {
                    commentsToUncomment.add(cur)
                }
                if (cur == endComment) break
                cur = cur.nextSibling
            }

            for (comment in commentsToUncomment.asReversed()) {
                val text = comment.text
                if (text.startsWith("#")) {
                    val content = if (text.startsWith("# ")) {
                        text.substring(2)
                    } else {
                        text.substring(1)
                    }
                    document.replaceString(comment.textRange.startOffset, comment.textRange.endOffset, content)
                }
            }
        } else {
            var endOffset = target.textRange.endOffset
            var next = target.nextSibling
            while (next is PsiWhiteSpace) {
                next = next.nextSibling
            }
            if (next != null && next.text == ",") {
                endOffset = next.textRange.endOffset
            }

            val textToComment = document.getText(TextRange(target.textRange.startOffset, endOffset))

            val startLine = document.getLineNumber(target.textRange.startOffset)
            val lineStartOffset = document.getLineStartOffset(startLine)
            val indentStr = document.getText(TextRange(lineStartOffset, target.textRange.startOffset))

            val lines = textToComment.lines()
            val commentedText = lines.mapIndexed { index, line ->
                if (line.isBlank()) {
                    line
                } else if (index == 0) {
                    "# $line"
                } else {
                    if (line.startsWith(indentStr)) {
                        indentStr + "# " + line.substring(indentStr.length)
                    } else {
                        "# " + line
                    }
                }
            }.joinToString("\n")
            document.replaceString(target.textRange.startOffset, endOffset, commentedText)
        }
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
    }

    private fun getRangeToUncomment(comment: PsiComment): Pair<PsiComment, PsiComment> {
        var first = comment
        while (first.prevSibling is PsiComment || (first.prevSibling is PsiWhiteSpace && first.prevSibling.prevSibling is PsiComment)) {
            first =
                if (first.prevSibling is PsiComment) first.prevSibling as PsiComment else first.prevSibling.prevSibling as PsiComment
        }

        var last = comment
        while (last.nextSibling is PsiComment || (last.nextSibling is PsiWhiteSpace && last.nextSibling.nextSibling is PsiComment)) {
            last =
                if (last.nextSibling is PsiComment) last.nextSibling as PsiComment else last.nextSibling.nextSibling as PsiComment
        }

        val comments = mutableListOf<PsiComment>()
        var cur: PsiElement? = first
        while (cur != null && (cur == last || cur.textRange.endOffset <= last.textRange.endOffset)) {
            if (cur is PsiComment) {
                comments.add(cur)
            }
            if (cur == last) break
            cur = cur.nextSibling
        }

        if (comments.size <= 1) return Pair(comment, comment)

        var balance = 0
        var segmentStartIdx = 0

        for (i in comments.indices) {
            val c = comments[i]
            val content = c.text.trimStart('#').trim()
            balance += countBalance(content)

            if (balance == 0) {
                val segmentComments = comments.subList(segmentStartIdx, i + 1)
                if (segmentComments.contains(comment)) {
                    return Pair(segmentComments.first(), segmentComments.last())
                }
                segmentStartIdx = i + 1
            }
        }

        if (segmentStartIdx < comments.size) {
            return Pair(comments[segmentStartIdx], comments.last())
        }

        return Pair(comment, comment)
    }

    private fun countBalance(text: String): Int {
        var balance = 0
        for (char in text) {
            when (char) {
                '(', '[', '{' -> balance++
                ')', ']', '}' -> balance--
            }
        }
        return balance
    }

    fun toggleOnParam(callExpression: PyCallExpression, file: PyFile) {
        val marksArg = callExpression.getKeywordArgument("marks")
        val skipMarker = "pytest.mark.skip"
        importService.ensureModuleImported(file, "pytest")

        if (marksArg != null) {
            val items = if (marksArg is PyListLiteralExpression) {
                marksArg.elements.map { it.text }.toMutableList()
            } else {
                mutableListOf(marksArg.text)
            }

            val hasSkip = items.any { it.contains(skipMarker) }
            if (hasSkip) {
                items.removeIf { it.contains(skipMarker) }
            } else {
                items.add(skipMarker)
            }

            val newListText = if (items.isNotEmpty()) {
                val hasSkipMarker = items.any { it.contains(skipMarker) }
                "[" + items.joinToString(", ") + (if (hasSkipMarker) "," else "") + "]"
            } else {
                "[]"
            }
            val newExpression = generator.createExpressionFromText(LanguageLevel.getLatest(), newListText)
            marksArg.replace(newExpression)
        } else {
            val argList = callExpression.argumentList
            if (argList != null) {
                val newArgText = "marks=[pytest.mark.skip,]"
                val argsText = argList.arguments.joinToString(", ") { it.text }
                val separator = if (argsText.isBlank()) "" else ", "
                val newArgListText = "($argsText$separator$newArgText)"

                val newArgList = generator.createArgumentList(LanguageLevel.getLatest(), newArgListText)
                argList.replace(newArgList)
            }
        }
    }

    private fun toggleDecorator(element: PyDecoratable, file: PyFile) {
        val skipMarker = "pytest.mark.skip"

        val existingDecorator = element.decoratorList?.decorators?.firstOrNull {
            it.qualifiedName.toString() == skipMarker || it.text.contains(skipMarker)
        }

        if (existingDecorator != null) {
            existingDecorator.delete()
            return
        }

        importService.ensureModuleImported(file, "pytest")

        if (element is PyFunction) {
            PyUtil.addDecorator(element, "@$skipMarker")
        } else {
            addDecoratorToDecoratable(element, "@$skipMarker")
        }
    }

    private fun addDecoratorToDecoratable(element: PyDecoratable, decoratorText: String) {
        val currentDecoratorList = element.decoratorList
        val decoTexts = ArrayList<String>()
        decoTexts.add(decoratorText)

        if (currentDecoratorList != null) {
            for (deco in currentDecoratorList.decorators) {
                decoTexts.add(deco.text)
            }
        }

        val newDecoratorList = generator.createDecoratorList(*ArrayUtilRt.toStringArray(decoTexts))

        if (currentDecoratorList != null) {
            currentDecoratorList.replace(newDecoratorList)
        } else {
            element.addBefore(newDecoratorList, element.firstChild)
        }
    }

    private fun toggleModuleLevelSkip(file: PyFile) {
        val assignments = file.statements.filterIsInstance<PyAssignmentStatement>()
        val pytestMarkAssignment = assignments.firstOrNull {
            it.targets.any { target -> target.text == "pytestmark" }
        }

        if (pytestMarkAssignment != null) {
            val value = pytestMarkAssignment.assignedValue

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

            importService.ensureModuleImported(file, "pytest")

            val newListText = "[" + items.joinToString(", ") + "]"
            val newAssignment = generator.createFromText(
                LanguageLevel.getLatest(),
                PyAssignmentStatement::class.java,
                "pytestmark = $newListText"
            )
            pytestMarkAssignment.replace(newAssignment)
            return
        }

        importService.ensureModuleImported(file, "pytest")

        val newAssignment = generator.createFromText(
            LanguageLevel.getLatest(),
            PyAssignmentStatement::class.java,
            "pytestmark = [pytest.mark.skip]"
        )

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
