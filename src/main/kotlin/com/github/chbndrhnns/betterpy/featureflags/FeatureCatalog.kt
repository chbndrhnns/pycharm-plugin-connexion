package com.github.chbndrhnns.betterpy.featureflags

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger

data class FeatureDeclaration(
    val id: String = "",
    val displayName: String = "",
    val description: String = "",
    val category: FeatureCategory = FeatureCategory.OTHER,
    val maturity: FeatureMaturity = FeatureMaturity.STABLE,
    val youtrackIssues: List<String> = emptyList(),
    val loggingCategories: List<String> = emptyList(),
    val since: String = "",
    val removeIn: String = "",
    val defaultEnabled: Boolean = true,
    val registrations: List<FeatureRegistration> = emptyList()
)

data class FeatureRegistration(
    val type: String = "",
    val className: String = ""
)

object FeatureCatalogLoader {
    private val logger = Logger.getInstance(FeatureCatalogLoader::class.java)
    private val mapper = ObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())

    private val cachedDeclarations: List<FeatureDeclaration> by lazy {
        val classLoader = FeatureCatalogLoader::class.java.classLoader
        val resources = classLoader.getResources("features").toList()
        if (resources.isEmpty()) {
            logger.warn("features/ directory not found on classpath")
            return@lazy emptyList()
        }

        val declarations = mutableListOf<FeatureDeclaration>()
        resources.forEach { resource ->
            val protocol = resource.protocol

            when (protocol) {
                "file" -> {
                    val path = java.nio.file.Paths.get(resource.toURI())
                    declarations.addAll(loadFromPath(path))
                }

                "jar" -> {
                    declarations.addAll(loadFromJar(resource))
                }

                else -> logger.warn("Unsupported features/ protocol: $protocol")
            }
        }

        declarations.sortedBy { it.id }
    }

    fun load(): List<FeatureDeclaration> = cachedDeclarations

    private fun loadFromPath(path: java.nio.file.Path): List<FeatureDeclaration> {
        if (!java.nio.file.Files.exists(path)) {
            return emptyList()
        }

        return java.nio.file.Files.list(path).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".yaml") }
                .sorted()
                .map { yamlPath ->
                    java.nio.file.Files.newInputStream(yamlPath).use { input ->
                        mapper.readValue<FeatureDeclaration>(input)
                    }
                }
                .toList()
        }
    }

    private fun loadFromJar(resource: java.net.URL): List<FeatureDeclaration> {
        val jarConnection = resource.openConnection() as? java.net.JarURLConnection
        val entryPrefix = (jarConnection?.entryName ?: "features").trimEnd('/') + "/"
        val jarFile = jarConnection?.jarFile ?: run {
            val url = resource.toString()
            val bangIndex = url.indexOf("!/")
            if (!url.startsWith("jar:") || bangIndex == -1) {
                logger.warn("Unable to read jar resource: $resource")
                return emptyList()
            }
            val jarUrl = java.net.URI(url.substring(4, bangIndex)).toURL()
            val jarPath = java.nio.file.Paths.get(jarUrl.toURI()).toFile()
            java.util.jar.JarFile(jarPath)
        }

        val results = mutableListOf<FeatureDeclaration>()
        jarFile.use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) {
                    continue
                }
                val name = entry.name
                if (!name.startsWith(entryPrefix) || !name.endsWith(".yaml")) {
                    continue
                }
                jar.getInputStream(entry).use { input ->
                    results.add(mapper.readValue(input))
                }
            }
        }

        return results
    }
}
