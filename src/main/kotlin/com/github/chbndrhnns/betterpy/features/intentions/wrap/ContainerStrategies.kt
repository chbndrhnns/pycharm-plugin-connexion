package com.github.chbndrhnns.betterpy.features.intentions.wrap

import com.github.chbndrhnns.betterpy.features.intentions.Elementwise
import com.github.chbndrhnns.betterpy.features.intentions.ElementwiseUnionChoice
import com.github.chbndrhnns.betterpy.features.intentions.Single
import com.github.chbndrhnns.betterpy.features.intentions.shared.CtorMatch
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedCtor
import com.github.chbndrhnns.betterpy.features.intentions.shared.ExpectedTypeInfo
import com.github.chbndrhnns.betterpy.features.intentions.shared.PyTypeIntentions
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyKeyValueExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Strategy for wrapping with outer container types (list, set, tuple).
 */
class OuterContainerStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val outerCtor = resolveExpectedOuterContainerCtor(context.element, context.typeEval)
            ?: return StrategyResult.Continue

        // Dict outer container wrapping is not supported, but allow other strategies to handle inner elements
        if (outerCtor.name == "dict") return StrategyResult.Continue

        // Check if we are already inside a container that matches the expectation
        if (PyWrapHeuristics.parentMatchesExpectedContainer(context.element, outerCtor.name)) {
            // The outer container is already there. Proceed to check items or other strategies.
            return StrategyResult.Continue
        }

        val element = context.element

        // Check if the element is already the same type of container as expected
        if (ContainerDetector.isSameContainerLiteral(element, outerCtor.name)) {
            return StrategyResult.Continue
        }

        val isContainerLiteral = ContainerDetector.isContainerLiteral(element) ||
                ContainerDetector.isComprehension(element)

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

        val concrete = ContainerDetector.mapToConcrete(baseName) ?: return null
        return ExpectedCtor(concrete, null)
    }
}

/**
 * Strategy for wrapping container items with expected type.
 */
class ContainerItemStrategy : WrapStrategy {
    override fun run(context: AnalysisContext): StrategyResult {
        val element = context.element
        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(context.editor, element)
        
        if (containerItemTarget == null) {
            // If we are falling back to the element itself, verify it is a direct item of a container.
            // Deeply nested elements (e.g. arguments in a call inside a list) should not be treated as list items.
            if (!isDirectContainerItem(element)) {
                // Fix for cases where nested elements happen to match the container type (e.g. list[int] -> CustomInt(1)).
                // In those cases, we want to SKIP to suppress subsequent strategies, preserving legacy behavior.
                // But if they differ (e.g. list[Outer] -> Inner(id=1)), we want to CONTINUE to avoid forcing 'Outer' on '1'.
                val ctor = PyTypeIntentions.tryContainerItemCtor(element, context.typeEval)
                if (ctor != null && 
                    PyTypeIntentions.elementDisplaysAsCtor(element, ctor.name, context.typeEval) == CtorMatch.MATCHES) {
                    return StrategyResult.Skip("Nested element matches container item type")
                }
                return StrategyResult.Continue
            }
        }
        
        val target = containerItemTarget ?: element

        val ctor = PyTypeIntentions.tryContainerItemCtor(target, context.typeEval)
            ?: return StrategyResult.Continue

        val suppressedContainers = setOf("list", "set", "tuple", "dict")
        if (suppressedContainers.contains(ctor.name.lowercase())) return StrategyResult.Continue

        if (PyTypeIntentions.elementDisplaysAsCtor(
                target,
                ctor.name,
                context.typeEval
            ) == CtorMatch.MATCHES
        ) {
            return StrategyResult.Skip("Element already matches")
        }

        if (PyWrapHeuristics.isAlreadyWrappedWith(target, ctor.name, ctor.symbol)) {
            return StrategyResult.Skip("Already wrapped")
        }

        if (PyWrapHeuristics.isProtocol(ctor.symbol, context.typeEval)) {
            return StrategyResult.Skip("Expected type is a Protocol")
        }

        if (UnwrapStrategy.unwrapYieldsExpectedCtor(target, ctor.name, context.typeEval)) {
            return StrategyResult.Skip("Unwrap yields expected ctor")
        }

        return StrategyResult.Found(Single(target, ctor.name, ctor.symbol))
    }

    private fun isDirectContainerItem(element: PyExpression): Boolean {
        val parent = element.parent
        if (ContainerDetector.isContainerLiteral(parent) || ContainerDetector.isComprehension(parent)) return true

        // Handle dict {k: v} where parent is KeyValue
        if (parent is PyKeyValueExpression) {
            val grand = parent.parent
            if (ContainerDetector.isContainerLiteral(grand) || ContainerDetector.isComprehension(grand)) return true
        }
        return false
    }
}
