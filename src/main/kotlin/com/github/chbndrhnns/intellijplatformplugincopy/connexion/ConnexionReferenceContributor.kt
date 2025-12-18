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
                .withParent(PlatformPatterns.psiElement(JsonProperty::class.java).withName(ConnexionConstants.OPERATION_ID)),
            ConnexionJsonReferenceProvider()
        )
        
        // JSON: "x-openapi-router-controller": "<value>"
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JsonStringLiteral::class.java)
                .withParent(PlatformPatterns.psiElement(JsonProperty::class.java).withName(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER)),
            ConnexionJsonControllerReferenceProvider()
        )

        // JSON: "x-swagger-router-controller": "<value>"
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JsonStringLiteral::class.java)
                .withParent(PlatformPatterns.psiElement(JsonProperty::class.java).withName(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER)),
            ConnexionJsonControllerReferenceProvider()
        )
    }
}

private class ConnexionJsonReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val parent = element.parent
        if (parent is JsonProperty && parent.value != element) return PsiReference.EMPTY_ARRAY

        val file = element.containingFile
        if (!OpenApiSpecUtil.isOpenApiFile(file)) return PsiReference.EMPTY_ARRAY
        return arrayOf(ConnexionJsonReference(element))
    }
}

private class ConnexionJsonControllerReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val parent = element.parent
        if (parent is JsonProperty && parent.value != element) return PsiReference.EMPTY_ARRAY

        val file = element.containingFile
        if (!OpenApiSpecUtil.isOpenApiFile(file)) return PsiReference.EMPTY_ARRAY

        val text = element.text
        if (text.length < 2) return PsiReference.EMPTY_ARRAY

        val valueStartOffset = 1
        val value = text.substring(1, text.length - 1)

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

private class ConnexionJsonReference(element: PsiElement) : ConnexionReferenceBase(element) {
    override fun operationIdText(): String = (element as? JsonStringLiteral)?.value ?: super.operationIdText()

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
        val c3 = obj.findProperty(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER)?.value as? JsonStringLiteral
        if (c3 != null) return c3.value

        val c2 = obj.findProperty(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER)?.value as? JsonStringLiteral
        return c2?.value
    }
}
