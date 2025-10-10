package com.github.chbndrhnns.connexion.features.connexion

import com.intellij.codeInsight.completion.CompletionType
import fixtures.TestBase

class ConnexionCompletionAndRenameTest : TestBase() {

    fun testOperationIdCompletionWithController() {
        myFixture.configureByText(
            "api.py", """
            def list_pets(): pass
            def get_pet(): pass
            def _private(): pass
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
        assertNotNull(strings)
        assertContainsElements(strings!!, "list_pets", "get_pet")
        assertDoesntContain(strings, "_private")
    }

    fun testRenameFunctionUpdatesOperationId() {
        myFixture.configureByText(
            "api.py", """
            def list_pets(): pass
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

        // Rename function in api.py
        myFixture.configureByFile("api.py")
        val function = myFixture.findElementByText("list_pets", com.jetbrains.python.psi.PyFunction::class.java)
        myFixture.renameElement(function, "get_all_pets")

        myFixture.checkResult(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: api
                  operationId: get_all_pets
        """.trimIndent(), true
        )
    }

    fun testRenameFunctionUpdatesQualifiedOperationId() {
        myFixture.tempDirFixture.createFile(
            "pkg/api.py", """
            def list_pets(): pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  operationId: pkg.api.list_pets
        """.trimIndent()
        )

        // Rename function
        myFixture.configureByFile("pkg/api.py")
        val function = myFixture.findElementByText("list_pets", com.jetbrains.python.psi.PyFunction::class.java)
        myFixture.renameElement(function, "get_all_pets")

        myFixture.checkResult(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  operationId: pkg.api.get_all_pets
        """.trimIndent(), true
        )
    }

    fun testControllerCompletion() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")
        myFixture.tempDirFixture.createFile("other.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "<caret>",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        // With hierarchical completion, we expect "pkg" (directory) and "other" (file)
        assertContainsElements(strings!!, "pkg", "other")
        assertDoesntContain(strings, "pkg.api", "api")
    }

    fun testRenameController() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "pkg.api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.configureByFile("pkg/api.py")
        val file = myFixture.file
        myFixture.renameElement(file, "new_api.py")

        myFixture.checkResult(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "pkg.new_api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent(), true
        )
    }

    fun testControllerCompletionAtRoot() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")
        myFixture.tempDirFixture.createFile("root_mod.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "<caret>",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(strings, "pkg", "root_mod")
        assertDoesntContain(strings, "api")
    }

    fun testControllerCompletionSecondLevel() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")
        myFixture.tempDirFixture.createFile("pkg/sub/mod.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "pkg.<caret>",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings ?: emptyList()
        assertContainsElements(strings, "api", "sub")
        assertDoesntContain(strings, "pkg", "mod")
    }

    fun testResolveDirectory() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "p<caret>kg.api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        val element = myFixture.getElementAtCaret()
        assertTrue(element is com.intellij.psi.PsiDirectory)
        assertEquals("pkg", (element as com.intellij.psi.PsiDirectory).name)
    }

    fun testRenameDirectory() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "pkg.api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

        // Find directory "pkg" and rename it
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val baseDir = com.intellij.openapi.roots.ProjectRootManager.getInstance(project).contentRoots.first()
        val pkgDir = baseDir.findChild("pkg")
        val pkgPsiDir = psiManager.findDirectory(pkgDir!!)!!

        myFixture.renameElement(pkgPsiDir, "renamed_pkg")

        myFixture.checkResult(
            """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "renamed_pkg.api",
                    "operationId": "list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )
    }
}
