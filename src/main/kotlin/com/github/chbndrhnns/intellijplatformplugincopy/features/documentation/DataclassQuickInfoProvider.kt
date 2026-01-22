package com.github.chbndrhnns.intellijplatformplugincopy.features.documentation

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.documentation.PythonDocumentationQuickInfoProvider
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.TypeEvalContext

class DataclassQuickInfoProvider : AbstractDocumentationProvider(), PythonDocumentationQuickInfoProvider {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val enabled = PluginSettingsState.instance().state.enableEnhancedDataclassQuickInfo
        if (!enabled) return null

        val pyClass = element as? PyClass ?: return null

        val context = TypeEvalContext.userInitiated(pyClass.project, pyClass.containingFile)
        val dataclassParams = parseDataclassParameters(pyClass, context)
        val isPydantic = if (dataclassParams == null) isPydanticModel(pyClass, context) else false

        if (dataclassParams == null && !isPydantic) return null

        // Use standard provider for the base doc (signature + docstring)
        val provider = PythonDocumentationProvider()
        val standardDoc = provider.generateDoc(element, originalElement)

        val fieldsInfo = buildFieldsInfo(pyClass, dataclassParams, context, isPydantic)

        if (standardDoc == null) {
            return fieldsInfo
        }

        // Inject fieldsInfo into standardDoc
        // Standard doc structure: ... <div class="definition">...</div> ... <div class="content">...</div> ...
        // We want to insert after the definition block.

        // Try to find the insertion point before the content block
        val contentStartIndex = standardDoc.indexOf("<div class=\"content\">")
        if (contentStartIndex != -1) {
            return standardDoc.substring(0, contentStartIndex) + fieldsInfo + standardDoc.substring(contentStartIndex)
        }

        // Try to find end of definition block
        val definitionStartIndex = standardDoc.indexOf("class=\"definition\">")
        if (definitionStartIndex != -1) {
            val closeDiv = standardDoc.indexOf("</div>", definitionStartIndex)
            if (closeDiv != -1) {
                val insertPos = closeDiv + 6
                return standardDoc.substring(0, insertPos) + fieldsInfo + standardDoc.substring(insertPos)
            }
        }

