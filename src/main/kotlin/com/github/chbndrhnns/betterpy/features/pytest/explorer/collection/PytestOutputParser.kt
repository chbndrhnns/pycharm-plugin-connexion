package com.github.chbndrhnns.betterpy.features.pytest.explorer.collection

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.diagnostic.Logger

object PytestOutputParser {

    private val LOG = Logger.getInstance(PytestOutputParser::class.java)

    private val QUIET_LINE_REGEX = Regex(
        """^(?<path>[^:]+)::(?:(?<cls>[^:]+)::)?(?<func>\S+?)(?:\[(?<param>[^\]]+)])?$"""
    )

    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun parseQuietOutput(stdout: String, workDir: String): List<RawCollectedItem> {
        LOG.debug("Parsing quiet output (${stdout.length} chars, workDir=$workDir)")
        return stdout.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("=") && !it.startsWith("-") }
            .mapNotNull { line ->
                QUIET_LINE_REGEX.matchEntire(line.trim())?.let {
                    RawCollectedItem(
                        nodeId = line.trim(),
                        type = ItemType.FUNCTION,
                    )
                }
            }
            .toList()
            .also { LOG.debug("Parsed ${it.size} items from quiet output") }
    }

    fun parseJsonOutput(stderr: String): CollectionJsonData? {
        val marker = "===PYTEST_COLLECTION_JSON==="
        val startIdx = stderr.indexOf(marker)
        val endIdx = stderr.lastIndexOf(marker)
        if (startIdx == -1 || endIdx <= startIdx) {
            LOG.debug("No JSON marker found in stderr (${stderr.length} chars)")
            return null
        }

        val json = stderr.substring(startIdx + marker.length, endIdx)
        return try {
            mapper.readValue(json, CollectionJsonData::class.java).also {
                LOG.debug("Parsed JSON: ${it.tests.size} tests, ${it.fixtures.size} fixtures")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse JSON collection data", e)
            null
        }
    }
}

data class CollectionJsonData(
    val tests: List<JsonTestItem>,
    val fixtures: Map<String, JsonFixtureItem>,
)

data class JsonTestItem(
    val nodeid: String,
    val module: String,
    val cls: String?,
    val name: String,
    val fixtures: List<String>,
)

data class JsonFixtureItem(
    val name: String,
    val scope: String,
    val baseid: String,
    val func_name: String,
    val module: String,
    val argnames: List<String>,
    val autouse: Boolean,
)
