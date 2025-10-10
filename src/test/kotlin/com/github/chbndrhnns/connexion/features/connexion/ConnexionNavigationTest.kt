package com.github.chbndrhnns.connexion.features.connexion

import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class ConnexionNavigationTest : TestBase() {

    // Tests disabled due to environment crash (SIGABRT) when running with required plugins
    fun _testYamlSpecToImplementation() {
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
                  operationId: <caret>list_pets
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is PyFunction)
        assertEquals("list_pets", (element as PyFunction).name)
    }

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
                    "operationId": "<caret>list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is PyFunction)
        assertEquals("list_pets", (element as PyFunction).name)
    }

    fun _testCodeToSpec() {
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

        myFixture.configureByText(
            "api.py", """
            def <caret>list_pets():
                pass
        """.trimIndent()
        )

        myFixture.performEditorAction("Connexion.GoToOpenApiOperation")

        val file = myFixture.file
        assertEquals("openapi.yaml", file.name)
        val text = file.text
        assertTrue(text.contains("operationId: list_pets"))
    }

    fun _testFullyQualifiedOperationId() {
        myFixture.configureByText(
            "pkg/api.py", """
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
                  operationId: <caret>pkg.api.list_pets
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is PyFunction)
        assertEquals("list_pets", (element as PyFunction).name)
    }

    fun testQualifiedOperationIdWithController() {
        myFixture.tempDirFixture.createFile(
            "my_pkg/api.py", """
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
                    "x-openapi-router-controller": "my_pkg",
                    "operationId": "<caret>api.list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is PyFunction)
        assertEquals("list_pets", (element as PyFunction).name)
    }
}