        return standardDoc + fieldsInfo
    }

    override fun getQuickInfo(originalElement: PsiElement): String? {
        val enabled = PluginSettingsState.instance().state.enableEnhancedDataclassQuickInfo

        if (!enabled) return null

        // Get the referenced class
        val pyClass = when {
            originalElement is PyClass -> originalElement
            originalElement is PyReferenceExpression -> originalElement.reference.resolve() as? PyClass
            originalElement.parent is PyClass -> originalElement.parent as PyClass
            else -> null
        }

        if (pyClass == null) {
            return null
        }

        // Check if it's a dataclass or Pydantic model
        val context = TypeEvalContext.userInitiated(
            pyClass.project, pyClass.containingFile
        )

        val dataclassParams = parseDataclassParameters(pyClass, context)
        val isPydantic = if (dataclassParams == null) isPydanticModel(pyClass, context) else false

        if (dataclassParams == null && !isPydantic) {
            return null
        }

        // Build enhanced quick info (only fields, as signature is handled by standard provider)
        return buildFieldsInfo(pyClass, dataclassParams, context, isPydantic, true)
    }

    private fun isPydanticModel(cls: PyClass, context: TypeEvalContext): Boolean {
        return cls.getAncestorClasses(context).any { ancestor ->
            val qName = ancestor.qualifiedName
            qName == "pydantic.BaseModel" || qName == "pydantic.main.BaseModel"
        }
    }

    private fun buildFieldsInfo(
        pyClass: PyClass,
        params: PyDataclassParameters?,
        context: TypeEvalContext,
        isPydantic: Boolean,
        plainText: Boolean = false
    ): String {
        if (plainText) {
            val sb = StringBuilder()
            val fields = collectFieldsWithInheritance(pyClass, context)
            fields.forEachIndexed { index, (field, _) ->
                sb.append(field.name ?: "unknown")
                val fieldType = context.getType(field)
                if (fieldType != null) {
                    sb.append(": ")
                    sb.append(PythonDocumentationProvider.getTypeName(fieldType, context))
                }
                if (index < fields.size - 1) sb.append("\n")
            }
            return sb.toString()
        }

        val html = HtmlBuilder()

        // Add "Pydantic Model" label if needed
        if (isPydantic) {
            html.append(styledSpan("Pydantic Model", PyHighlighter.PY_DECORATOR))
            html.append(HtmlChunk.br())
        }

        // Add fields section
        html.append(HtmlChunk.br())
        html.append(styledSpan("Fields:", PyHighlighter.PY_KEYWORD))
        html.append(HtmlChunk.br())

        // Collect fields with inheritance
        val fields = collectFieldsWithInheritance(pyClass, context)

        if (fields.isEmpty()) {
            html.append("  (no fields)")
        } else {
            fields.forEach { (field, sourceClass) ->
                html.append("  ")

                // Field name
                html.append(styledSpan(field.name ?: "unknown", PyHighlighter.PY_PARAMETER))

                // Type annotation
                val fieldType = context.getType(field)
                if (fieldType != null) {
                    html.append(styledSpan(": ", PyHighlighter.PY_OPERATION_SIGN))
                    val typeName = PythonDocumentationProvider.getTypeName(fieldType, context)
                    html.append(styledSpan(typeName, PyHighlighter.PY_ANNOTATION))
                }

                // Default value
                val defaultValue = field.findAssignedValue()
                if (defaultValue != null) {
                    html.append(styledSpan(" = ", PyHighlighter.PY_OPERATION_SIGN))
                    val valueText = defaultValue.text
                    if (valueText.length > 30) {
                        html.append("...")
                    } else {
                        html.append(valueText)
                    }
                }

                // Show inheritance source
                if (sourceClass != pyClass) {
                    html.append(styledSpan(" (from ${sourceClass.name})", PyHighlighter.PY_LINE_COMMENT))
                }

                html.append(HtmlChunk.br())
            }
        }

        // Add dataclass parameters info
        if (params != null) {
            html.append(HtmlChunk.br())
            html.append(buildDataclassParamsInfo(params))
        }

        return html.toString()
    }

    private fun collectFieldsWithInheritance(
        pyClass: PyClass, context: TypeEvalContext
    ): List<Pair<PyTargetExpression, PyClass>> {
        val result = mutableListOf<Pair<PyTargetExpression, PyClass>>()
        val seenNames = mutableSetOf<String>()

        // Collect from current class and ancestors
        val classesToCheck = mutableListOf(pyClass)
        classesToCheck.addAll(pyClass.getAncestorClasses(context))

        for (cls in classesToCheck) {
            // Check if ancestor is also a dataclass or Pydantic model
            val ancestorParams = parseDataclassParameters(cls, context)
            val isPydantic = if (ancestorParams == null) isPydanticModel(cls, context) else false

            if (ancestorParams != null || isPydantic || cls == pyClass) {
                for (field in cls.classAttributes) {
                    val fieldName = field.name
                    if (fieldName != null && !seenNames.contains(fieldName)) {
                        result.add(field to cls)
                        seenNames.add(fieldName)
                    }
                }
            }
        }

        return result
    }

    private fun buildDataclassParamsInfo(
        params: PyDataclassParameters
    ): HtmlChunk {
        val html = HtmlBuilder()
        html.append(styledSpan("Dataclass options:", PyHighlighter.PY_KEYWORD))
        html.append(HtmlChunk.br())

        val options = listOf(
            "init" to params.init,
            "repr" to params.repr,
            "eq" to params.eq,
            "order" to params.order,
            "frozen" to params.frozen,
            "slots" to params.slots
        ).filter { it.second }

        if (options.isNotEmpty()) {
            html.append("  ${options.joinToString(", ") { it.first }}")
        }

        return html.toFragment()
    }

    private fun styledSpan(text: String, key: TextAttributesKey): HtmlChunk {
        return HtmlChunk.tag("span").child(HtmlChunk.text(text))
    }
}
