package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * YAML-only OpenAPI helpers.
 *
 * IMPORTANT: This object must only be accessed when the YAML plugin is available.
 */
internal object OpenApiYamlSpecUtil {

    private const val CONTROLLER_V3 = "x-openapi-router-controller"
    private const val CONTROLLER_V2 = "x-swagger-router-controller"

    fun isOpenApiYamlFile(file: PsiFile): Boolean {
        val yamlFile = file as? YAMLFile ?: return false

        // Cheap heuristic
        val text = yamlFile.text
        if ((!text.contains("openapi") && !text.contains("swagger")) || !text.contains("paths")) {
            return false
        }

        val root = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false
        return (root.getKeyValueByKey("openapi") != null || root.getKeyValueByKey("swagger") != null) &&
                root.getKeyValueByKey("paths") != null
    }

    fun extractYamlOperations(file: PsiFile): List<OpenApiSpecUtil.OpenApiOperation> {
        val yamlFile = file as? YAMLFile ?: return emptyList()

        val root = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return emptyList()
        val paths = root.getKeyValueByKey("paths")?.value as? YAMLMapping ?: return emptyList()

        val rootController = getControllerFromYaml(root)
        val result = mutableListOf<OpenApiSpecUtil.OpenApiOperation>()

        for (pathKey in paths.keyValues) {
            val pathStr = pathKey.keyText
            val pathItem = pathKey.value as? YAMLMapping ?: continue
            val pathController = getControllerFromYaml(pathItem) ?: rootController

            for (methodKey in pathItem.keyValues) {
                val methodStr = methodKey.keyText.lowercase()
                if (methodStr == "parameters" || methodStr.startsWith("x-")) continue

                val operationObj = methodKey.value as? YAMLMapping ?: continue
                val opController = getControllerFromYaml(operationObj) ?: pathController

                val opIdKv = operationObj.getKeyValueByKey("operationId") ?: continue
                val opIdVal = opIdKv.value as? YAMLScalar ?: continue

                result.add(
                    OpenApiSpecUtil.OpenApiOperation(
                        yamlFile,
                        pathStr,
                        methodStr,
                        opIdVal.textValue,
                        opIdVal,
                        opController
                    )
                )
            }
        }

        return result
    }

    private fun getControllerFromYaml(mapping: YAMLMapping): String? {
        return (mapping.getKeyValueByKey(CONTROLLER_V3)?.value as? YAMLScalar)?.textValue
            ?: (mapping.getKeyValueByKey(CONTROLLER_V2)?.value as? YAMLScalar)?.textValue
    }
}
