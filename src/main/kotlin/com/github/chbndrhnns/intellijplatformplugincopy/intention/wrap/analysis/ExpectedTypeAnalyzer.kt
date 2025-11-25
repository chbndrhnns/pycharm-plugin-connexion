package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.analysis

import com.github.chbndrhnns.intellijplatformplugincopy.intention.*
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.*
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.PyWrapHeuristics
import com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util.UnionCandidates
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext

class ExpectedTypeAnalyzer(private val project: Project) {

    fun analyzeAtCaret(editor: Editor, file: PsiFile): WrapPlan? {
        val context = TypeEvalContext.codeAnalysis(project, file)
        val elementAtCaret = PyTypeIntentions.findExpressionAtCaret(editor, file) ?: return null

        // Do not offer wrapping on the variable name being defined/assigned to
        if (elementAtCaret is PyTargetExpression) return null

        // fix outer-most container mismatch first (list/set/tuple/dict + common aliases).
        // Guard: do NOT trigger when the caret sits inside an already-correct container literal/comprehension.
        expectedOuterContainerCtor(elementAtCaret, context)?.let { outerCtor ->
            // Skip if the element itself is already a literal/comprehension of the same outer container
            // (e.g., list literal when list[...] is expected). We'll handle inner items instead.
            val isSameContainerLiteral = when (outerCtor.name.lowercase()) {
                "list" -> elementAtCaret is PyListLiteralExpression || elementAtCaret is PyListCompExpression
                "set" -> elementAtCaret is PySetLiteralExpression || elementAtCaret is PySetCompExpression
                "dict" -> elementAtCaret is PyDictLiteralExpression || elementAtCaret is PyDictCompExpression
                "tuple" -> elementAtCaret is PyTupleExpression
                else -> false
            }
            // Prefer element-wise plan when outer container is already satisfied by the argument's actual type
            // (even if it's a variable, not a literal/comprehension), and we can infer the item ctor.
            val isContainerLiteral = when (elementAtCaret) {
                is PyListLiteralExpression, is PySetLiteralExpression, is PyDictLiteralExpression,
                is PyListCompExpression, is PySetCompExpression, is PyDictCompExpression, is PyTupleExpression -> true

                else -> false
            }
            if (!isContainerLiteral) {
                // Fix: dicts are not supported for item wrapping
                if (outerCtor.name == "dict") return null

                val match = PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, outerCtor.name, context)
                val names = PyTypeIntentions.computeDisplayTypeNames(elementAtCaret, context)
                val actualUsesSameOuter = names.actual?.lowercase()?.startsWith(outerCtor.name.lowercase()) == true
                if (match == CtorMatch.MATCHES || actualUsesSameOuter) {
                    val candidates = PyTypeIntentions.expectedItemCtorsForContainer(elementAtCaret, context)
                    if (candidates.isNotEmpty()) {
                        if (candidates.size >= 2) {
                            return ElementwiseUnionChoice(elementAtCaret, outerCtor.name, candidates)
                        }
                        val only = candidates.first()
                        if (!PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, only.name, only.symbol)) {
                            return Elementwise(elementAtCaret, outerCtor.name, only.name, only.symbol)
                        }
                    }
                }
            }

