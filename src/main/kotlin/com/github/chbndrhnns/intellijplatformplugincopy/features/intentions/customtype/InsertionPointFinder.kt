package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

/**
 * Decides which module should host the newly introduced custom type.
 *
 * This is a direct extraction of the logic that previously lived in
 * IntroduceCustomTypeFromStdlibIntention. Behaviour is intentionally kept
 * identical:
 *
 * - When working with a dataclass field, the custom type is created in the
 *   file that declares the dataclass.
 * - When invoked on a call-site expression that resolves to a dataclass
 *   constructor (but without a specific field), the custom type is also
 *   created in the dataclass declaration file.
 * - Otherwise, the current file is used as before.
 */
class InsertionPointFinder {

    /**
     * Choose the file that should host the new custom type.
     *
     * @param dataclassField the dataclass field associated with the builtin, if any
     * @param expression the expression we were invoked on, if any
     * @param currentFile the file where the intention was invoked
     */
    fun chooseFile(
        dataclassField: PyTargetExpression?,
        expression: PyExpression?,
        currentFile: PyFile,
    ): PyFile {
        // Prefer the dataclass declaration file when we know the field.
        val sourceFileFromField = (dataclassField?.containingFile as? PyFile)

        // Otherwise, when invoked on a call-site expression, try to resolve the
        // callee to a dataclass and use its containing file.
        val sourceFileFromCall = if (dataclassField == null && expression != null) {
            val call = PsiTreeUtil.getParentOfType(expression, PyCallExpression::class.java, false)
            val callee = call?.callee as? PyReferenceExpression
            val resolvedClass = callee?.reference?.resolve() as? PyClass
            if (resolvedClass != null && isDataclassClass(resolvedClass)) {
                resolvedClass.containingFile as? PyFile
            } else null
        } else null

        return sourceFileFromField ?: sourceFileFromCall ?: currentFile
    }
}
