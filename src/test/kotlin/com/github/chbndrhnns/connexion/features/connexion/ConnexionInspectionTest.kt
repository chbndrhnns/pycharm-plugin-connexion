package com.github.chbndrhnns.connexion.features.connexion

import fixtures.TestBase

class ConnexionInspectionTest : TestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ConnexionJsonInspection::class.java)
    }

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
                    "operationId": "<error descr="Connexion: Unresolved operation ID">missing_function</error>"
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
                    "x-openapi-router-controller": "<error descr="Connexion: Unresolved controller reference">missing_pkg</error>",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonQualifiedOperationIdWithControllerDoesNotFlagRelativeModule() {
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
                    "operationId": "api.list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonQualifiedOperationIdWithPathControllerDoesNotFlagOperationId() {
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
                  "x-openapi-router-controller": "my_pkg",
                  "get": {
                    "operationId": "api.list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonQualifiedOperationIdWithRootControllerDoesNotFlagOperationId() {
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
              "x-openapi-router-controller": "my_pkg",
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "api.list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonOperationIdModulePrefixWithControllerDoesNotFlagOperationId() {
        myFixture.tempDirFixture.createFile("my_pkg/api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "my_pkg",
                    "operationId": "api"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonNestedOperationIdModulePrefixWithControllerDoesNotFlagOperationId() {
        myFixture.tempDirFixture.createFile("my_pkg/api/pets.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "my_pkg",
                    "operationId": "api.pets"
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

    fun testYamlQualifiedOperationIdWithControllerDoesNotFlagRelativeModule() {
        myFixture.enableInspections(ConnexionYamlInspection::class.java)

        myFixture.tempDirFixture.createFile(
            "my_pkg/api.py", """
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
                  x-openapi-router-controller: my_pkg
                  operationId: api.list_pets
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testYamlOperationIdModulePrefixWithControllerDoesNotFlagOperationId() {
        myFixture.enableInspections(ConnexionYamlInspection::class.java)

        myFixture.tempDirFixture.createFile("my_pkg/api.py", "")

        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: my_pkg
                  operationId: api
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testYamlNestedOperationIdModulePrefixWithControllerDoesNotFlagOperationId() {
        myFixture.enableInspections(ConnexionYamlInspection::class.java)

        myFixture.tempDirFixture.createFile("my_pkg/api/pets.py", "")

        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: my_pkg
                  operationId: api.pets
        """.trimIndent()
        )

        myFixture.checkHighlighting(true, false, true)
    }

    fun testJsonIgnoredWithoutController() {
        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "missing_function"
                  }
                }
              }
            }
        """.trimIndent()
        )

        // No highlighting errors expected because there is no controller specified
        myFixture.checkHighlighting(true, false, true)
    }

    fun testYamlIgnoredWithoutController() {
        myFixture.enableInspections(ConnexionYamlInspection::class.java)

        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  operationId: missing_function
        """.trimIndent()
        )

        // No highlighting errors expected because there is no controller specified
        myFixture.checkHighlighting(true, false, true)
    }
}
