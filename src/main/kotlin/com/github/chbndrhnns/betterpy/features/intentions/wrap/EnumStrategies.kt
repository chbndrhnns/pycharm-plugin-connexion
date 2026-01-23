package com.github.chbndrhnns.betterpy.features.intentions.wrap

import com.github.chbndrhnns.betterpy.features.intentions.ReplaceWithVariant
import com.github.chbndrhnns.betterpy.features.intentions.Single
import com.github.chbndrhnns.betterpy.features.intentions.shared.CtorMatch
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedTypeInfo
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyTypeIntentions
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Strategy for replacing literal values with enum variants when the expected type is an enum.
 */
class EnumStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val expectedTypeInfo =
            ExpectedTypeInfo.getExpectedTypeInfo(context.element, context.typeEval) ?: return StrategyResult.Continue
        val expectedType = expectedTypeInfo.type as? PyClassType ?: return StrategyResult.Continue
        val pyClass = expectedType.pyClass

        if (!isEnum(pyClass, context.typeEval)) {
            return StrategyResult.Continue
        }

        val literalValue = when (val element = context.element) {
            is PyStringLiteralExpression -> element.stringValue
            is PyNumericLiteralExpression -> element.longValue?.toString()
            else -> null
        } ?: return StrategyResult.Continue

        var matchedVariant: String? = null
        var matchedElement: PsiNamedElement? = null

        for (attr in pyClass.classAttributes) {
            val value = attr.findAssignedValue()
            if (value is PyStringLiteralExpression && value.stringValue == literalValue) {
                matchedVariant = attr.name
                matchedElement = attr
                break
            }
            if (value is PyNumericLiteralExpression && value.longValue?.toString() == literalValue) {
                matchedVariant = attr.name
                matchedElement = attr
                break
            }
        }

        if (matchedVariant != null) {
            val variantName = "${pyClass.name}.${matchedVariant}"
            return StrategyResult.Found(ReplaceWithVariant(context.element, variantName, matchedElement))
        }

        return StrategyResult.Skip("Enum variant not found")
    }

    private fun isEnum(pyClass: PyClass, context: TypeEvalContext): Boolean {
        if (pyClass.qualifiedName == "enum.Enum") return true
        return pyClass.getAncestorClasses(context).any { it.qualifiedName == "enum.Enum" }
    }
}

/**
 * Strategy for wrapping enum member definition values with the mixin type.
 * For example, in `class Status(str, Enum)`, string values should be wrapped with `str()`.
 */
class EnumMemberDefinitionStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val element = context.element
        val parent = element.parent as? PyAssignmentStatement ?: return StrategyResult.Continue

        // Ensure we are assigning to a target, and the element is the value
        if (parent.assignedValue != element) return StrategyResult.Continue

        // Ensure assignment is at class level (not in a function)
        if (PsiTreeUtil.getParentOfType(parent, PyFunction::class.java) != null) {
            return StrategyResult.Continue
        }

        // Check if we are inside a class
        val pyClass = PsiTreeUtil.getParentOfType(parent, PyClass::class.java) ?: return StrategyResult.Continue

        val typeEval = context.typeEval

        val mixinType = resolveEnumMixinType(pyClass, typeEval) ?: return StrategyResult.Continue
        val typeName = mixinType.name ?: return StrategyResult.Continue

        if (!elementMatchesType(element, typeName, typeEval)) {
            if (PyWrapHeuristics.isAlreadyWrappedWith(element, typeName, mixinType)) {
                return StrategyResult.Skip("Already wrapped with $typeName")
            }
            return StrategyResult.Found(Single(element, typeName, mixinType))
        }

        return StrategyResult.Continue
    }

    private fun resolveEnumMixinType(pyClass: PyClass, context: TypeEvalContext): PyClass? {
        if (!isEnum(pyClass, context)) return null

        // Iterate over ancestors to find the first one that is not Enum and not object
        // This identifies the "data type" mixed into the Enum
        val ancestors = pyClass.getAncestorClasses(context)
        for (cls in ancestors) {
            if (cls.name == "object") continue
            if (isEnum(cls, context)) continue

            // Found non-Enum ancestor.
            return cls
        }
        return null
    }

    private fun isEnum(pyClass: PyClass, context: TypeEvalContext): Boolean {
        val qName = pyClass.qualifiedName
        if (qName == "enum.Enum") return true
        return pyClass.getAncestorClasses(context).any { it.qualifiedName == "enum.Enum" }
    }

    private fun elementMatchesType(element: PyExpression, typeName: String, context: TypeEvalContext): Boolean {
        return PyTypeIntentions.elementDisplaysAsCtor(element, typeName, context) == CtorMatch.MATCHES
    }
}
