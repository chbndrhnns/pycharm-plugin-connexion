package com.github.chbndrhnns.intellijplatformplugincopy.features.refactoring.parameterobject.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyNamedParameter

/**
 * Interface for generating parameter object classes of different base types.
 */
interface ParameterObjectGenerator {
    /**
     * Generates a parameter object class with the given configuration.
     *
     * @param project The current project
     * @param languageLevel The Python language level
     * @param className The name for the generated class
     * @param params The parameters to include as fields
     * @param generateFrozen Whether to generate as frozen/immutable (if applicable)
     * @param generateSlots Whether to use slots (if applicable)
     * @param generateKwOnly Whether to use keyword-only arguments (if applicable)
     * @return The generated PyClass
     */
    fun generateClass(
        project: Project,
        languageLevel: LanguageLevel,
        className: String,
        params: List<PyNamedParameter>,
        generateFrozen: Boolean,
        generateSlots: Boolean,
        generateKwOnly: Boolean
    ): PyClass

    /**
     * Adds the required imports for this base type to the file.
     *
     * @param file The Python file to add imports to
     * @param anchor The anchor element for import placement
     */
    fun addRequiredImports(file: PyFile, anchor: PsiElement)
}
