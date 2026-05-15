package com.github.chbndrhnns.connexion.features.connexion

import com.intellij.openapi.diagnostic.logger
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
    private val LOG = logger<OpenApiYamlSpecUtil>()

    fun isOpenApiYamlFile(file: PsiFile): Boolean {
        val yamlFile = file as? YAMLFile ?: return false

        // Cheap heuristic
        val text = yamlFile.text
        if ((!text.contains(ConnexionConstants.OPENAPI) && !text.contains(ConnexionConstants.SWAGGER)) || !text.contains(
                ConnexionConstants.PATHS
            ) || (!text.contains(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER) && !text.contains(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER))
        ) {
            LOG.debug("File ${file.name} ignored: missing 'openapi'/'swagger', 'paths', or router controller keys in text.")
            return false
        }

        val root = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false
        val isMatch = (root.getKeyValueByKey(ConnexionConstants.OPENAPI) != null || root.getKeyValueByKey(ConnexionConstants.SWAGGER) != null) &&
                root.getKeyValueByKey(ConnexionConstants.PATHS) != null
                
        if (isMatch) {
            LOG.debug("File ${file.name} successfully parsed as OpenAPI YAML.")
        } else {
            LOG.debug("File ${file.name} ignored: 'openapi'/'swagger' or 'paths' keys not found at root level.")
        }
        
        return isMatch
    }

    fun extractYamlOperations(file: PsiFile): List<OpenApiSpecUtil.OpenApiOperation> {
        val yamlFile = file as? YAMLFile ?: return emptyList()

        val root = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return emptyList()
        val paths = root.getKeyValueByKey(ConnexionConstants.PATHS)?.value as? YAMLMapping ?: return emptyList()

        val rootController = getControllerFromYaml(root)
        val result = mutableListOf<OpenApiSpecUtil.OpenApiOperation>()

        for (pathKey in paths.keyValues) {
            val pathStr = pathKey.keyText
            val pathItem = pathKey.value as? YAMLMapping ?: continue
            val pathController = getControllerFromYaml(pathItem) ?: rootController

            for (methodKey in pathItem.keyValues) {
                val methodStr = methodKey.keyText.lowercase()
                if (methodStr == ConnexionConstants.PARAMETERS || methodStr.startsWith("x-")) continue

                val operationObj = methodKey.value as? YAMLMapping ?: continue
                val opController = getControllerFromYaml(operationObj) ?: pathController

                val opIdKv = operationObj.getKeyValueByKey(ConnexionConstants.OPERATION_ID) ?: continue
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
        return (mapping.getKeyValueByKey(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER)?.value as? YAMLScalar)?.textValue
            ?: (mapping.getKeyValueByKey(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER)?.value as? YAMLScalar)?.textValue
    }
}
