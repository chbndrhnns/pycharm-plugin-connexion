package com.github.chbndrhnns.intellijplatformplugincopy.intention.wrap.util

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.jetbrains.python.psi.impl.PyBuiltinCache

/**
 * Coarse-grained classification of Python types used for wrap intentions.
 *
 * The priority expresses how desirable it is to wrap with a constructor
 * from this bucket when multiple union members are available.
 */
enum class TypeBucket(val priority: Int) {
    BUILTIN(0),     // lowest
    STDLIB(1),
    THIRDPARTY(2),
    OWN(3);         // highest
}

/**
 * Best-effort bucket classifier. The exact boundaries are heuristic but stable
 * enough for intention ranking:
 *
 *  - BUILTIN: symbols provided by PyBuiltinCache.
 *  - OWN: symbols whose files are in the project content roots.
 *  - THIRDPARTY: library symbols located under common virtualenv / site-packages
 *    directories.
 *  - STDLIB: other library symbols.
 */
object TypeBucketClassifier {

    fun bucketFor(symbol: PsiNamedElement?, anchor: PsiElement): TypeBucket? {
        if (symbol == null) return null

        val builtins = PyBuiltinCache.getInstance(anchor)
        if (builtins.isBuiltin(symbol)) return TypeBucket.BUILTIN

        val vFile = symbol.containingFile?.virtualFile ?: return TypeBucket.OWN
        val project = anchor.project
        val index = ProjectFileIndex.getInstance(project)

        if (index.isInContent(vFile)) return TypeBucket.OWN

        if (index.isInLibraryClasses(vFile) || index.isInLibrarySource(vFile)) {
            val path = vFile.path.replace('\\', '/')
            return if (path.contains("/site-packages/") ||
                path.contains("/dist-packages/") ||
                path.contains("/.venv/") ||
                path.contains("/venv/")
            ) {
                TypeBucket.THIRDPARTY
            } else {
                TypeBucket.STDLIB
            }
        }

        // Fallback when the file is outside any known root: treat as OWN so
        // that project/local code is still preferred over stdlib/builtins.
        return TypeBucket.OWN
    }
}
