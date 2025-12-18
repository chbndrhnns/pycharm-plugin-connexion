package com.github.chbndrhnns.intellijplatformplugincopy.connexion

import com.jetbrains.python.psi.PyFunction
import fixtures.TestBase

class ConnexionSrcPrefixTest : TestBase() {

    fun testSrcPrefixControllerResolution() {
        myFixture.tempDirFixture.createFile(
            "src/pkg/api.py", """
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
                    "x-openapi-router-controller": "src.pkg.api",
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
}
