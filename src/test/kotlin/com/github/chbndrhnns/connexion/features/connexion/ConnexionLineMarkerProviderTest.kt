package com.github.chbndrhnns.connexion.features.connexion

import fixtures.TestBase

class ConnexionLineMarkerProviderTest : TestBase() {

    fun testJsonSpecToImplementation() {
        myFixture.configureByText(
            "api.py", """
            def list_pets():
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        val gutters = myFixture.findAllGutters()
        val marker = gutters.find { it.tooltipText == "Navigate to implementation" }
        assertNotNull("Should have line marker to implementation", marker)
    }

    fun testCodeToSpec() {
        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "api.py", """
            def list_pets():
                pass
        """.trimIndent()
        )

        val gutters = myFixture.findAllGutters()
        val marker = gutters.find { it.tooltipText == "Navigate to OpenAPI spec" }
        assertNotNull("Should have line marker to spec", marker)
    }

    fun testYamlSpecToImplementation() {
        myFixture.configureByText(
            "api.py", """
            def list_pets():
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: api
                  operationId: list_pets
        """.trimIndent()
        )

        val gutters = myFixture.findAllGutters()
        val marker = gutters.find { it.tooltipText == "Navigate to implementation" }
        assertNotNull("Should have line marker to implementation", marker)
    }
}
