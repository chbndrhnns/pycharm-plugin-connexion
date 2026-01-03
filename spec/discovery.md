# Feature Discoverability Analysis & Recommendations

## Current Infrastructure (What We Already Have)

### Strong Foundation

- **50+ features** with `@Feature` annotation containing `id`, `displayName`, `description`, `maturity`, `category`
- **FeatureRegistry** service for programmatic feature access
- **13 feature categories** (TYPE_WRAPPING, ARGUMENTS, CODE_STRUCTURE, PYTEST, etc.)
- **Maturity levels**: STABLE, INCUBATING, HIDDEN, DEPRECATED
- **IncubatingFeatureNotifier** - notifies users on startup about experimental/deprecated features
- **Filterable settings UI** with colored maturity tags
- **Extensive MkDocs documentation** (25 pages)

---

## Recommendations for Discovery Mode

### Option 1: Contextual Feature Tips (Recommended)

Add a "Discovery Mode" toggle that shows contextual tips when features could help:

```kotlin
// In PluginSettingsState.State
@Feature(
    id = "discovery-mode",
    displayName = "Discovery Mode",
    description = "Shows contextual tips when plugin features could help with your current code",
    category = FeatureCategory.OTHER
)
var enableDiscoveryMode: Boolean = false
```

#### Implementation Ideas

1. **Intention Hints** - When an intention is available but not invoked, show a subtle hint:
   - User writes a function with many parameters → hint: "💡 Try 'Introduce Parameter Object' (Alt+Enter)"
   - User has type mismatch → hint: "💡 'Wrap with expected type' can fix this"

2. **Editor Notifications** - Non-intrusive banner at top of editor:
   ```kotlin
   class FeatureDiscoveryEditorNotification : EditorNotificationProvider {
       // Show contextual tips based on file content/context
   }
   ```

3. **Gutter Icons** - Small lightbulb icons in gutter when features apply

### Option 2: Feature Tour / Onboarding

Create a "What's New" or "Feature Tour" dialog:

```kotlin
class FeatureTourDialog : DialogWrapper {
    // Carousel of feature cards with:
    // - Feature name & description
    // - Animated GIF/screenshot
    // - "Try it now" button
    // - "Enable/Disable" toggle
}
```

Show on:
- First plugin install
- After plugin updates with new features
- Via Help menu: "BetterPy Feature Tour"

### Option 3: Smart Suggestions Notification

Extend the existing `IncubatingFeatureNotifier` pattern:

```kotlin
class FeatureDiscoveryNotifier : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!settings.enableDiscoveryMode) return
        
        // Analyze project to suggest relevant features
        val suggestions = analyzeProjectForFeatureSuggestions(project)
        
        if (suggestions.isNotEmpty()) {
            showFeatureSuggestionNotification(project, suggestions)
        }
    }
    
    private fun analyzeProjectForFeatureSuggestions(project: Project): List<FeatureSuggestion> {
        // If project uses pytest → suggest pytest features
        // If project has dataclasses → suggest type wrapping features
        // If project has OpenAPI specs → suggest Connexion features
        // etc.
    }
}
```

### Option 4: "Did You Know?" Tips

Periodic tips shown in IDE (like IntelliJ's "Tip of the Day"):

```kotlin
class BetterPyTipOfTheDay {
    private val tips = listOf(
        Tip("populate-arguments", "Populate all function arguments with Alt+Enter on empty parentheses"),
        Tip("wrap-with-expected-type", "Fix type mismatches instantly with 'Wrap with expected type'"),
        // ... more tips
    )
    
    fun showRandomTip(project: Project) {
        val tip = tips.filter { registry.isFeatureEnabled(it.featureId) }.random()
        // Show notification with tip
    }
}
```

---

## Quick Wins (Low Effort, High Impact)

1. **Add "Learn More" links in Settings UI**
   ```kotlin
   // In FeatureCheckboxBuilder
   if (feature.description.isNotEmpty()) {
       comment(feature.description)
       // Add: link("Learn more") { BrowserUtil.browse(docsUrl) }
   }
   ```

2. **Feature Search in Settings**
   - Add a search box to filter features by name/description
   - Already have `FeatureRegistry.getAllFeatures()` to search

3. **"New Features" badge**
   - Add `since` version to `@Feature` annotation (already exists!)
   - Show "NEW" badge for features added in recent versions

4. **Keyboard Shortcut Hints**
   - Show keyboard shortcuts in feature descriptions
   - "Populate arguments (Alt+Enter when on function call)"

---

## Suggested Implementation Priority

| Priority | Feature | Effort | Impact |
|----------|---------|--------|--------|
| 1 | Discovery Mode toggle in settings | Low | Medium |
| 2 | Contextual editor notifications | Medium | High |
| 3 | "Learn More" links to docs | Low | Medium |
| 4 | Feature search in settings | Low | Medium |
| 5 | Feature Tour dialog | Medium | High |
| 6 | Project-aware suggestions | High | High |
| 7 | Tip of the Day integration | Medium | Medium |

---

## Architecture Suggestion

Create a new package `discovery/` with:

```
settings/
├── discovery/
│   ├── DiscoveryMode.kt           # Settings & state
│   ├── FeatureDiscoveryService.kt # Central service
│   ├── ContextualTipProvider.kt   # Analyzes context for tips
│   ├── FeatureTourDialog.kt       # Onboarding tour
│   └── EditorFeatureHint.kt       # Editor notifications
```

This keeps discovery logic separate from feature implementation while leveraging the existing `FeatureRegistry` infrastructure.

---

## Summary

The plugin already has excellent infrastructure for feature management. The key missing piece is **proactive discovery** - helping users find features when they need them, not just when they browse settings. 

### Recommended Starting Points

1. Add a `enableDiscoveryMode` toggle
2. Implement contextual editor notifications for 3-5 key features
3. Add "Learn More" links to the existing settings UI
