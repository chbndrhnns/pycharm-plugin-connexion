package com.github.chbndrhnns.intellijplatformplugincopy.features.connexion

import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class ConnexionQuoteTest : TestBase() {

    fun testYamlUnquoted() {
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

    fun testYamlSingleQuoted() {
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
                  x-openapi-router-controller: 'api'
                  operationId: '<caret>list_pets'
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is PyFunction)
        assertEquals("list_pets", (element as PyFunction).name)
    }

    fun testYamlDoubleQuoted() {
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
                  x-openapi-router-controller: "api"
                  operationId: "<caret>list_pets"
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is PyFunction)
        assertEquals("list_pets", (element as PyFunction).name)
    }

    fun testLineMarkersWithQuotes() {
        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: "api"
                  operationId: 'list_pets'
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
        assertNotNull("Should have line marker to spec even with mixed quotes", marker)
    }
}
