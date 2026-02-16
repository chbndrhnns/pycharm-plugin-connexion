package com.github.chbndrhnns.betterpy.features.intentions.movescope

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter

object MoveScopeTextBuilder {
    const val DEFAULT_INDENT = 4

    enum class MethodStyle {
        FORCE_INSTANCE,
        FORCE_STATIC,
        INSTANCE_IF_FIRST_PARAM_TYPED
    }

    fun buildMethodText(function: PyFunction, targetClass: PyClass, methodStyle: MethodStyle): String {
        val targetIndent = classBodyIndent(targetClass)
        return buildMethodText(function, targetIndent, methodStyle, targetClass.name)
    }

    fun buildMethodText(
        function: PyFunction,
        targetIndent: Int,
        methodStyle: MethodStyle,
        targetClassName: String? = null
    ): String {
        val fileText = function.containingFile.text
        val startElement = function.decoratorList ?: function
        val startOffset = startElement.textRange.startOffset
        val endOffset = function.textRange.endOffset

        val reindented = reindentRange(fileText, startOffset, endOffset, targetIndent)
        val isInstance = isInstanceMethod(function, targetClassName, methodStyle)
        return if (isInstance) {
            when (methodStyle) {
                MethodStyle.FORCE_INSTANCE -> ensureSelfFirstParam(reindented, replaceFirstParam = false)
                MethodStyle.INSTANCE_IF_FIRST_PARAM_TYPED -> ensureSelfFirstParam(reindented, replaceFirstParam = true)
                MethodStyle.FORCE_STATIC -> reindented
            }
        } else {
            addStaticDecorator(reindented, targetIndent)
        }
    }

    fun buildClassWithMethod(function: PyFunction, className: String, methodStyle: MethodStyle): String {
        val methodText = buildMethodText(function, DEFAULT_INDENT, methodStyle, className)
        return "class $className:\n$methodText"
    }

    fun canInsertMethodIntoClass(methodName: String, targetClass: PyClass): Boolean {
        if (targetClass.findMethodByName(methodName, true, null) != null) return false
        return targetClass.statementList.statements.none { stmt ->
            stmt is PyClass && stmt.name == methodName
        }
    }

    fun classBodyIndent(targetClass: PyClass): Int {
        val fileText = targetClass.containingFile.text
        val classBodyStart = targetClass.statementList.textRange.startOffset
        val classBodyLineStart = fileText.lastIndexOf('\n', classBodyStart - 1) + 1
        return classBodyStart - classBodyLineStart
    }

    fun elementLineIndent(element: PsiElement): Int {
        val fileText = element.containingFile.text
        val startOffset = element.textRange.startOffset
        val lineStart = fileText.lastIndexOf('\n', startOffset - 1) + 1
        return startOffset - lineStart
    }

    fun reindentRange(text: String, startOffset: Int, endOffset: Int, targetIndent: Int): String {
        val lineStart = text.lastIndexOf('\n', startOffset - 1) + 1
        val currentIndent = startOffset - lineStart
        val rawText = text.substring(lineStart, endOffset)
        val lines = rawText.lines()
        return lines.joinToString("\n") { line ->
            if (line.isBlank()) {
                ""
            } else {
                val stripped = if (line.length >= currentIndent) line.drop(currentIndent) else line.trimStart()
                " ".repeat(targetIndent) + stripped
            }
        }
    }

    fun isInstanceMethod(function: PyFunction, className: String?, methodStyle: MethodStyle): Boolean {
        return when (methodStyle) {
            MethodStyle.FORCE_INSTANCE -> true
            MethodStyle.FORCE_STATIC -> false
            MethodStyle.INSTANCE_IF_FIRST_PARAM_TYPED -> {
                val targetName = className ?: return false
                isFirstParamTypedAsClass(function, targetName)
            }
        }
    }

    private fun addStaticDecorator(funcText: String, targetIndent: Int): String {
        val indent = " ".repeat(targetIndent)
        return indent + "@staticmethod\n" + funcText
    }

    private fun ensureSelfFirstParam(funcText: String, replaceFirstParam: Boolean): String {
        val regex = Regex(
            """^(\s*(?:async\s+)?def\s+\w+\s*\()([^)]*)\)""",
            RegexOption.MULTILINE
        )
        val defMatch = regex.find(funcText) ?: return funcText
        val prefix = defMatch.groupValues[1]
        val params = defMatch.groupValues[2]

        val paramList = params.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (paramList.isEmpty()) {
            return if (replaceFirstParam) funcText else funcText.replaceRange(defMatch.range, "${prefix}self)")
        }

        val newParams = if (replaceFirstParam) {
            paramList[0] = "self"
            paramList
        } else {
            if (paramList[0] == "self" || paramList[0].startsWith("self:")) {
                paramList
            } else {
                listOf("self") + paramList
            }
        }

        return funcText.replaceRange(defMatch.range, "$prefix${newParams.joinToString(", ")})")
    }

    private fun isFirstParamTypedAsClass(function: PyFunction, className: String): Boolean {
        val params = function.parameterList.parameters
        if (params.isEmpty()) return false
        val firstParam = params[0] as? PyNamedParameter ?: return false
        val annotationText = firstParam.annotation?.value?.text ?: return false
        return annotationText == className
    }
}
