package com.github.chbndrhnns.betterpy.features.documentation

import com.jetbrains.python.documentation.PyDocumentationSettings
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.psi.PyClass
import fixtures.TestBase

class DataclassQuickInfoProviderTest : TestBase() {

    fun testBasicDataclass() {
        myFixture.configureByText(
            "test.py", """
            from dataclasses import dataclass
            
            @dataclass
            class Person:
                name: str
                age: int
        """.trimIndent()
        )

        val pyClass = myFixture.findElementByText("Person", PyClass::class.java)
        val provider = DataclassQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(pyClass)

        assertNotNull("Quick info should not be null", quickInfo)
        assertEquals("name: str\nage: int", quickInfo)
        // We don't check for @dataclass here as it's provided by the standard provider
        // and getQuickInfo only returns the enhanced fields info
    }

    fun testPydanticModel() {
        // Mock pydantic
        myFixture.addFileToProject(
            "pydantic.py", """
            class BaseModel:
                pass
        """.trimIndent()
        )

        myFixture.configureByText(
            "test_pydantic.py", """
            import pydantic
            
            class User(pydantic.BaseModel):
                id: int
                name: str
        """.trimIndent()
        )

        val pyClass = myFixture.findElementByText("User", PyClass::class.java)
        val provider = DataclassQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(pyClass)

        assertNotNull("Pydantic model should be recognized", quickInfo)
        assertEquals("id: int\nname: str", quickInfo)
    }

    fun testDataclassDefinitionHover() {
        myFixture.configureByText(
            "test.py", """
            from dataclasses import dataclass
            
            @dataclass
            class Person:
                name: str
                age: int
        """.trimIndent()
        )

        // Find the offset of "class Person" - "Person" part
        val offset = myFixture.editor.document.text.indexOf("class Person") + 6 // "class " length
        val element = myFixture.file.findElementAt(offset)

        assertNotNull("Element at caret should not be null", element)

        val provider = DataclassQuickInfoProvider()
        val quickInfo = provider.getQuickInfo(element!!)

        assertNotNull("Quick info should not be null when hovering definition name", quickInfo)
        assertEquals("name: str\nage: int", quickInfo)
    }

    fun testGenerateDoc() {
        myFixture.configureByText(
            "test_doc.py", """
            from dataclasses import dataclass

            @dataclass
            class DocTest:
                '''My docstring'''
                field: int
        """.trimIndent()
        )

        val pyClass = myFixture.findElementByText("DocTest", PyClass::class.java)

        // Set docstring format to PLAIN to avoid "Unittest placeholder"
        val settings = PyDocumentationSettings.getInstance(myFixture.module)
        val oldFormat = settings.format
        settings.format = DocStringFormat.PLAIN
        try {
            val provider = DataclassQuickInfoProvider()
            val doc = provider.generateDoc(pyClass, null)

            assertNotNull("Generated documentation should not be null", doc)

            val expectedDoc = """
                <html>
                <body>
                <div class="bottom">
                    <icon src="AllIcons.Nodes.Package"/>&nbsp;<code><a href="psi_element://#module#test_doc">test_doc</a></code></div>
                <div class="definition">
                    <pre><span style="color:#808000;">@dataclass</span><br/><span
                            style="color:#000080;font-weight:bold;">class </span><span style="color:#000000;">DocTest</span></pre>
                </div>
                <br/><span>Fields:</span><br/>
                <span>field</span><span>: </span><span>int</span><br/><br/><span>Dataclass options:</span><br/> init, repr, eq
                <div class="content">My docstring</div>
                </body>
                </html>
            """.trimIndent().replace(Regex(">\\s+<"), "><").replace("\n", "").replace(Regex("\\s+"), " ")

            assertEquals(
                expectedDoc,
                doc!!.replace(Regex(">\\s+<"), "><").replace("\n", "").replace(Regex("\\s+"), " ")
            )
        } finally {
            settings.format = oldFormat
        }
    }

    fun testGenerateDocSnapshot() {
        myFixture.configureByText(
            "test_snapshot.py", """
            from dataclasses import dataclass

            @dataclass
            class Person:
                '''Person docstring'''
                name: str
                age: int
                email: str = "default@example.com"
        """.trimIndent()
        )

        val pyClass = myFixture.findElementByText("Person", PyClass::class.java)

        // Set docstring format to PLAIN to avoid "Unittest placeholder"
        val settings = PyDocumentationSettings.getInstance(myFixture.module)
        val oldFormat = settings.format
        settings.format = DocStringFormat.PLAIN
        try {
            val provider = DataclassQuickInfoProvider()
            val doc = provider.generateDoc(pyClass, null)

            assertNotNull("Generated documentation should not be null", doc)

            // Assert on complete HTML output snapshot
            val expectedDoc = """
                <html><body><div class="bottom"><icon src="AllIcons.Nodes.Package"/>&nbsp;<code><a href="psi_element://#module#test_snapshot">test_snapshot</a></code></div><div class="definition"><pre><span style="color:#808000;">@dataclass</span><br/><span style="color:#000080;font-weight:bold;">class </span><span style="color:#000000;">Person</span></pre></div><br/><span>Fields:</span><br/>  <span>name</span><span>: </span><span>str</span><br/>  <span>age</span><span>: </span><span>int</span><br/>  <span>email</span><span>: </span><span>str</span><span> = </span>&quot;default@example.com&quot;<br/><br/><span>Dataclass options:</span><br/>  init, repr, eq<div class="content">Person docstring</div></body></html>
            """.trimIndent().replace(Regex(">\\s+<"), "><").replace("\n", "").replace(Regex("\\s+"), " ")

            assertEquals(
                expectedDoc,
                doc!!.replace(Regex(">\\s+<"), "><").replace("\n", "").replace(Regex("\\s+"), " ")
            )
        } finally {
            settings.format = oldFormat
        }
    }
}
