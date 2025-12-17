package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

/**
 * JSON-only contributor.
 *
 * YAML support is registered via an optional dependency (see `META-INF/yaml-support.xml`).
 */
class ConnexionJsonReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // JSON: "operationId": "<value>"
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JsonStringLiteral::class.java)
                .withParent(PlatformPatterns.psiElement(JsonProperty::class.java).withName("operationId")),
            ConnexionJsonReferenceProvider()
        )
    }
}

private class ConnexionJsonReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val file = element.containingFile
        if (!OpenApiSpecUtil.isOpenApiFile(file)) return PsiReference.EMPTY_ARRAY
        return arrayOf(ConnexionJsonReference(element))
    }
}

private class ConnexionJsonReference(element: PsiElement) : ConnexionReferenceBase(element) {
    override fun findController(): String? {
        val operationObj = element.parent?.parent as? JsonObject ?: return null

        val operationController = getControllerFromJson(operationObj)

        val methodProp = operationObj.parent as? JsonProperty
        val pathItemObj = methodProp?.parent as? JsonObject
        val pathController = pathItemObj?.let { getControllerFromJson(it) }

        val rootController = run {
            val pathProp = pathItemObj?.parent as? JsonProperty
            val pathsObj = pathProp?.parent as? JsonObject
            val pathsProp = pathsObj?.parent as? JsonProperty
            val rootObj = pathsProp?.parent as? JsonObject
            rootObj?.let { getControllerFromJson(it) }
        }

        return operationController ?: pathController ?: rootController
    }

    private fun getControllerFromJson(obj: JsonObject): String? {
        val c3 = obj.findProperty("x-openapi-router-controller")?.value as? JsonStringLiteral
        if (c3 != null) return c3.value

        val c2 = obj.findProperty("x-swagger-router-controller")?.value as? JsonStringLiteral
        return c2?.value
    }
}
