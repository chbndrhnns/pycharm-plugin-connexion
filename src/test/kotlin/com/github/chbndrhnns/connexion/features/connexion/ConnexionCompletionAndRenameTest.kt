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

    fun testJsonQualifiedOperationIdCompletionUsesControllerPrefix() {
        myFixture.tempDirFixture.createFile(
            "my_pkg/api.py", """
            def list_pets(): pass
            def get_pet(): pass
            def _private(): pass
        """.trimIndent()
        )
        myFixture.tempDirFixture.createFile(
            "api.py", """
            def root_function(): pass
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
                    "operationId": "api.<caret>"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertContainsElements(strings!!, "api.list_pets", "api.get_pet")
        assertDoesntContain(strings, "_private", "root_function")
    }

    fun testOperationIdModuleCompletionUsesControllerPrefix() {
        myFixture.tempDirFixture.createFile("my_pkg/api.py", "")
        myFixture.tempDirFixture.createFile("my_pkg/sub/nested_api.py", "")
        myFixture.tempDirFixture.createFile("api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "my_pkg",
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
        assertContainsElements(strings!!, "api", "sub")
        assertDoesntContain(strings, "nested_api")
    }

    fun testNestedOperationIdModuleCompletionUsesControllerPrefix() {
        myFixture.tempDirFixture.createFile("my_pkg/sub/nested_api.py", "")
        myFixture.tempDirFixture.createFile("my_pkg/sub/other_api.py", "")
        myFixture.tempDirFixture.createFile("sub/root_api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "x-openapi-router-controller": "my_pkg",
                    "operationId": "sub.<caret>"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertContainsElements(strings!!, "sub.nested_api", "sub.other_api")
        assertDoesntContain(strings, "sub.root_api")
    }

    fun testYamlQualifiedOperationIdCompletionUsesControllerPrefix() {
        myFixture.tempDirFixture.createFile(
            "my_pkg/api.py", """
            def list_pets(): pass
            def get_pet(): pass
            def _private(): pass
        """.trimIndent()
        )
        myFixture.tempDirFixture.createFile(
            "api.py", """
            def root_function(): pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "openapi.yaml", """
            openapi: 3.0.0
            paths:
              /pets:
                get:
                  x-openapi-router-controller: my_pkg
                  operationId: api.<caret>
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertContainsElements(strings!!, "api.list_pets", "api.get_pet")
        assertDoesntContain(strings, "_private", "root_function")
    }

    fun testQualifiedOperationIdCompletionWithoutController() {
        myFixture.tempDirFixture.createFile(
            "pkg/api.py", """
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
                    "operationId": "pkg.api.<caret>"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertContainsElements(strings!!, "pkg.api.list_pets", "pkg.api.get_pet")
        assertDoesntContain(strings, "_private")
    }

    fun testQualifiedOperationIdModuleCompletionWithoutController() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")
        myFixture.tempDirFixture.createFile("pkg/sub/nested_api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "pkg.<caret>"
                  }
                }
              }
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC)
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings)
        assertContainsElements(strings!!, "pkg.api", "pkg.sub")
        assertDoesntContain(strings, "nested_api")
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
                  x-openapi-router-controller: dummy
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
                  x-openapi-router-controller: dummy
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
    fun testRenameControllerDirectoryInOperationId() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "pkg.api.list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )

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
                    "operationId": "renamed_pkg.api.list_pets"
                  }
                }
              }
            }
        """.trimIndent()
        )
    }

    fun testRenameControllerModuleInOperationId() {
        myFixture.tempDirFixture.createFile("pkg/api.py", "")

        myFixture.configureByText(
            "openapi.json", """
            {
              "openapi": "3.0.0",
              "paths": {
                "/pets": {
                  "get": {
                    "operationId": "pkg.api.list_pets"
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
                    "operationId": "pkg.new_api.list_pets"
                  }
                }
              }
            }
        """.trimIndent(), true
        )
    }
}
