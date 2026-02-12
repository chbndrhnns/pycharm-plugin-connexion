package com.github.chbndrhnns.betterpy.features.intentions.pytest

import com.github.chbndrhnns.betterpy.core.pytest.PytestParametrizeUtil
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

object ParametrizeSignatureUtil {

    fun findNamesArg(args: Array<PyExpression>, decorator: PyDecorator): PyExpression? {
        val first = args[0]
        if (first !is PyKeywordArgument) return first
        return decorator.getKeywordArgument("argnames")
    }

    fun findValuesArg(args: Array<PyExpression>, decorator: PyDecorator): PyExpression? {
        if (args.size >= 2 && args[1] !is PyKeywordArgument) return args[1]
        return decorator.getKeywordArgument("argvalues")
    }

    fun generateNewParamName(existingNames: List<String>): String {
        var index = existingNames.size + 1
        var name = "arg$index"
        while (name in existingNames) {
            index++
            name = "arg$index"
        }
        return name
    }

    private fun createExpression(text: String, context: PsiElement, generator: PyElementGenerator): PyExpression {
        return generator.createExpressionFromText(LanguageLevel.forElement(context), text)
    }

    fun addNameToArgnames(namesArg: PyExpression, newName: String, generator: PyElementGenerator) {
        when (namesArg) {
            is PyStringLiteralExpression -> {
                val oldValue = namesArg.stringValue
                val newValue = "$oldValue, $newName"
                val newLiteral = generator.createStringLiteralAlreadyEscaped("\"$newValue\"")
                namesArg.replace(newLiteral)
            }

            is PyParenthesizedExpression -> {
                val contained = namesArg.containedExpression
                if (contained is PyTupleExpression) {
                    addStringToSequence(contained, newName, generator, namesArg)
                }
            }

            is PyTupleExpression -> {
                addStringToSequence(namesArg, newName, generator, namesArg)
            }

            is PyListLiteralExpression -> {
                addStringToSequence(namesArg, newName, generator, namesArg)
            }
        }
    }

    private fun addStringToSequence(
        sequence: PsiElement,
        newName: String,
        generator: PyElementGenerator,
        replaceTarget: PsiElement
    ) {
        val elements = when (sequence) {
            is PyTupleExpression -> sequence.elements
            is PyListLiteralExpression -> sequence.elements
            else -> return
        }
        val existingTexts = elements.map { it.text }
        val allTexts = existingTexts + "\"$newName\""
        val joined = allTexts.joinToString(", ")
        val newText = when (replaceTarget) {
            is PyParenthesizedExpression -> "($joined)"
            is PyListLiteralExpression -> "[$joined]"
            else -> "($joined)"
        }
        replaceTarget.replace(createExpression(newText, replaceTarget, generator))
    }

    fun removeNameFromArgnames(
        namesArg: PyExpression,
        removeIndex: Int,
        existingNames: List<String>,
        generator: PyElementGenerator
    ) {
        val remaining = existingNames.filterIndexed { i, _ -> i != removeIndex }
        when (namesArg) {
            is PyStringLiteralExpression -> {
                val newValue = remaining.joinToString(", ")
                val suffix = if (remaining.size == 1) "," else ""
                val newLiteral = generator.createStringLiteralAlreadyEscaped("\"$newValue$suffix\"")
                namesArg.replace(newLiteral)
            }

            is PyParenthesizedExpression -> {
                val trailingComma = if (remaining.size == 1) "," else ""
                val newText = "(" + remaining.joinToString(", ") { "\"$it\"" } + trailingComma + ")"
                namesArg.replace(createExpression(newText, namesArg, generator))
            }

            is PyTupleExpression -> {
                val trailingComma = if (remaining.size == 1) "," else ""
                val newText = "(" + remaining.joinToString(", ") { "\"$it\"" } + trailingComma + ")"
                namesArg.replace(createExpression(newText, namesArg, generator))
            }

            is PyListLiteralExpression -> {
                val newText = "[" + remaining.joinToString(", ") { "\"$it\"" } + "]"
                namesArg.replace(createExpression(newText, namesArg, generator))
            }
        }
    }

    fun addValueToEachEntry(
        valuesList: PyListLiteralExpression,
        currentParamCount: Int,
        generator: PyElementGenerator
    ) {
        for (entry in valuesList.elements) {
            when {
                entry is PyCallExpression && PytestParametrizeUtil.isPytestParamCall(entry) -> {
                    addValueToPytestParam(entry, generator)
                }

                entry is PyTupleExpression || entry is PyParenthesizedExpression -> {
                    val tuple =
                        if (entry is PyParenthesizedExpression) entry.containedExpression as? PyTupleExpression else entry as? PyTupleExpression
                    if (tuple != null) {
                        val elTexts = tuple.elements.map { it.text } + "None"
                        val newText = "(" + elTexts.joinToString(", ") + ")"
                        entry.replace(createExpression(newText, entry, generator))
                    }
                }

                currentParamCount == 1 -> {
                    val newText = "(${entry.text}, None)"
                    entry.replace(createExpression(newText, entry, generator))
                }
            }
        }
    }

