package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.Single
import com.github.chbndrhnns.intellijplatformplugincopy.intention.UnionChoice
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.CtorMatch
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.PyTypeIntentions
import com.jetbrains.python.psi.types.PyClassType

/**
 * Strategy for handling union types - offers wrapping with the best matching union member.
 */
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

/**
 * Strategy for generic constructor wrapping based on expected type.
 */
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
