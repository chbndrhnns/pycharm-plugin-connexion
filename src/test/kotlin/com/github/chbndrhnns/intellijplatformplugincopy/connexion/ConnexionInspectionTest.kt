package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import fixtures.TestBase

class ConnexionInspectionTest : TestBase() {

    fun testJsonMissingOperationId() {

        myFixture.configureByText(
            "api.py", """
            def existing_function():
                pass
        """.trimIndent()
        )

        // operationId 'missing_function' does not exist
        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "api",
                    "operationId": "<error descr=\"Connexion: Unresolved operation ID\">missing_function</error>"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonMissingController() {
        // controller 'missing_pkg' does not exist
        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "<error descr=\"Connexion: Unresolved controller reference\">missing_pkg</error>",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testYamlMissingOperationId() {
        myFixture.enableInspections(ConnexionYamlInspection::class.java)

        myFixture.configureByText(
            "api.py", """
            def existing_function():
                pass
        """.trimIndent()
        )

        // operationId 'missing_function' does not exist
        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: api
                  operationId: <error descr="Connexion: Unresolved operation ID">missing_function</error>
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }
}
