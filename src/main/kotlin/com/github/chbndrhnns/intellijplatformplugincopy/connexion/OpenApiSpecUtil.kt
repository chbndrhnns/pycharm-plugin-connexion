package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex

object OpenApiSpecUtil {

    private val YAML_PLUGIN_ID: PluginId = PluginId.getId("org.jetbrains.plugins.yaml")

    private fun isYamlAvailable(): Boolean {
        return PluginManagerCore.getPlugin(YAML_PLUGIN_ID) != null && !PluginManagerCore.isDisabled(YAML_PLUGIN_ID)
    }

    private fun yamlFileTypes(): List<FileType> {
        val ftm = FileTypeManager.getInstance()
        return listOf("yaml", "yml")
            .map { ftm.getFileTypeByExtension(it) }
            .distinct()
    }

    fun isOpenApiFile(file: PsiFile): Boolean {
        return when (file) {
            is JsonFile -> isOpenApiJsonFile(file)
            else -> {
                if (!isYamlAvailable()) return false
                OpenApiYamlSpecUtil.isOpenApiYamlFile(file)
            }
        }
    }

    private fun isOpenApiJsonFile(file: JsonFile): Boolean {
        // Cheap heuristic
        val text = file.text
        if ((!text.contains(ConnexionConstants.OPENAPI) && !text.contains(ConnexionConstants.SWAGGER)) || !text.contains(
                ConnexionConstants.PATHS
            )
        ) {
            return false
        }

        val root = file.topLevelValue as? JsonObject ?: return false
        return (root.findProperty(ConnexionConstants.OPENAPI) != null || root.findProperty(ConnexionConstants.SWAGGER) != null) &&
                root.findProperty(ConnexionConstants.PATHS) != null
    }

    fun findAllOpenApiFiles(project: Project): List<PsiFile> {
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        val jsonFiles = FileTypeIndex.getFiles(JsonFileType.INSTANCE, scope)
            .mapNotNull { psiManager.findFile(it) }
            .filterIsInstance<JsonFile>()
            .filter { isOpenApiJsonFile(it) }

        val yamlFiles = if (isYamlAvailable()) {
            yamlFileTypes()
                .flatMap { FileTypeIndex.getFiles(it, scope) }
                .distinct()
                .mapNotNull { psiManager.findFile(it) }
                .filter { OpenApiYamlSpecUtil.isOpenApiYamlFile(it) }
        } else {
            emptyList()
        }

        return yamlFiles + jsonFiles
    }

    data class OpenApiOperation(
        val file: PsiFile,
        val path: String,
        val method: String,
        val operationId: String,
        val operationIdElement: PsiElement,
        val controller: String?
    )

    fun extractOperations(file: PsiFile): List<OpenApiOperation> {
        return when (file) {
            is JsonFile -> extractJsonOperations(file)
            else -> {
                if (!isYamlAvailable()) return emptyList()
                OpenApiYamlSpecUtil.extractYamlOperations(file)
            }
        }
    }

    fun findSpecOperationsForFunction(function: PyFunction): List<OpenApiOperation> {
        val qName = function.qualifiedName ?: return emptyList()
        val project = function.project
        val files = findAllOpenApiFiles(project)
        val functionFile = function.containingFile

        val result = mutableListOf<OpenApiOperation>()
        for (file in files) {
            val operations = extractOperations(file)
            for (op in operations) {
                if (matchesFunction(op, qName, functionFile, project)) {
                    result.add(op)
                }
            }
        }
        return result
    }

    private fun matchesFunction(
        op: OpenApiOperation,
        functionQName: String,
        functionFile: PsiFile,
        project: Project
    ): Boolean {
        val opId = op.operationId.replace(":", ".")
        if (opId == functionQName) return true

        val controller = op.controller
        if (controller != null) {
            val fqn = "$controller.$opId".replace(":", ".")
            if (fqn == functionQName) return true

            // Check if controller resolves to the function's file
            val resolvedController = resolvePath(project, controller)
            if (resolvedController != null && resolvedController == functionFile && opId == functionQName.substringAfterLast(
                    "."
                )
            ) {
                return true
            }
        }
        return false
    }

    fun resolvePythonSymbol(qName: String, project: Project): List<PsiElement> {
        val parts = qName.split(".")
        if (parts.isEmpty()) return emptyList()

        val lastPart = parts.last()
        val moduleName = parts.dropLast(1).joinToString(".")

        val result = mutableListOf<PsiElement>()
        val scope = GlobalSearchScope.projectScope(project)

        val functions = PyFunctionNameIndex.find(lastPart, project, scope)
        val moduleFile = if (moduleName.isNotEmpty()) resolvePath(project, moduleName) as? PyFile else null

        for (function in functions) {
            if (isSymbolInModule(function, moduleName)) {
                result.add(function)
            } else if (moduleFile != null && function.containingFile == moduleFile) {
                result.add(function)
            }
        }
        return result
    }

    fun resolvePath(project: Project, path: String): PsiFileSystemItem? {
        val baseDir = ProjectRootManager.getInstance(project).contentRoots.firstOrNull() ?: return null
        var current: PsiFileSystemItem? = PsiManager.getInstance(project).findDirectory(baseDir) ?: return null

        if (path.isEmpty()) return current

        val parts = path.split(".")
        for (part in parts) {
            if (current is com.intellij.psi.PsiDirectory) {
                val subDir = current.findSubdirectory(part)
                if (subDir != null) {
                    current = subDir
                    continue
                }
                val file = current.findFile("$part.py")
                if (file is PyFile) {
                    current = file
                    continue
                }
                return null
            } else {
                return null
            }
        }
        return current
    }

    private fun isSymbolInModule(symbol: PsiElement, expectedModuleQName: String): Boolean {
        if (symbol is PyFunction) {
            val symbolQName = symbol.qualifiedName ?: return false
            val parentQName = symbolQName.substringBeforeLast(".")
            return parentQName == expectedModuleQName
        }
        return false
    }

    private fun extractJsonOperations(file: JsonFile): List<OpenApiOperation> {
        val root = file.topLevelValue as? JsonObject ?: return emptyList()
        val paths = root.findProperty(ConnexionConstants.PATHS)?.value as? JsonObject ?: return emptyList()

        val rootController = getControllerFromJson(root)
        val result = mutableListOf<OpenApiOperation>()

        for (pathProp in paths.propertyList) {
            val pathStr = pathProp.name
            val pathItem = pathProp.value as? JsonObject ?: continue
            val pathController = getControllerFromJson(pathItem) ?: rootController

            for (methodProp in pathItem.propertyList) {
                val methodStr = methodProp.name.lowercase()
                if (methodStr == ConnexionConstants.PARAMETERS || methodStr.startsWith("x-")) continue

                val operationObj = methodProp.value as? JsonObject ?: continue
                val opController = getControllerFromJson(operationObj) ?: pathController

                val opIdProp = operationObj.findProperty(ConnexionConstants.OPERATION_ID)
                if (opIdProp != null) {
                    val opIdVal = opIdProp.value
                    if (opIdVal is JsonStringLiteral) {
                        result.add(
                            OpenApiOperation(
                                file,
                                pathStr,
                                methodStr,
                                opIdVal.value,
                                opIdVal,
                                opController
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun getControllerFromJson(obj: JsonObject): String? {
        return (obj.findProperty(ConnexionConstants.X_OPENAPI_ROUTER_CONTROLLER)?.value as? JsonStringLiteral)?.value
            ?: (obj.findProperty(ConnexionConstants.X_SWAGGER_ROUTER_CONTROLLER)?.value as? JsonStringLiteral)?.value
    }
}