    fun removeValueFromEachEntry(
        valuesList: PyListLiteralExpression,
        removeIndex: Int,
        currentParamCount: Int,
        generator: PyElementGenerator
    ) {
        val remainingCount = currentParamCount - 1
        for (entry in valuesList.elements) {
            when {
                entry is PyCallExpression && PytestParametrizeUtil.isPytestParamCall(entry) -> {
                    removeValueFromPytestParam(entry, removeIndex, generator)
                }

                entry is PyTupleExpression || entry is PyParenthesizedExpression -> {
                    val tuple =
                        if (entry is PyParenthesizedExpression) entry.containedExpression as? PyTupleExpression else entry as? PyTupleExpression
                    if (tuple != null) {
                        val remaining = tuple.elements.filterIndexed { i, _ -> i != removeIndex }
                        if (remainingCount == 1 && remaining.size == 1) {
                            val newText = "(" + remaining[0].text + ",)"
                            entry.replace(createExpression(newText, entry, generator))
                        } else {
                            val newText = "(" + remaining.joinToString(", ") { it.text } + ")"
                            entry.replace(createExpression(newText, entry, generator))
                        }
                    }
                }
            }
        }
    }

    private fun addValueToPytestParam(call: PyCallExpression, generator: PyElementGenerator) {
        val argList = call.argumentList ?: return
        val positional = argList.arguments.filter { it !is PyKeywordArgument }
        val keywords = argList.arguments.filterIsInstance<PyKeywordArgument>()

        val posTexts = positional.map { it.text } + "None"
        val kwTexts = keywords.map { it.text }
        val allTexts = posTexts + kwTexts
        val calleeText = call.callee?.text ?: "pytest.param"
        val newText = "$calleeText(${allTexts.joinToString(", ")})"
        call.replace(createExpression(newText, call, generator))
    }

    private fun removeValueFromPytestParam(call: PyCallExpression, removeIndex: Int, generator: PyElementGenerator) {
        val argList = call.argumentList ?: return
        val positional = argList.arguments.filter { it !is PyKeywordArgument }
        val keywords = argList.arguments.filterIsInstance<PyKeywordArgument>()

        val remaining = positional.filterIndexed { i, _ -> i != removeIndex }
        val posTexts = remaining.map { it.text }
        val kwTexts = keywords.map { it.text }
        val allTexts = posTexts + kwTexts
        val calleeText = call.callee?.text ?: "pytest.param"
        val newText = "$calleeText(${allTexts.joinToString(", ")})"
        call.replace(createExpression(newText, call, generator))
    }

    fun addFunctionParameter(function: PyFunction, paramName: String, generator: PyElementGenerator) {
        val paramList = function.parameterList
        val newParam = generator.createParameter(paramName)
        paramList.addParameter(newParam)
    }

    fun removeFunctionParameter(function: PyFunction, paramName: String) {
        val param = function.parameterList.findParameterByName(paramName) ?: return
        param.delete()
    }

    fun findCaretParamIndex(element: PsiElement, namesArg: PyExpression, names: List<String>, caretOffset: Int): Int {
        when (namesArg) {
            is PyStringLiteralExpression -> {
                val stringStart = namesArg.textRange.startOffset
                val text = namesArg.text
                val valueRange = namesArg.stringValueTextRange
                var searchFrom = valueRange.startOffset

                for ((index, name) in names.withIndex()) {
                    val nameOffset = text.indexOf(name, searchFrom)
                    if (nameOffset < 0) continue
                    val absStart = stringStart + nameOffset
                    val absEnd = absStart + name.length
                    if (caretOffset in absStart..absEnd) return index
                    searchFrom = nameOffset + name.length
                }
            }

            is PyParenthesizedExpression, is PyTupleExpression, is PyListLiteralExpression -> {
                val elements = when (namesArg) {
                    is PyParenthesizedExpression -> (namesArg.containedExpression as? PyTupleExpression)?.elements
                    is PyTupleExpression -> namesArg.elements
                    is PyListLiteralExpression -> namesArg.elements
                    else -> null
                } ?: return -1

                for ((index, el) in elements.withIndex()) {
                    if (el is PyStringLiteralExpression) {
                        val range = el.textRange
                        if (caretOffset in range.startOffset..range.endOffset) return index
                    }
                }
            }
        }
        return if (namesArg.textRange.startOffset <= caretOffset && caretOffset <= namesArg.textRange.endOffset) 0 else -1
    }
}
