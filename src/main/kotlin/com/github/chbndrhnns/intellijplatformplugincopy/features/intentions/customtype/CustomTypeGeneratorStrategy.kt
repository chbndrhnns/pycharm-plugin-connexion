package com.github.chbndrhnns.intellijplatformplugincopy.features.intentions.customtype

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFile

/**
 * Strategy interface for generating different kinds of custom types.
 *
 * Implementations handle the specifics of creating type definitions
 * (subclass, NewType, dataclass, Pydantic model) and any required imports.
 */
interface CustomTypeGeneratorStrategy {

    /**
     * The kind of custom type this generator creates.
     */
    val kind: CustomTypeKind

    /**
     * Determine the text to use for the base/underlying type.
     *
     * For subscripted container annotations like `dict[str, list[int]]`,
     * this may return the full annotation text including generic arguments.
     */
    fun determineBaseTypeText(builtinName: String, annotationRef: PyExpression?): String

    /**
     * Create the type definition element (class, assignment, etc.).
     *
     * @param project The current project
     * @param name The name for the new type
     * @param builtin The builtin type name (may include generic args for containers)
     * @return The created PSI element representing the type definition
     */
    fun createTypeDefinition(project: Project, name: String, builtin: String): PsiElement

    /**
     * Insert the type definition into the target file.
     *
     * @param targetFile The file where the type should be inserted
     * @param typeDefinition The type definition element to insert
     * @return The inserted element
     */
    fun insertTypeDefinition(targetFile: PyFile, typeDefinition: PsiElement): PsiElement

    /**
     * Add any required imports for this type kind.
     *
     * For example, NewType requires `from typing import NewType`,
     * frozen dataclass requires `from dataclasses import dataclass`.
     *
     * @param targetFile The file where imports should be added
     */
    fun addRequiredImports(targetFile: PyFile)

    /**
     * Generate the expression text for wrapping a value with this type.
     *
     * @param typeName The name of the custom type
     * @param valueText The text of the value expression to wrap
     * @return The wrapped expression text
     */
    fun wrapExpressionText(typeName: String, valueText: String): String

    /**
     * Whether this type kind requires `.value` access to get the underlying value.
     *
     * True for value objects (frozen dataclass, Pydantic), false for subclass/NewType.
     */
    val requiresValueAccess: Boolean
        get() = false
}

/**
 * Factory function to get the appropriate generator strategy for a given type kind.
 */
fun getGeneratorStrategy(kind: CustomTypeKind): CustomTypeGeneratorStrategy {
    return when (kind) {
        CustomTypeKind.SUBCLASS -> SubclassTypeGenerator()
        CustomTypeKind.NEWTYPE -> NewTypeGenerator()
        CustomTypeKind.FROZEN_DATACLASS -> FrozenDataclassGenerator()
        CustomTypeKind.PYDANTIC_VALUE_OBJECT -> PydanticValueObjectGenerator()
    }
}
