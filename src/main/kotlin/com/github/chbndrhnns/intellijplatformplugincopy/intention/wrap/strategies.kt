package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.*
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedTypeInfo
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.TypeEvalContext

interface WrapStrategy {
    fun run(context: AnalysisContext): StrategyResult
}

class OuterContainerStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val outerCtor = resolveExpectedOuterContainerCtor(context.element, context.typeEval)
            ?: return StrategyResult.Continue

        if (outerCtor.name == "dict") return StrategyResult.Skip("Dict not supported")

        // Check if we are already inside a container that matches the expectation
        if (PyWrapHeuristics.parentMatchesExpectedContainer(context.element, outerCtor.name)) {
            // The outer container is already there. Proceed to check items or other strategies.
            return StrategyResult.Continue
        }

        val element = context.element
        val isSameContainerLiteral = when (outerCtor.name.lowercase()) {
            "list" -> element is PyListLiteralExpression || element is PyListCompExpression
            "set" -> element is PySetLiteralExpression || element is PySetCompExpression
            "dict" -> element is PyDictLiteralExpression || element is PyDictCompExpression
            "tuple" -> element is PyTupleExpression
            else -> false
        }

        if (isSameContainerLiteral) {
            return StrategyResult.Continue
        }

        val isContainerLiteral = PyWrapHeuristics.isContainerLiteral(element) ||
                element is PyListCompExpression || element is PySetCompExpression || element is PyDictCompExpression

        if (!isContainerLiteral) {
            // Check if the variable itself matches the outer container type
            val match = PyTypeIntentions.elementDisplaysAsCtor(element, outerCtor.name, context.typeEval)
            val names = PyTypeIntentions.computeDisplayTypeNames(element, context.typeEval)
            val actualUsesSameOuter = names.actual?.lowercase()?.startsWith(outerCtor.name.lowercase()) == true

            if (match == CtorMatch.MATCHES || actualUsesSameOuter) {
                // Outer matches. Check for elementwise issues.
                val candidates = PyWrapHeuristics.expectedItemCtorsForContainer(element, context.typeEval)
                if (candidates.isNotEmpty()) {
                    if (candidates.size >= 2) {
                        return StrategyResult.Found(ElementwiseUnionChoice(element, outerCtor.name, candidates))
                    }
                    val only = candidates.first()
                    if (!PyWrapHeuristics.isAlreadyWrappedWith(element, only.name, only.symbol)) {
                        return StrategyResult.Found(Elementwise(element, outerCtor.name, only.name, only.symbol))
                    }
                }
            }
        }

        // Fallback: wrap with outer container
        val differs =
            PyTypeIntentions.elementDisplaysAsCtor(element, outerCtor.name, context.typeEval) == CtorMatch.DIFFERS
        val alreadyWrapped = PyWrapHeuristics.isAlreadyWrappedWith(element, outerCtor.name, outerCtor.symbol)

        if (differs && !alreadyWrapped) {
            val target = (element.parent as? PyExpression) ?: element
            if (UnwrapStrategy.unwrapYieldsExpectedCtor(target, outerCtor.name, context.typeEval)) {
                return StrategyResult.Skip("Unwrap yields expected ctor")
            }
            return StrategyResult.Found(Single(target, outerCtor.name, outerCtor.symbol))
        }

        return StrategyResult.Continue
    }

    private fun resolveExpectedOuterContainerCtor(expr: PyExpression, ctx: TypeEvalContext): ExpectedCtor? {
        val info = ExpectedTypeInfo.getExpectedTypeInfo(expr, ctx) ?: return null
        val annExpr = info.annotationExpr as? PyExpression ?: return null

        val baseName = when (val sub = annExpr as? PySubscriptionExpression) {
            null -> (annExpr as? PyReferenceExpression)?.name?.lowercase()
            else -> (sub.operand as? PyReferenceExpression)?.name?.lowercase()
        } ?: return null

        fun mapToConcrete(name: String): String? = when (name) {
            "list", "sequence", "collection", "iterable", "mutablesequence", "generator", "iterator" -> "list"
            "set" -> "set"
            "tuple" -> "tuple"
            "dict", "mapping", "mutablemapping" -> "dict"
            else -> null
        }

        val concrete = mapToConcrete(baseName) ?: return null
        return ExpectedCtor(concrete, null)
    }
}

class ContainerItemStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val element = context.element
        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(context.editor, element) ?: element

        val ctor = PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context.typeEval)
            ?: return StrategyResult.Continue

        val suppressedContainers = setOf("list", "set", "tuple", "dict")
        if (suppressedContainers.contains(ctor.name.lowercase())) return StrategyResult.Continue

        if (PyTypeIntentions.elementDisplaysAsCtor(
                containerItemTarget,
                ctor.name,
                context.typeEval
            ) == CtorMatch.MATCHES
        ) {
            return StrategyResult.Skip("Element already matches")
        }

        if (PyWrapHeuristics.isAlreadyWrappedWith(containerItemTarget, ctor.name, ctor.symbol)) {
            return StrategyResult.Skip("Already wrapped")
        }

        if (UnwrapStrategy.unwrapYieldsExpectedCtor(containerItemTarget, ctor.name, context.typeEval)) {
            return StrategyResult.Skip("Unwrap yields expected ctor")
        }

        return StrategyResult.Found(Single(containerItemTarget, ctor.name, ctor.symbol))
    }
}

class UnionStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val element = context.element

        val names = PyTypeIntentions.computeDisplayTypeNames(element, context.typeEval)
        val annElement = names.expectedAnnotationElement

        val unionCtors = annElement?.let { UnionCandidates.collect(it, element) } ?: emptyList()

        if (unionCtors.size < 2) {
            return StrategyResult.Continue
        }


        // Bucketize candidates to prefer OWN > THIRDPARTY > STDLIB > BUILTIN.
        data class BucketedCtor(val bucket: TypeBucket, val ctor: ExpectedCtor)

        val bucketed = unionCtors.mapNotNull { ctor ->
            val bucket = TypeBucketClassifier.bucketFor(ctor.symbol, element) ?: return@mapNotNull null
            BucketedCtor(bucket, ctor)
        }
        if (bucketed.isEmpty()) {
            val anyMatches = unionCtors.any {
                PyWrapHeuristics.elementMatchesCtor(element, it, context.typeEval)
            }
            if (anyMatches) return StrategyResult.Skip("Matches unresolved union member")
            return StrategyResult.Continue
        }

        val byBucket = bucketed.groupBy { it.bucket }
        val bestBucket = byBucket.keys.maxByOrNull { it.priority } ?: return StrategyResult.Continue
        val bestBucketCtors = byBucket.getValue(bestBucket).map { it.ctor }

        // If the actual expression is already of the highest available bucket,
        // do not suggest wrapping.
        val actualTypeBucket = (context.typeEval.getType(element) as? PyClassType)
            ?.pyClass
            ?.let { TypeBucketClassifier.bucketFor(it, element) }

        if (actualTypeBucket != null && actualTypeBucket == bestBucket) {
            return StrategyResult.Skip("Actual type already in best bucket")
        }

        val differing = bestBucketCtors.filter {
            !PyWrapHeuristics.elementMatchesCtor(element, it, context.typeEval)
        }

        val wrappedWithAny = differing.any {
            PyWrapHeuristics.isAlreadyWrappedWith(element, it.name, it.symbol)
        }
        if (wrappedWithAny) return StrategyResult.Skip("Already wrapped with one union member")

        return when (differing.size) {
            0 -> StrategyResult.Skip("No differing candidates")
            1 -> {
                val only = differing.first()
                StrategyResult.Found(Single(element, only.name, only.symbol))
            }

            else -> StrategyResult.Found(UnionChoice(element, differing))
        }
    }
}

class GenericCtorStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val ctor =
            PyWrapHeuristics.expectedCtorName(context.element, context.typeEval) ?: return StrategyResult.Continue

        if (PyWrapHeuristics.shouldSuppressContainerCtor(context.element, ctor)) {
            return StrategyResult.Skip("Suppressed container ctor")
        }

        val names = PyTypeIntentions.computeDisplayTypeNames(context.element, context.typeEval)
        val ctorElem = names.expectedElement

        // Verify mismatch
        if (PyTypeIntentions.elementDisplaysAsCtor(context.element, ctor, context.typeEval) == CtorMatch.MATCHES) {
            return StrategyResult.Skip("Already matches")
        }

        // Additional check for unknown/same types to mimic legacy logic
        if (names.actual == names.expected && names.actual != "Unknown") {
            return StrategyResult.Skip("Types match")
        }

        if (PyWrapHeuristics.isAlreadyWrappedWith(context.element, ctor, ctorElem)) {
            return StrategyResult.Skip("Already wrapped")
        }

        return StrategyResult.Found(Single(context.element, ctor, ctorElem))
    }
}

object UnwrapStrategy {
    fun unwrapYieldsExpectedCtor(element: PyExpression, expectedCtorName: String, context: TypeEvalContext): Boolean {
        val wrapperInfo = PyWrapHeuristics.getWrapperCallInfo(element) ?: return false
        if (wrapperInfo.name.lowercase() in PyWrapHeuristics.CONTAINERS) return false

        val match = PyTypeIntentions.elementDisplaysAsCtor(wrapperInfo.inner, expectedCtorName, context)
        if (match != CtorMatch.MATCHES) return false

        val type = context.getType(element)
        if (type is PyClassType) {
            val cls = type.pyClass
            if (cls.name == expectedCtorName) return true
            val ancestors = cls.getAncestorClasses(context)
            if (ancestors.any { it.name == expectedCtorName }) return true
        }

        return true
    }
}

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
