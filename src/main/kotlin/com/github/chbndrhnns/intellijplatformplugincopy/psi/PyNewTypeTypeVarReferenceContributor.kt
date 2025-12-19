package com.github.chbndrhnns.intellijplatformplugincopy.psi

import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.*

class PyNewTypeTypeVarReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PyStringLiteralExpression::class.java)
                .withParent(PyArgumentList::class.java)
                .withSuperParent(2, PyCallExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (!PluginSettingsState.instance().state.enableNewTypeTypeVarParamSpecRename) return PsiReference.EMPTY_ARRAY

                    val literal = element as? PyStringLiteralExpression ?: return PsiReference.EMPTY_ARRAY
                    val argumentList = literal.parent as? PyArgumentList ?: return PsiReference.EMPTY_ARRAY
                    val call = argumentList.parent as? PyCallExpression ?: return PsiReference.EMPTY_ARRAY
                    val callee = call.callee as? PyReferenceExpression ?: return PsiReference.EMPTY_ARRAY

                    val name = callee.name
                    if (name != "NewType" && name != "TypeVar" && name != "ParamSpec") return PsiReference.EMPTY_ARRAY

                    val arguments = argumentList.arguments
                    if (arguments.isEmpty() || arguments[0] !== literal) return PsiReference.EMPTY_ARRAY

                    val assignment = call.parent as? PyAssignmentStatement ?: return PsiReference.EMPTY_ARRAY
                    val targets = assignment.targets
                    if (targets.size != 1) return PsiReference.EMPTY_ARRAY

                    val target = targets[0] as? PyTargetExpression ?: return PsiReference.EMPTY_ARRAY

                    if (literal.stringValue != target.name) return PsiReference.EMPTY_ARRAY

                    return arrayOf(PyNewTypeTypeVarReference(literal, target))
                }
            }
        )
    }
}

class PyNewTypeTypeVarReference(element: PyStringLiteralExpression, private val target: PyTargetExpression) :
    PsiReferenceBase<PyStringLiteralExpression>(element, element.stringValueTextRange) {

    override fun resolve(): PsiElement = target

    override fun handleElementRename(newElementName: String): PsiElement {
        val generator = PyElementGenerator.getInstance(element.project)
        val newLiteral = generator.createStringLiteralAlreadyEscaped("\"$newElementName\"")
        return element.replace(newLiteral)
    }
}
