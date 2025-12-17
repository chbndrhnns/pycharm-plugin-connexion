package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class ConnexionPythonLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element !is PyFunction) return

        val operations = OpenApiSpecUtil.findSpecOperationsForFunction(element)
        if (operations.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
            .setTargets(operations.map { it.operationIdElement })
            .setTooltipText("Navigate to OpenAPI spec")

        result.add(builder.createLineMarkerInfo(element.nameIdentifier ?: element))
    }
}

class ConnexionJsonLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element !is JsonStringLiteral) return

        if (!isOperationIdValue(element)) return
        if (!OpenApiSpecUtil.isOpenApiFile(element.containingFile)) return

        val operations = OpenApiSpecUtil.extractOperations(element.containingFile)
        val op = operations.find { it.operationIdElement == element } ?: return

        val qName = if (op.controller != null) "${op.controller}.${op.operationId}" else op.operationId
        val resolved = OpenApiSpecUtil.resolvePythonSymbol(qName.replace(":", "."), element.project)

        if (resolved.isNotEmpty()) {
            val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(resolved)
                .setTooltipText("Navigate to implementation")
            result.add(builder.createLineMarkerInfo(element.firstChild ?: element))
        }
    }

    private fun isOperationIdValue(element: JsonStringLiteral): Boolean {
        val parent = element.parent
        if (parent is JsonProperty) {
            return parent.name == "operationId" && parent.value == element
        }
        return false
    }
}

class ConnexionYamlLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (element !is YAMLScalar) return

        if (!isOperationIdValue(element)) return
        if (!OpenApiSpecUtil.isOpenApiFile(element.containingFile)) return

        val operations = OpenApiSpecUtil.extractOperations(element.containingFile)
        val op = operations.find { it.operationIdElement == element } ?: return

        val qName = if (op.controller != null) "${op.controller}.${op.operationId}" else op.operationId
        val resolved = OpenApiSpecUtil.resolvePythonSymbol(qName.replace(":", "."), element.project)

        if (resolved.isNotEmpty()) {
            val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(resolved)
                .setTooltipText("Navigate to implementation")
            result.add(builder.createLineMarkerInfo(element.firstChild ?: element))
        }
    }

    private fun isOperationIdValue(element: YAMLScalar): Boolean {
        val parent = element.parent
        if (parent is YAMLKeyValue) {
            return parent.keyText == "operationId" && parent.value == element
        }
        return false
    }
}
