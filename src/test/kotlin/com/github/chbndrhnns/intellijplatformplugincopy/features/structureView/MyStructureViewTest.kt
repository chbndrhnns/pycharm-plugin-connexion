package com.github.chbndrhnns.intellijplatformplugincopy.features.structureView

import com.github.chbndrhnns.intellijplatformplugincopy.featureflags.PluginSettingsState
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import fixtures.TestBase

class MyStructureViewTest : TestBase() {

    fun testPrivateMembersFilter() {
        // 1. Setup a Python file with private members
        val file = myFixture.configureByText(
            "test.py",
            """
            class MyClass:
                def public_method(self): pass
                def _private_method(self): pass
            """.trimIndent()
        )

        // 2. Get the builder (this mimics what the IDE does)
        val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file)
        assertNotNull(builder)

        // Ensure it is our wrapper (TreeBasedStructureViewBuilder)
        assertTrue("Builder should be TreeBasedStructureViewBuilder", builder is TreeBasedStructureViewBuilder)
        val treeBuilder = builder as TreeBasedStructureViewBuilder

        // 3. Create the model
        val model = treeBuilder.createStructureViewModel(myFixture.editor)

        // 4. Verify our filter is present
        val filters = model.filters
        val myFilter = filters.find { it is MyPrivateMembersFilter }
        assertNotNull("My custom filter should be registered", myFilter)

        // 5. Test the filter logic manually
        val root = model.root

        var classElement: TreeElement? = null
        for (child in root.children) {
            if (child.presentation.presentableText == "MyClass") {
                classElement = child
                break
            }
        }

        assertNotNull("Class 'MyClass' not found in structure view root", classElement)

        val methods = classElement!!.children
        val publicMethod = findMethod(methods, "public_method")
        val privateMethod = findMethod(methods, "_private_method")

        assertTrue("Public method should be visible", myFilter!!.isVisible(publicMethod))
        assertFalse("Private method should be hidden by default filter logic", myFilter.isVisible(privateMethod))
    }

    private fun findMethod(methods: Array<TreeElement>, name: String): TreeElement {
        return methods.firstOrNull {
            val text = it.presentation.presentableText ?: return@firstOrNull false
            text == name || text.startsWith("$name(")
        }
            ?: throw RuntimeException("Method not found: $name. Available: ${methods.joinToString { it.presentation.presentableText ?: "null" }}")
    }

    fun testPrivateMembersFilterDisabled() {
        // 0. Disable the setting
        PluginSettingsState.instance().state.enableStructureViewPrivateMembersFilter = false

        try {
            // 1. Setup a Python file with private members
            val file = myFixture.configureByText(
                "test.py",
                """
                class MyClass:
                    def public_method(self): pass
                    def _private_method(self): pass
                """.trimIndent()
            )

            // 2. Get the builder
            val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file)
            assertNotNull(builder)
            assertTrue(builder is TreeBasedStructureViewBuilder)
            val treeBuilder = builder as TreeBasedStructureViewBuilder

            // 3. Create the model
            val model = treeBuilder.createStructureViewModel(myFixture.editor)

            // 4. Verify our filter is NOT present
            val filters = model.filters
            val myFilter = filters.find { it is MyPrivateMembersFilter }
            assertNull("My custom filter should NOT be registered when setting is disabled", myFilter)

        } finally {
            // Restore setting (though TestBase.setUp should handle it for other tests, it is good practice)
            PluginSettingsState.instance().state.enableStructureViewPrivateMembersFilter = true
        }
    }
}
