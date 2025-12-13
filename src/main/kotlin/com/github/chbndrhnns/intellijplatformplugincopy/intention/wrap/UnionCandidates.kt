package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap

import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedCtor
import com.github.chbndrhnns.intellijplatformplugincopy.intention.shared.ExpectedTypeInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

private val LOG = logger<StringAnnotationResolver>()

/**
 * Utility that extracts constructor candidates from union-like annotations.
 *
 * Supported forms:
 * - PEP 604 style: `A | B | C`
 * - typing.Union[A, B, C]
 * - typing.Optional[A]  (treated as Union[A, NoneType] but explicit ``None`` is ignored).
 *
 * Returns a list of distinct (name, resolved element) pairs, and only when at
 * least two such candidates can be determined. Builtins are kept so they can
 * participate in bucket-based prioritization.
 */
private fun String.equalsAnyIgnoreCase(vararg options: String): Boolean =
    options.any { this.equals(it, ignoreCase = true) }

private fun isSupportedCtor(name: String?, symbol: PsiNamedElement?, builtins: PyBuiltinCache): Boolean {
    val n = name ?: return false
    // Keep builtins so that union bucket selection can prefer higher-value
    // candidates (stdlib/thirdparty/own) over them.
    return !ExpectedTypeInfo.isTooGenericCtorName(n)
}

/**
 * Helper for resolving string-typed forward references like "A", "pkg.mod.Class" or "A|B" tokens.
 *
 * This class encapsulates the same-file / qualified / import-based resolution logic so that the
 * PSI-based union collection code can stay focused on walking expressions rather than resolution.
 */
private class StringAnnotationResolver(private val anchor: PyExpression) {

    /** Lazily collected same-file symbols, indexed by simple name. */
    private val sameFileSymbols: SameFileSymbols by lazy { collectSameFileSymbols(anchor) }

    /** Lazily collected from-import statements in the current file. */
    private val fromImports: Collection<PyFromImportStatement> by lazy {
        PsiTreeUtil.findChildrenOfType(
            anchor.containingFile,
            PyFromImportStatement::class.java
        )
    }

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
        // Use cached same-file symbols instead of re-scanning PSI on every call.
        sameFileSymbols.classes[simpleName]?.let { return it }
        sameFileSymbols.functions[simpleName]?.let { return it }
        sameFileSymbols.targets[simpleName]?.let { return it }
        return null
    }

    private fun resolveQualified(dotted: String): PsiNamedElement? {
        // Best-effort cross-module resolution for strings like "pkg.mod.Class" without relying on import resolver.
        val parts = dotted.split('.')
        if (parts.isEmpty()) return null
        val name = parts.last()
        val modulePath = parts.dropLast(1).joinToString("/")
        val scope = GlobalSearchScope.projectScope(anchor.project)
        return try {
            val candidates = PyClassNameIndex.find(name, anchor.project, scope)
            candidates.firstOrNull { cls ->
                val path = cls.containingFile?.virtualFile?.path ?: return@firstOrNull false
                // Match end of path so it works on different OS path prefixes
                path.replace('\\', '/').endsWith("/$modulePath.py")
            } ?: candidates.firstOrNull()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("Failed to resolve qualified name '$dotted'", e)
            null
        }
    }

    private fun resolveViaImports(simpleName: String): PsiNamedElement? {
        // Iterate over cached from-imports; PSI is scanned only once per resolver instance.
        for (stmt in fromImports) {
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

private data class SameFileSymbols(
    val classes: Map<String, PyClass>,
    val functions: Map<String, PyFunction>,
    val targets: Map<String, PyTargetExpression>,
)

private fun collectSameFileSymbols(anchor: PyExpression): SameFileSymbols {
    val file = anchor.containingFile

    val classes = PsiTreeUtil.findChildrenOfType(file, PyClass::class.java)
        .mapNotNull { cls ->
            val name = cls.name ?: return@mapNotNull null
            name to cls
        }
        .toMap()

    val functions = PsiTreeUtil.findChildrenOfType(file, PyFunction::class.java)
        .mapNotNull { fn ->
            val name = fn.name ?: return@mapNotNull null
            name to fn
        }
        .toMap()

    val targets = PsiTreeUtil.findChildrenOfType(file, PyTargetExpression::class.java)
        .mapNotNull { target ->
            val name = target.name ?: return@mapNotNull null
            name to target
        }
        .toMap()

    return SameFileSymbols(classes, functions, targets)
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

            // Additional heuristic: avoid super-generic ctor names (e.g. ``object``, ``Any``) which
            // typically come from very broad typing in stubs like ``print(*values: object, ...)``.
            // Suggesting "Wrap with object()" or variants like "Wrap with (object, ...)()" is not helpful.
            if (ExpectedTypeInfo.isTooGenericCtorName(name)) return@forEach

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
            val raw = str.stringValue

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

        // Filter out explicit ``None``; allow unresolved textual candidates from forward refs
        val builtins = PyBuiltinCache.getInstance(anchor)
        val filtered = distinct.filter { isSupportedCtor(it.name, it.symbol, builtins) }
        return if (filtered.size >= 2) filtered else emptyList()
    }
}
