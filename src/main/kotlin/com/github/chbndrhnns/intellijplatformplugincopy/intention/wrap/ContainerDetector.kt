package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

/**
 * Unified utility for detecting container-related PSI patterns.
 * 
 * This consolidates the container detection logic that was previously spread across
 * `ContainerStrategies.kt` and `PyWrapHeuristics.kt` into a single coherent API.
 */
object ContainerDetector {

    /**
     * Container type names used for detection and suppression.
     */
    val CONTAINER_NAMES = setOf("list", "set", "tuple", "dict")

    /**
     * Checks if the element is a container literal (list, set, tuple, dict literal).
     * Does NOT include comprehensions or generator expressions.
     */
    fun isContainerLiteral(element: PsiElement): Boolean = when (element) {
        is PyListLiteralExpression,
        is PyTupleExpression,
        is PySetLiteralExpression,
        is PyDictLiteralExpression -> true

        else -> false
    }

    /**
     * Checks if the element is a comprehension expression (list, set, dict comprehension).
     */
    fun isComprehension(element: PsiElement): Boolean = when (element) {
        is PyListCompExpression,
        is PySetCompExpression,
        is PyDictCompExpression -> true

        else -> false
    }

    /**
     * Checks if the element is a generator expression.
     */
    fun isGeneratorExpression(element: PsiElement): Boolean = element is PyGeneratorExpression

    /**
     * Checks if the element is inside a container literal or comprehension.
     * Used to determine if we're at ELEMENT_LEVEL context.
     */
    fun isInsideContainerLiteralOrComprehension(expr: PyExpression): Boolean {
        val parent = expr.parent
        return isContainerLiteral(parent) || isComprehension(parent)
    }

    /**
     * Checks if the element is a container expression that should be wrapped with
     * a constructor call (e.g., `list(expr)`) rather than literal syntax (e.g., `[expr]`).
     * 
     * This includes:
     * - Container literals (list, tuple, set, dict)
     * - Comprehensions (list, set, dict)
     * - Generator expressions
     * - Common iterable factory calls (set(), tuple(), dict(), range())
     */
    fun isContainerExpression(expr: PyExpression): Boolean = when (expr) {
        is PyListLiteralExpression,
        is PyTupleExpression,
        is PySetLiteralExpression,
        is PyDictLiteralExpression,
        is PyListCompExpression,
        is PySetCompExpression,
        is PyDictCompExpression,
        is PyGeneratorExpression -> true

        is PyCallExpression -> {
            val calleeName = (expr.callee as? PyReferenceExpression)?.name
            // Treat common iterable/container factories as containers to avoid nesting
            // e.g., prefer list(set()) over [set()] and list(range(...)) over [range(...)]
            calleeName in setOf("set", "tuple", "dict", "range")
        }

        else -> false
    }

    /**
     * Checks if the element is the same type of container literal as the expected container.
     * Used to avoid suggesting wrapping a list literal with list() when it's already a list.
     */
    fun isSameContainerLiteral(element: PyExpression, expectedContainerName: String): Boolean {
        return when (expectedContainerName.lowercase()) {
            "list" -> element is PyListLiteralExpression || element is PyListCompExpression
            "set" -> element is PySetLiteralExpression || element is PySetCompExpression
            "dict" -> element is PyDictLiteralExpression || element is PyDictCompExpression
            "tuple" -> element is PyTupleExpression
            else -> false
        }
    }

    /**
     * Checks if the parent of the element matches the expected container type.
     * Used to determine if we're already inside the correct container.
     */
    fun parentMatchesExpectedContainer(element: PyExpression, expectedContainerName: String): Boolean {
        val parent = element.parent
        return when (expectedContainerName.lowercase()) {
            "list" -> parent is PyListLiteralExpression || parent is PyListCompExpression
            "set" -> parent is PySetLiteralExpression || parent is PySetCompExpression
            "tuple" -> parent is PyTupleExpression
            "dict" -> parent is PyDictLiteralExpression || parent is PyDictCompExpression
            else -> false
        }
    }

    /**
     * Checks if the container constructor should be suppressed for the given element.
     * Returns true if the element is inside a container literal/comprehension and
     * the constructor name is a container type.
     */
    fun shouldSuppressContainerCtor(element: PyExpression, ctorName: String): Boolean {
        if (ctorName.lowercase() !in CONTAINER_NAMES) return false
        return isInsideContainerLiteralOrComprehension(element)
    }

    /**
     * Maps abstract container type names to their concrete implementations.
     * For example, `Sequence` -> `list`, `Mapping` -> `dict`.
     * 
     * Returns null if the name is not a recognized container type.
     */
    fun mapToConcrete(name: String): String? = when (name.lowercase()) {
        "list", "sequence", "collection", "iterable", "mutablesequence", "generator", "iterator" -> "list"
        "set" -> "set"
        "tuple" -> "tuple"
        "dict", "mapping", "mutablemapping" -> "dict"
        else -> null
    }
}
