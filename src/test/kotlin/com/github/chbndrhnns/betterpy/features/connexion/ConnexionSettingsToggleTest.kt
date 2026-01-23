package com.github.chbndrhnns.betterpy.features.connexion

import com.intellij.codeInsight.completion.CompletionType
import fixtures.SettingsTestUtils.withPluginSettings
import fixtures.TestBase

class ConnexionSettingsToggleTest : TestBase() {

    fun testInspectionDisabledViaSettings() {
        // Normally this would trigger an error because 'missing_function' does not exist.
        // With inspection disabled, no error should be highlighted.
        withPluginSettings({ enableConnexionInspections = false }) {
            myFixture.enableInspections(ConnexionJsonInspection::class.java)

            myFixture.configureByText(
                "api.py", """
                def existing_function():
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
                        "operationId": "missing_function"
                      }
                    }
                  }
                }
            """.trimIndent()
            )

            myFixture.checkHighlighting(true, false, true)
        }
    }

    fun testCompletionDisabledViaSettings() {
        withPluginSettings({ enableConnexionCompletion = false }) {
            myFixture.configureByText(
                "api.py", """
                def list_pets(): pass
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
                        "operationId": "<caret>"
                      }
                    }
                  }
                }
            """.trimIndent()
            )

            myFixture.complete(CompletionType.BASIC)
            val strings = myFixture.lookupElementStrings
            // With references disabled, "list_pets" should not be suggested
            if (strings != null) {
                assertDoesntContain(strings, "list_pets")
            }
        }
    }
}
