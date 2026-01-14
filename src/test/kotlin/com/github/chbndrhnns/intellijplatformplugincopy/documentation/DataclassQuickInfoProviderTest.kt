package com.github.chbndrhnns.intellijplatformplugincopy.documentation

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
        val provider = DataclassQuickInfoProvider()
        val doc = provider.generateDoc(pyClass, null)

        assertNotNull(doc)
        // Extract just the fields portion for comparison (doc includes standard provider content)
        assertTrue("Doc should contain 'field: int'. Actual: $doc", doc!!.contains("field: int"))
        // Note: In test environment, PythonDocumentationProvider might return "Unittest placeholder"
        // instead of the actual docstring due to indexing/PSI limitations with configureByText.
        // We assume standard provider works correctly in production.
    }

    fun testGenerateDocSnapshot() {
        myFixture.configureByText(
            "test_snapshot.py", """
            from dataclasses import dataclass

            @dataclass
            class Person:
                name: str
                age: int
                email: str = "default@example.com"
        """.trimIndent()
        )

        val pyClass = myFixture.findElementByText("Person", PyClass::class.java)
        val provider = DataclassQuickInfoProvider()
        val doc = provider.generateDoc(pyClass, null)

        assertNotNull("Generated documentation should not be null", doc)

        // Assert on complete HTML output snapshot
        val expectedHtml = """<br>
<span>Fields:</span>
<br>
  <span>name</span><span>: </span><span>str</span><br>
  <span>age</span><span>: </span><span>int</span><br>
  <span>email</span><span>: </span><span>str</span><span> = </span>"default@example.com"<br>
<br>
<span>Dataclass options:</span>
<br>
  init, repr, eq"""

        assertEquals(expectedHtml, doc)
    }
}
