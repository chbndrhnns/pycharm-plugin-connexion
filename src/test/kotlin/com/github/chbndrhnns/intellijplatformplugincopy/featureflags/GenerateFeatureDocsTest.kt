package com.github.chbndrhnns.intellijplatformplugincopy.featureflags

import fixtures.TestBase
import java.io.File

/**
 * Test class for generating feature documentation.
 *
 * This test is excluded from the normal test suite by default.
 * Run it explicitly with:
 *
 * ```bash
 * ./gradlew generateFeatureDocs
 * ```
 *
 * Or directly:
 * ```bash
 * ./gradlew test --tests "*.GenerateFeatureDocsTest" -PrunDocGenTest=true
 * ```
 *
 * The output directory can be customized via the `docgen.output.dir` system property.
 */
class GenerateFeatureDocsTest : TestBase() {

    private val outputDir: File by lazy {
        // Use system property if set, otherwise use current working directory
        val dir = System.getProperty("docgen.output.dir")
            ?: System.getProperty("user.dir")
            ?: "."
        File(dir)
    }

    /**
     * Generates markdown documentation for all features and writes it to docs/features/feature-reference.md
     */
    fun testGenerateMarkdownDocs() {
        val markdown = FeatureDocumentationGenerator.generateMarkdown(includeHidden = false)

        val outputFile = outputDir.resolve("docs/features/feature-reference.md")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(markdown)

        println("Generated markdown documentation at: ${outputFile.absolutePath}")
        println("Feature count: ${FeatureRegistry.instance().getVisibleFeatures().size}")
    }

    /**
     * Generates JSON documentation for all features and writes it to docs/features/features.json
     */
    fun testGenerateJsonDocs() {
        val json = FeatureDocumentationGenerator.generateJson(includeHidden = false)

        val outputFile = outputDir.resolve("docs/features/features.json")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(json)

        println("Generated JSON documentation at: ${outputFile.absolutePath}")
    }

    /**
     * Generates a simple text list of all features (including hidden) and prints to console.
     */
    fun testGenerateSimpleList() {
        val list = FeatureDocumentationGenerator.generateSimpleList(includeHidden = true)
        println(list)
    }

    /**
     * Generates all documentation formats at once.
     */
    fun testGenerateAllDocs() {
        testGenerateMarkdownDocs()
        testGenerateJsonDocs()
        println("\n--- Simple List ---\n")
        testGenerateSimpleList()
    }
}
