package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextField

class ExtractFixtureDialog(
    private val project: Project,
    private val model: ExtractFixtureModel,
    private val suggestedName: String
) : DialogWrapper(project, true) {

    private var fixtureName: String = suggestedName
    private var extractionMode: ExtractionMode = loadLastExtractionMode()
    private var targetLocation: TargetLocation = loadLastTargetLocation()
    private var fixtureScope: FixtureScope = loadLastFixtureScope()
    private var autouse: Boolean = false
    private var injectionMode: InjectionMode = InjectionMode.PARAMETER

    private lateinit var nameField: Cell<JTextField>
    private lateinit var previewArea: JBTextArea

    init {
        title = "Extract Pytest Fixture"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") {
            nameField = textField()
                .bindText(::fixtureName)
                .focused()
                .columns(COLUMNS_MEDIUM)
                .validationOnInput { field ->
                    validateName(field.text)
                }
                .validationOnApply { field ->
                    validateName(field.text)
                }
        }

        row("Extract as:") {
            comboBox(ExtractionMode.entries)
                .bindItem({ extractionMode }, { it?.let { extractionMode = it } })
                .onChanged { updatePreview() }
        }

        row("Target:") {
            comboBox(TargetLocation.entries)
                .bindItem({ targetLocation }, { it?.let { targetLocation = it } })
                .onChanged { updatePreview() }
        }

        row("Scope:") {
            comboBox(FixtureScope.entries)
                .bindItem({ fixtureScope }, { it?.let { fixtureScope = it } })
                .onChanged { updatePreview() }
        }

        row {
            checkBox("Autouse")
                .bindSelected(::autouse)
                .onChanged { updatePreview() }
        }

        row("Inject via:") {
            comboBox(InjectionMode.entries)
                .bindItem({ injectionMode }, { it?.let { injectionMode = it } })
                .onChanged { updatePreview() }
        }

        if (model.usedFixtures.isNotEmpty()) {
            group("Used fixtures") {
                model.usedFixtures.forEach { fixture ->
                    row {
                        label(fixture.name)
                    }
                }
            }
        }

        group("Preview") {
            row {
                previewArea = JBTextArea().apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    preferredSize = Dimension(400, 150)
                }
                cell(previewArea)
                    .align(Align.FILL)
            }
        }
    }.also {
        updatePreview()
    }

    private fun validateName(name: String): ValidationInfo? {
        if (name.isBlank()) {
            return ValidationInfo("Name cannot be empty", nameField.component)
        }
        if (!name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
            return ValidationInfo("Invalid Python identifier", nameField.component)
        }
        // Check for conflicts with existing fixtures/functions in the file
        val file = model.containingFunction.containingFile as? PyFile
        if (file != null) {
            val existingNames = file.topLevelFunctions.mapNotNull { it.name }
            if (name in existingNames && name != suggestedName) {
                return ValidationInfo("Name '$name' already exists in file", nameField.component)
            }
        }
        return null
    }

    private fun updatePreview() {
        if (!::previewArea.isInitialized) return

        val usedFixtures = model.usedFixtures.map { it.name }
        val paramList = usedFixtures.joinToString(", ")
        val selectedText = model.statements.joinToString("\n") { it.text }

        val preview = buildString {
            // Build the extracted fixture/helper
            if (extractionMode == ExtractionMode.FIXTURE) {
                val decoratorArgs = buildList {
                    if (fixtureScope != FixtureScope.FUNCTION) {
                        add("scope=\"${fixtureScope.value}\"")
                    }
                    if (autouse) {
                        add("autouse=True")
                    }
                }
                val decoratorSuffix = if (decoratorArgs.isNotEmpty()) "(${decoratorArgs.joinToString(", ")})" else ""
                appendLine("@pytest.fixture$decoratorSuffix")
            }
            appendLine("def $fixtureName($paramList):")
            selectedText.lines().forEach { line ->
                appendLine("    $line")
            }
            appendLine("    return ...")

            appendLine()
            appendLine("# Updated source:")

            // Build the updated source function preview
            val containingClass = PsiTreeUtil.getParentOfType(model.containingFunction, PyClass::class.java)
            val classIndent = if (containingClass != null) "    " else ""

            // Show class context if inside a class
            if (containingClass != null) {
                appendLine("class ${containingClass.name}:")
            }

            // Build updated function signature
            val originalParams = model.containingFunction.parameterList.parameters
                .mapNotNull { it.name }
                .filter { it !in usedFixtures || it == "self" || it == "cls" }

            val newParams = if (extractionMode == ExtractionMode.FIXTURE && !autouse) {
                if (injectionMode == InjectionMode.PARAMETER) {
                    originalParams + fixtureName
                } else {
                    originalParams
                }
            } else if (extractionMode == ExtractionMode.HELPER_FUNCTION) {
                originalParams
            } else {
                originalParams
            }

            // Add usefixtures decorator if needed
            if (extractionMode == ExtractionMode.FIXTURE && injectionMode == InjectionMode.USEFIXTURES && !autouse) {
                appendLine("${classIndent}@pytest.mark.usefixtures(\"$fixtureName\")")
            }

            val funcName = model.containingFunction.name ?: "func"
            val asyncPrefix = if (model.containingFunction.isAsync) "async " else ""
            appendLine("${classIndent}${asyncPrefix}def $funcName(${newParams.joinToString(", ")}):")
            appendLine("$classIndent    ...")
        }

        previewArea.text = preview.trimEnd()
    }

    override fun doOKAction() {
        saveSettings()
        super.doOKAction()
    }

    private fun saveSettings() {
        val props = PropertiesComponent.getInstance(project)
        props.setValue(PROP_EXTRACTION_MODE, extractionMode.name)
        props.setValue(PROP_TARGET_LOCATION, targetLocation.name)
        props.setValue(PROP_FIXTURE_SCOPE, fixtureScope.name)
    }

    private fun loadLastExtractionMode(): ExtractionMode {
        val props = PropertiesComponent.getInstance(project)
        val value = props.getValue(PROP_EXTRACTION_MODE)
        return value?.let { runCatching { ExtractionMode.valueOf(it) }.getOrNull() } ?: ExtractionMode.FIXTURE
    }

    private fun loadLastTargetLocation(): TargetLocation {
        val props = PropertiesComponent.getInstance(project)
        val value = props.getValue(PROP_TARGET_LOCATION)
        return value?.let { runCatching { TargetLocation.valueOf(it) }.getOrNull() } ?: TargetLocation.SAME_FILE
    }

    private fun loadLastFixtureScope(): FixtureScope {
        val props = PropertiesComponent.getInstance(project)
        val value = props.getValue(PROP_FIXTURE_SCOPE)
        return value?.let { runCatching { FixtureScope.valueOf(it) }.getOrNull() } ?: FixtureScope.FUNCTION
    }

    fun getOptions(): ExtractFixtureOptions = ExtractFixtureOptions(
        fixtureName = fixtureName,
        extractionMode = extractionMode,
        targetLocation = targetLocation,
        fixtureScope = fixtureScope,
        autouse = autouse,
        injectionMode = injectionMode
    )

    // Keep for backward compatibility with tests
    fun getFixtureName(): String = fixtureName

    fun setFixtureName(name: String) {
        fixtureName = name
    }

    companion object {
        private const val PROP_EXTRACTION_MODE = "betterpy.extractFixture.extractionMode"
        private const val PROP_TARGET_LOCATION = "betterpy.extractFixture.targetLocation"
        private const val PROP_FIXTURE_SCOPE = "betterpy.extractFixture.fixtureScope"
    }
}
