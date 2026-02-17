package com.github.chbndrhnns.connexion.features.connexion

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * YAML-only contributor.
 *
 * Must only be registered when the YAML plugin is available.
 */
class ConnexionYamlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // YAML: operationId: <value>
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java)
                .withParent(PlatformPatterns.psiElement(YAMLKeyValue::class.java).withName(ConnexionConstants.OPERATION_ID)),
            ConnexionYamlReferenceProvider()
        )

        // YAML: x-openapi-router-controller: <value>
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java)
                .withParent(PlatformPatterns.psiElement(YAMLKeyValue::class.java).withName(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER)),
            ConnexionYamlControllerReferenceProvider()
        )

        // YAML: x-swagger-router-controller: <value>
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java)
                .withParent(PlatformPatterns.psiElement(YAMLKeyValue::class.java).withName(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER)),
            ConnexionYamlControllerReferenceProvider()
        )
    }
}

private class ConnexionYamlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val parent = element.parent
        if (parent is YAMLKeyValue && parent.value != element) return PsiReference.EMPTY_ARRAY

        val file = element.containingFile
        if (!OpenApiSpecUtil.isOpenApiFile(file)) return PsiReference.EMPTY_ARRAY
        return arrayOf(ConnexionYamlReference(element))
    }
}

private class ConnexionYamlControllerReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val parent = element.parent
        if (parent is YAMLKeyValue && parent.value != element) return PsiReference.EMPTY_ARRAY

        val file = element.containingFile
        if (!OpenApiSpecUtil.isOpenApiFile(file)) return PsiReference.EMPTY_ARRAY

        val manipulator = ElementManipulators.getManipulator(element) ?: return PsiReference.EMPTY_ARRAY
        val valueRange = manipulator.getRangeInElement(element)
        val value = valueRange.substring(element.text)
        val valueStartOffset = valueRange.startOffset

        val references = mutableListOf<PsiReference>()
        var start = 0
        var currentPrefix = ""
        while (true) {
            val dotIndex = value.indexOf('.', start)
            val end = if (dotIndex == -1) value.length else dotIndex
            val range = com.intellij.openapi.util.TextRange(valueStartOffset + start, valueStartOffset + end)

            references.add(ConnexionControllerReference(element, range, currentPrefix))

            val part = value.substring(start, end)
            if (currentPrefix.isEmpty()) currentPrefix = part else currentPrefix += ".$part"

            if (dotIndex == -1) break
            start = dotIndex + 1
        }

        return references.toTypedArray()
    }
}

private class ConnexionYamlReference(element: PsiElement) : ConnexionReferenceBase(element) {
    override fun operationIdText(): String = (element as? YAMLScalar)?.textValue ?: super.operationIdText()

    override fun findController(): String? {
        val operationMapping = element.parent?.parent as? YAMLMapping ?: return null

        val operationController = getControllerFromYaml(operationMapping)

        val methodKV = operationMapping.parent as? YAMLKeyValue
        val pathItemMapping = methodKV?.parent as? YAMLMapping
        val pathController = pathItemMapping?.let { getControllerFromYaml(it) }

        val rootController = run {
            val pathKV = pathItemMapping?.parent as? YAMLKeyValue
            val pathsMapping = pathKV?.parent as? YAMLMapping
            val pathsKV = pathsMapping?.parent as? YAMLKeyValue
            val rootMapping = pathsKV?.parent as? YAMLMapping
            rootMapping?.let { getControllerFromYaml(it) }
        }

        return operationController ?: pathController ?: rootController
    }

    private fun getControllerFromYaml(mapping: YAMLMapping): String? {
        val c3 = mapping.getKeyValueByKey(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER)?.value as? YAMLScalar
        if (c3 != null) return c3.textValue

        val c2 = mapping.getKeyValueByKey(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER)?.value as? YAMLScalar
        return c2?.textValue
    }
}
