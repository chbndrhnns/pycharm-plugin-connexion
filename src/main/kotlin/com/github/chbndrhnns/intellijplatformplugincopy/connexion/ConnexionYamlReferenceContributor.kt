package com.github.chbndrhnns.intellijplatformplugincopy.connexion

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
                .withParent(PlatformPatterns.psiElement(YAMLKeyValue::class.java).withName("operationId")),
            ConnexionYamlReferenceProvider()
        )
    }
}

private class ConnexionYamlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val file = element.containingFile
        if (!OpenApiSpecUtil.isOpenApiFile(file)) return PsiReference.EMPTY_ARRAY
        return arrayOf<PsiReference>(ConnexionYamlReference(element))
    }
}

private class ConnexionYamlReference(element: PsiElement) : ConnexionReferenceBase(element) {
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
        val c3 = mapping.getKeyValueByKey("x-openapi-router-controller")?.value as? YAMLScalar
        if (c3 != null) return c3.textValue

        val c2 = mapping.getKeyValueByKey("x-swagger-router-controller")?.value as? YAMLScalar
        return c2?.textValue
    }
}
