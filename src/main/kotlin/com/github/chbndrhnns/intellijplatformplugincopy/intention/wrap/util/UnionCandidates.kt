package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

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
private fun String.equalsAnyIgnoreCase(vararg options: String): Boolean =
    options.any { this.equals(it, ignoreCase = true) }

private fun isSupportedCtor(name: String?, symbol: PsiNamedElement?, builtins: PyBuiltinCache): Boolean {
    val n = name ?: return false
    if (n.equals("None", ignoreCase = true)) return false
    return symbol == null || !builtins.isBuiltin(symbol)
}

/**
 * Helper for resolving string-typed forward references like "A", "pkg.mod.Class" or "A|B" tokens.
 *
 * This class encapsulates the same-file / qualified / import-based resolution logic so that the
 * PSI-based union collection code can stay focused on walking expressions rather than resolution.
 */
private class StringAnnotationResolver(private val anchor: PyExpression) {

    fun resolveToken(token: String): Pair<String, PsiNamedElement?>? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null

        val name = trimmed.substringAfterLast('.')
        if (name.isBlank()) return null

        val resolved: PsiNamedElement? = when {
            // If appears qualified, try cross-module resolution
            trimmed.contains('.') -> resolveQualified(trimmed)
            else -> resolveInSameFile(name) ?: resolveViaImports(name)
        }

        return name to resolved
    }

    private fun resolveInSameFile(simpleName: String): PsiNamedElement? {
        val file = anchor.containingFile
        // Search same-file top-level symbols by name: classes, functions, and assignment targets (e.g., NewType aliases)
        val classes = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PyClass::class.java)
        classes.firstOrNull { it.name == simpleName }?.let { return it }

        val funcs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PyFunction::class.java)
        funcs.firstOrNull { it.name == simpleName }?.let { return it }

        val targets = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PyTargetExpression::class.java)
        targets.firstOrNull { it.name == simpleName }?.let { return it }

        return null
    }

    private fun resolveQualified(dotted: String): PsiNamedElement? {
        // Best-effort cross-module resolution for strings like "pkg.mod.Class" without relying on import resolver.
        val parts = dotted.split('.')
        if (parts.isEmpty()) return null
        val name = parts.last()
        val modulePath = parts.dropLast(1).joinToString("/")
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(anchor.project)
        return try {
            val candidates = com.jetbrains.python.psi.stubs.PyClassNameIndex.find(name, anchor.project, scope)
            candidates.firstOrNull { cls ->
                val path = cls.containingFile?.virtualFile?.path ?: return@firstOrNull false
                // Match end of path so it works on different OS path prefixes
                path.replace('\\', '/').endsWith("/$modulePath.py")
            } ?: candidates.firstOrNull()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaImports(simpleName: String): PsiNamedElement? {
        val file = anchor.containingFile
        val imports =
            com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, PyFromImportStatement::class.java)
        for (stmt in imports) {
            val importSource = stmt.importSourceQName?.toString() ?: continue
            for (el in stmt.importElements) {
                // visible name in this file (consider alias)
                val visible = el.asName ?: el.importedQName?.lastComponent
                if (visible == simpleName) {
                    val full = if (importSource.isNotBlank()) "$importSource.$simpleName" else simpleName
                    val resolved = resolveQualified(full)
                    if (resolved != null) return resolved
                }
            }
        }
        return null
    }
}

object UnionCandidates {
    fun collect(annotation: PyTypedElement, anchor: PyExpression): List<ExpectedCtor> {
        // Prefer type-provider based union handling first: this covers PEP 604 unions, typing.Union/Optional,
        // quoted annotations and forward refs via PyTypingTypeProvider/TypeEvalContext.
        val fromTypes = collectFromTypes(annotation, anchor)
        if (fromTypes.size >= 2) return fromTypes

        val expr = annotation as? PyExpression ?: return emptyList()
        return collectFromPsi(expr, anchor)
    }

    /**
     * Primary implementation: rely on the type system to parse/normalize unions instead of hand-parsing text.
     * Falls back to the legacy PSI/text logic above when type information is missing or yields < 2 candidates.
     */
    private fun collectFromTypes(annotation: PyTypedElement, anchor: PyExpression): List<ExpectedCtor> {
        val file = anchor.containingFile
        val project = anchor.project
        val ctx = TypeEvalContext.userInitiated(project, file)
        val type = ctx.getType(annotation) ?: return emptyList()

        val builtins = PyBuiltinCache.getInstance(anchor)
        val byName = LinkedHashMap<String, ExpectedCtor>()

        PyTypeUtil.toStream(type).forEach { member ->
            val classType = member as? PyClassType ?: return@forEach
            val cls = classType.pyClass as? PsiNamedElement ?: return@forEach
            val name = cls.name ?: return@forEach

            // Mirror legacy filters: drop explicit None and builtins.
            if (!isSupportedCtor(name, cls, builtins)) return@forEach

            byName.putIfAbsent(name, ExpectedCtor(name, cls))
        }

        return byName.values.toList()
    }

    private fun collectFromPsi(expr: PyExpression, anchor: PyExpression): List<ExpectedCtor> {
        val out = LinkedHashSet<ExpectedCtor>()
        val resolver = StringAnnotationResolver(anchor)

        fun addRef(ref: PyReferenceExpression) {
            val name = ref.name ?: return
            val resolved = ref.reference.resolve() as? PsiNamedElement
            out += ExpectedCtor(name, resolved)
        }

        fun addString(str: PyStringLiteralExpression) {
            val raw = str.stringValue ?: return

            fun addToken(token: String) {
                val (name, resolved) = resolver.resolveToken(token) ?: return
                out += ExpectedCtor(name, resolved)
            }

            // Handle PEP 604 unions written inside quotes, e.g. "A|B" or "A | B"
            if (raw.contains('|')) {
                raw.split('|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { part -> addToken(part) }
                return
            }
            addToken(raw)
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
                                    when (el) {
                                        is PyReferenceExpression -> addRef(el)
                                        is PyExpression -> visit(el) // allow nested A|B inside tuple args
                                    }
                                }

                                is PyReferenceExpression -> addRef(idx)
                                is PyStringLiteralExpression -> addString(idx)
                                is PyExpression -> visit(idx) // handle Union[A | B] and Optional[A | B]
                            }
                        }
                        return
                    }
                }
            }

            when (e) {
                is PyReferenceExpression -> addRef(e)
                is PyStringLiteralExpression -> addString(e)
            }
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

        // Filter out explicit None/builtins; allow unresolved textual candidates from forward refs
        val builtins = PyBuiltinCache.getInstance(anchor)
        val filtered = distinct.filter { isSupportedCtor(it.name, it.symbol, builtins) }
        return if (filtered.size >= 2) filtered else emptyList()
    }
}
