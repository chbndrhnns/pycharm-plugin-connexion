# Generating Feature Documentation

The `FeatureDocumentationGenerator` generates documentation for all plugin features from their `@Feature` annotation metadata. This is useful for maintaining up-to-date feature references and for external tooling.

## Overview

The generator is located at:
```
src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/settings/FeatureDocumentationGenerator.kt
```

It provides three output formats:

| Method | Description | Output |
|--------|-------------|--------|
| `generateMarkdown()` | Full documentation with tables, badges, and YouTrack links | Markdown |
| `generateJson()` | Machine-readable feature data | JSON |
| `generateSimpleList()` | Quick reference list with status indicators | Plain text |

All methods accept an optional `includeHidden: Boolean = false` parameter to include hidden features.

## Running the Generator

Since `FeatureDocumentationGenerator` uses `FeatureRegistry.instance()`, it requires the IntelliJ Platform application context. There are several ways to run it:

### Option 1: From a Test (Recommended for Development)

Create a test that generates the documentation:

```kotlin
package com.github.chbndrhnns.intellijplatformplugincopy.settings

import fixtures.TestBase
import java.io.File

class GenerateFeatureDocsTest : TestBase() {

    fun testGenerateMarkdownDocs() {
        val markdown = FeatureDocumentationGenerator.generateMarkdown(includeHidden = false)
        
        // Print to console
        println(markdown)
        
        // Or write to file
        File("docs/features/feature-reference.md").writeText(markdown)
    }

    fun testGenerateJsonDocs() {
        val json = FeatureDocumentationGenerator.generateJson(includeHidden = false)
        File("docs/features/features.json").writeText(json)
    }

    fun testGenerateSimpleList() {
        val list = FeatureDocumentationGenerator.generateSimpleList(includeHidden = true)
        println(list)
    }
}
```

Run with:
```bash
./gradlew test --tests "com.github.chbndrhnns.intellijplatformplugincopy.settings.GenerateFeatureDocsTest.testGenerateMarkdownDocs"
```

### Option 2: From the IDE Plugin (Runtime)

You can invoke the generator from within the running plugin, for example via an action:

```kotlin
class GenerateFeatureDocsAction : AnAction("Generate Feature Docs") {
    override fun actionPerformed(e: AnActionEvent) {
        val markdown = FeatureDocumentationGenerator.generateMarkdown()
        
        // Copy to clipboard
        CopyPasteManager.getInstance().setContents(StringSelection(markdown))
        
        // Or show in a dialog
        Messages.showInfoMessage(e.project, "Documentation generated and copied to clipboard", "Feature Docs")
    }
}
```

### Option 3: From runIde Task

1. Start the plugin in development mode:
   ```bash
   ./gradlew runIde
   ```

2. Open the Kotlin scratch file or use **Evaluate Expression** in the debugger:
   ```kotlin
   com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureDocumentationGenerator.generateMarkdown()
   ```

## Output Examples

### Markdown Output

The markdown output includes:

- **Summary table** with feature counts by maturity status
- **Features grouped by category** with:
    - Maturity badges (🧪 Incubating, ⚠️ Deprecated, 👁️ Hidden)
    - Description
    - Metadata table (ID, Settings Key, Since version)
    - Related YouTrack issues with links
- **YouTrack Issue Index** listing all referenced issues

### JSON Output

```json
{
  "features": [
    {
      "id": "populate-arguments",
      "displayName": "Populate arguments",
      "description": "Automatically fills in function call arguments...",
      "maturity": "STABLE",
      "category": "ARGUMENTS",
      "propertyName": "enablePopulateArgumentsIntention",
      "since": "",
      "removeIn": "",
      "youtrackIssues": [],
      "enabled": true
    }
  ]
}
```

### Simple List Output

```
BetterPy Features
=================

Arguments:
  ✓ Create local variable
  ✓ Make parameter mandatory
  ✓ Make parameter optional
  ✓ Populate arguments

Code Structure:
  ✓ Add exception capture
  ✓ Change visibility
  ✓ Convert Callable to Protocol [INCUBATING]
  ...
```

## Integrating with CI/CD

To automatically generate and commit documentation on release:

```yaml
# .github/workflows/docs.yml
- name: Generate feature docs
  run: ./gradlew test --tests "*.GenerateFeatureDocsTest.testGenerateMarkdownDocs"

- name: Commit docs
  run: |
    git add docs/features/feature-reference.md
    git commit -m "docs: update feature reference" || true
```

## Adding Feature Metadata

To ensure your features appear in the generated documentation, annotate them in `PluginSettingsState.State`:

```kotlin
@Feature(
    id = "my-feature",
    displayName = "My Feature",
    description = "What this feature does",
    maturity = FeatureMaturity.INCUBATING,
    category = FeatureCategory.CODE_STRUCTURE,
    youtrackIssues = ["PY-12345"],
    since = "1.2.0"
)
var enableMyFeature: Boolean = true
```

See [FeatureMetadata.kt](../src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/settings/FeatureMetadata.kt) for all available annotation parameters.
