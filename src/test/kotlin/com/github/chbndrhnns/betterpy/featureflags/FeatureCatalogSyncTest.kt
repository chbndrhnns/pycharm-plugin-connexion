package com.github.chbndrhnns.betterpy.featureflags

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import fixtures.TestBase
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class FeatureCatalogSyncTest : TestBase() {

    fun testCatalogMatchesFeatureToggles() {
        val catalog = FeatureCatalogLoader.load()
        val catalogIds = catalog.map { it.id }.toSet()
        val toggleIds = collectFeatureToggleIds()

        assertEquals(toggleIds, catalogIds)
    }

    fun testCatalogFeaturesExistInCode() {
        val catalog = FeatureCatalogLoader.load()
        val catalogIds = catalog.map { it.id }.toSet()
        val toggleIds = collectFeatureToggleIds()

        val missingInCode = catalogIds - toggleIds
        assertTrue(
            "Catalog contains features missing from code: $missingInCode",
            missingInCode.isEmpty()
        )
    }

    fun testCatalogFilesMatchSchema() {
        val schemaStream = requireNotNull(
            FeatureCatalogSyncTest::class.java.classLoader.getResourceAsStream("features/schema.json")
        )
        val jsonMapper = ObjectMapper()
        val yamlMapper = ObjectMapper(YAMLFactory())
        val schemaNode = schemaStream.use { jsonMapper.readTree(it) }
        val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode)

        FeatureCatalogLoader.load().forEach { feature ->
            val node = yamlMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(feature)
            val errors = schema.validate(node)
            assertTrue("Schema errors in ${feature.id}: $errors", errors.isEmpty())
        }
    }

    fun testPluginXmlRegistrationsCoveredByCatalog() {
        val catalog = FeatureCatalogLoader.load()
        val catalogRegistrations = catalog
            .flatMap { it.registrations }
            .map { it.type to it.className }
            .toSet()

        val pluginRegistrations = loadPluginXmlRegistrations()
        val allowedUnmapped = setOf(
            "com.github.chbndrhnns.betterpy.bootstrap.listeners.ProtocolCacheInvalidationListener",
            "com.github.chbndrhnns.betterpy.bootstrap.startup.PythonVersionStartupActivity",
            "com.github.chbndrhnns.betterpy.core.index.PyClassMembersFileIndex",
            "com.github.chbndrhnns.betterpy.core.index.PyLambdaFileIndex",
            "com.github.chbndrhnns.betterpy.core.index.PytestFixtureFileIndex",
            "com.github.chbndrhnns.betterpy.featureflags.IncubatingFeatureNotifier",
            "com.github.chbndrhnns.betterpy.featureflags.PluginSettingsState",
            "com.github.chbndrhnns.betterpy.features.statusbar.BetterPyStatusBarWidgetFactory"
        )

        val filteredPluginRegistrations = pluginRegistrations
            .filterNot { (_, className) -> className in allowedUnmapped }
            .toSet()

        val missingInCatalog = filteredPluginRegistrations - catalogRegistrations
        assertTrue(
            "Missing catalog entries for plugin.xml registrations: $missingInCatalog",
            missingInCatalog.isEmpty()
        )

        val missingInPluginXml = catalogRegistrations - pluginRegistrations.toSet()
        assertTrue(
            "Catalog registrations missing in plugin.xml: $missingInPluginXml",
            missingInPluginXml.isEmpty()
        )
    }

    private fun collectFeatureToggleIds(): Set<String> {
        val result = mutableSetOf<String>()
        val visited = mutableSetOf<KClass<*>>()

        fun scan(klass: KClass<*>) {
            if (!visited.add(klass)) {
                return
            }
            klass.memberProperties.forEach { prop ->
                val annotation = prop.findAnnotation<Feature>()
                    ?: prop.javaField?.getAnnotation(Feature::class.java)

                if (annotation != null) {
                    result.add(annotation.id)
                    return@forEach
                }

                val nestedClass = prop.returnType.classifier as? KClass<*>
                if (nestedClass != null && hasFeatureAnnotatedProperties(nestedClass)) {
                    scan(nestedClass)
                }
            }
        }

        scan(PluginSettingsState.State::class)
        return result
    }

    private fun hasFeatureAnnotatedProperties(klass: KClass<*>): Boolean {
        return klass.memberProperties.any { prop ->
            prop.findAnnotation<Feature>() != null || prop.javaField?.getAnnotation(Feature::class.java) != null
        }
    }

    private fun loadPluginXmlRegistrations(): List<Pair<String, String>> {
        val inputStream = requireNotNull(
            FeatureCatalogSyncTest::class.java.classLoader.getResourceAsStream("META-INF/plugin.xml")
        )
        val document = parseXml(inputStream)
        val elements = document.getElementsByTagName("*")
        val registrations = mutableSetOf<Pair<String, String>>()

        for (i in 0 until elements.length) {
            val element = elements.item(i) as? Element ?: continue
            val tag = element.tagName
            for (attribute in listOf("implementationClass", "implementation", "className")) {
                if (element.hasAttribute(attribute)) {
                    val className = element.getAttribute(attribute).trim()
                    if (className.startsWith("com.github.chbndrhnns.betterpy")) {
                        registrations.add(tag to className)
                    }
                }
            }

            if (tag == "className") {
                val className = element.textContent.trim()
                val parent = element.parentNode as? Element
                if (parent != null && className.startsWith("com.github.chbndrhnns.betterpy")) {
                    registrations.add(parent.tagName to className)
                }
            }
        }

        return registrations.toList()
    }

    private fun parseXml(inputStream: InputStream) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)

    // YAML resource listing is handled by FeatureCatalogLoader; schema validation reuses parsed declarations.
}