            // Fallback: if outer container differs, propose wrapping with the container ctor.
            if (!isSameContainerLiteral && !isInsideMatchingContainer(elementAtCaret, outerCtor.name)) {
                val differs =
                    PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, outerCtor.name, context) == CtorMatch.DIFFERS
                val alreadyWrapped =
                    PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, outerCtor.name, outerCtor.symbol)
                if (differs && !alreadyWrapped) {
                    val target: PyExpression = (elementAtCaret.parent as? PyExpression) ?: elementAtCaret
                    if (unwrapDoesTheJob(target, outerCtor.name, context)) return null
                    return Single(target, outerCtor.name, outerCtor.symbol)
                }
            }
        }

        // Prefer container-element analysis (e.g., items inside list literals) before generic logic.
        // When the caret expression is a container literal, locate the specific item under the caret
        // and target that element for wrapping; otherwise keep the original element.
        val containerItemTarget = PyTypeIntentions.findContainerItemAtCaret(editor, elementAtCaret)
            ?: elementAtCaret

        // Attempt element-level container analysis first; it only triggers when inside a container.
        PyTypeIntentions.tryContainerItemCtor(containerItemTarget, context)
            ?.let { ctor ->
                val suppressedContainers = setOf("list", "set", "tuple", "dict")
                if (!suppressedContainers.contains(ctor.name.lowercase())) {
                    // Suppress if the element already has the expected type (e.g., int in list[int])
                    if (PyTypeIntentions.elementDisplaysAsCtor(
                            containerItemTarget,
                            ctor.name,
                            context
                        ) == CtorMatch.MATCHES
                    ) {
                        return null
                    }
                    if (!PyWrapHeuristics.isAlreadyWrappedWith(containerItemTarget, ctor.name, ctor.symbol)) {
                        // Prefer elementwise plan/label when expected outer container is present
                        // but only when the caret-targeted item is NOT inside a literal container.
                        val outer = expectedOuterContainerCtor(elementAtCaret, context)
                        if (outer != null) {
                            if (outer.name == "dict") return null

                            val p = containerItemTarget.parent
                            val isLiteral =
                                p is PyListLiteralExpression || p is PySetLiteralExpression || p is PyTupleExpression || p is PyDictLiteralExpression
                            if (!isLiteral) {
                                if (unwrapDoesTheJob(containerItemTarget, ctor.name, context)) return null
                                return Elementwise(containerItemTarget, outer.name, ctor.name, ctor.symbol)
                            }
                        }
                        if (unwrapDoesTheJob(containerItemTarget, ctor.name, context)) return null
                        return Single(containerItemTarget, ctor.name, ctor.symbol)
                    }
                }
            }
        // After handling element-level cases, fall back to union/generic ctor logic below.

        // Suppress generic wrapping when caret is on container literals where only element-level makes sense.
        if (elementAtCaret is PyListLiteralExpression ||
            elementAtCaret is PySetLiteralExpression ||
            elementAtCaret is PyDictLiteralExpression
        ) return null

        val names = PyTypeIntentions.computeDisplayTypeNames(elementAtCaret, context)
        if (names.actual == null || names.expected == null) return null
        // Allow wrapping if both types are Unknown (unresolved), provided we can find a valid constructor name later.
        if (names.actual == names.expected && names.actual != "Unknown") return null

        if (unwrapDoesTheJob(elementAtCaret, names.expected, context)) return null

        // If the expected type is a union and the element matches ANY of the union members,
        // do not offer wrapping. This prevents cases like val: int | str = 2 offering "Wrap with int()".
        val info = ExpectedTypeInfo.getExpectedTypeInfo(elementAtCaret, context)
        val expectedType = info?.type

        if (expectedType != null) {
            val actualType = context.getType(elementAtCaret)
            if (actualType != null && PyTypeChecker.match(expectedType, actualType, context)) {
                return null
            }
        }

        if (expectedType is PyUnionType) {
            val anyMatches = expectedType.members.any { member ->
                val memberName = TypeNameRenderer.render(member)
                if (PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, memberName, context) == CtorMatch.MATCHES) {
                    return@any true
                }
                // Fallback: check underlying class name (e.g. handles "Literal[1]" vs "int")
                val type = context.getType(elementAtCaret)
                type is PyClassType && type.pyClass.name == memberName
            }
            if (anyMatches) return null
        }

        val ctor = PyTypeIntentions.expectedCtorName(elementAtCaret, context)
        val annElement = names.expectedAnnotationElement
        val unionCtors = annElement?.let { UnionCandidates.collect(it, elementAtCaret) } ?: emptyList()

        if (unionCtors.size >= 2) {
            // Filter out candidates that already match the element's actual displayed type
            val differing = unionCtors.filter {
                PyTypeIntentions.elementDisplaysAsCtor(elementAtCaret, it.name, context) == CtorMatch.DIFFERS
            }
            val wrappedWithAny = differing.any {
                PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, it.name, it.symbol)
            }
            if (wrappedWithAny) return null

            when (differing.size) {
                0 -> return null
                1 -> {
                    val only = differing.first()
                    if (!PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, only.name, only.symbol))
                        return Single(elementAtCaret, only.name, only.symbol)
                    return null
                }

                else -> return UnionChoice(elementAtCaret, differing)
            }
        }

        if (!ctor.isNullOrBlank()) {
            // Do not offer container constructors (list/set/tuple/dict) when the caret is inside any container
            // literal/comprehension — element-level wrapping should handle those cases.
            val suppressedContainerCtor = when (ctor.lowercase()) {
                "list", "set", "tuple", "dict" -> {
                    val p = elementAtCaret.parent
                    p is PyListLiteralExpression ||
                            p is PySetLiteralExpression ||
                            p is PyTupleExpression ||
                            p is PyDictLiteralExpression ||
                            p is PyListCompExpression ||
                            p is PySetCompExpression ||
                            p is PyDictCompExpression
                }

                else -> false
            }
            if (suppressedContainerCtor) return null
            val ctorElem = names.expectedElement
            if (PyWrapHeuristics.isAlreadyWrappedWith(elementAtCaret, ctor, ctorElem)) return null
            return Single(elementAtCaret, ctor, ctorElem)
        }
        return null
    }

    /** Detect the expected outer container and map to concrete Python constructors we can call (list/set/tuple/dict). */
    private fun expectedOuterContainerCtor(expr: PyExpression, ctx: TypeEvalContext): ExpectedCtor? {
        val info = ExpectedTypeInfo
            .getExpectedTypeInfo(expr, ctx) ?: return null
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

    /** True if the element is inside a literal/comprehension whose outer container already matches expected. */
    private fun isInsideMatchingContainer(element: PyExpression, expectedCtor: String): Boolean {
        val p = element.parent
        return when (expectedCtor.lowercase()) {
            "list" -> p is PyListLiteralExpression || p is PyListCompExpression
            "set" -> p is PySetLiteralExpression || p is PySetCompExpression
            "tuple" -> p is PyTupleExpression
            "dict" -> p is PyDictLiteralExpression || p is PyDictCompExpression
            else -> false
        }
    }

    private fun unwrapDoesTheJob(element: PyExpression, expectedCtorName: String, context: TypeEvalContext): Boolean {
        val wrapperInfo = PyTypeIntentions.getWrapperCallInfo(element) ?: return false
        if (wrapperInfo.name.lowercase() in PyTypeIntentions.CONTAINERS) return false
        val match = PyTypeIntentions.elementDisplaysAsCtor(wrapperInfo.inner, expectedCtorName, context)
        if (match != CtorMatch.MATCHES) return false

        val type = context.getType(element)
        if (type is PyClassType) {
            val cls = type.pyClass
            if (cls.name == expectedCtorName) return true
            val ancestors = cls.getAncestorClasses(context)
            if (ancestors.any { it.name == expectedCtorName }) return true
        }

        return false
    }
}
