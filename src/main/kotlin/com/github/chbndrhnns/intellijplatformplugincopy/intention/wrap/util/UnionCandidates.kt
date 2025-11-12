package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache

/**
 * Utility that extracts constructor candidates from union-like annotations.
 *
 * Supported forms:
 * - PEP 604 style: `A | B | C`
 * - typing.Union[A, B, C]
 * - typing.Optional[A]  (treated as Union[A, NoneType] but NoneType is ignored as builtin)
 *
 * Returns a list of distinct (name, resolved element) pairs for non-builtin symbols only,
 * and only when at least two such candidates can be determined.
 */
object UnionCandidates {
    fun collect(annotation: PyTypedElement, anchor: PyExpression): List<ExpectedCtor> {
        val expr = annotation as? PyExpression ?: return emptyList()
        val out = LinkedHashSet<ExpectedCtor>()

        fun String.equalsAnyIgnoreCase(vararg options: String): Boolean =
            options.any { this.equals(it, ignoreCase = true) }

        fun addRef(ref: PyReferenceExpression) {
            val name = ref.name ?: return
            val resolved = ref.reference.resolve() as? PsiNamedElement
            out += ExpectedCtor(name, resolved)
        }

        fun visit(e: PyExpression) {
            when (e) {
                is PyBinaryExpression -> {
                    if (e.operator == PyTokenTypes.OR) {
                        e.leftExpression?.let { visit(it) }
                        e.rightExpression?.let { visit(it) }
                        return
                    }
                }

                is PySubscriptionExpression -> {
                    val calleeName = (e.operand as? PyReferenceExpression)?.name
                    if (calleeName?.equalsAnyIgnoreCase("Union", "Optional") == true) {
                        e.indexExpression?.let { idx ->
                            when (idx) {
                                is PyTupleExpression -> idx.elements.forEach { el ->
                                    (el as? PyReferenceExpression)?.let { addRef(it) }
                                }

                                is PyReferenceExpression -> addRef(idx)
                            }
                        }
                        return
                    }
                }
            }

            (e as? PyReferenceExpression)?.let { addRef(it) }
        }

        // Tolerate redundant parentheses around unions: ((A | B))
        var root: PyExpression = expr
        while (root is PyParenthesizedExpression) {
            val inner = root.containedExpression ?: break
            root = inner
        }

        visit(root)

        // Deduplicate by simple name
        val distinct = out.toList().distinctBy { it.name }

        // Filter out builtins and unresolved; require at least two non-builtin candidates
        val builtins = PyBuiltinCache.getInstance(anchor)
        val nonBuiltin = distinct.filter { it.symbol != null && !builtins.isBuiltin(it.symbol) }
        return if (nonBuiltin.size >= 2 && nonBuiltin.size == distinct.size) nonBuiltin else emptyList()
    }
}
