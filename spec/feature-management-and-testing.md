# Feature Management: Development vs. Published Versions

This document outlines strategies for managing different feature sets between development and published versions of the plugin, along with corresponding testing approaches.

## Problem Statement

When publishing the plugin, we want to have a different set of features enabled:
- **Development builds**: All features including experimental/incubating ones
- **Published/Release builds**: Only stable, production-ready features

## Feature Management Approaches

### Approach 1: Use `FeatureMaturity` Levels (Recommended - Already Built-In)

The plugin already has a `FeatureMaturity` enum in `FeatureMetadata.kt`:

```kotlin
enum class FeatureMaturity {
    STABLE,      // Feature is stable and ready for production use
    INCUBATING,  // Feature is in development, may change or be removed
    HIDDEN,      // Feature is hidden from UI, only accessible via registry/config
    DEPRECATED   // Feature is deprecated and will be removed
}
```

**Strategy:**
- Mark development-only features as `INCUBATING` or `HIDDEN`
- At build time or runtime, filter features based on maturity level
- For published versions, only enable `STABLE` features by default

**Implementation:**

1. Add a build-time property to `gradle.properties`:
   ```properties
   pluginBuildType=development  # or "release"
   ```

2. Generate a resource file during build that indicates the build type

3. In `PluginSettingsState`, check the build type and adjust default values accordingly

### Approach 2: Build Variants via Gradle Properties

Add a Gradle property to control which features are included:

```kotlin
// In build.gradle.kts
val isDevelopmentBuild = providers.gradleProperty("developmentBuild")
    .orElse("false").get().toBoolean()

// Generate a BuildConfig-like file
tasks.register("generateBuildConfig") {
    val outputDir = layout.buildDirectory.dir("generated/buildConfig")
    outputs.dir(outputDir)
    doLast {
        outputDir.get().asFile.mkdirs()
        File(outputDir.get().asFile, "BuildConfig.kt").writeText("""
            package com.github.chbndrhnns.intellijplatformplugincopy
            object BuildConfig {
                const val IS_DEVELOPMENT = $isDevelopmentBuild
            }
        """.trimIndent())
    }
}
```

Then in your code:
```kotlin
// In PluginSettingsState.State defaults
var enableExperimentalFeature: Boolean = BuildConfig.IS_DEVELOPMENT
```

### Approach 3: Runtime Detection via Plugin Version

Use the plugin version to determine the build type:

```kotlin
// In PluginConstants.kt or a new BuildInfo object
object BuildInfo {
    val isDevelopment: Boolean by lazy {
        val version = PluginManagerCore.getPlugin(
            PluginId.getId("com.github.chbndrhnns.intellijplatformplugincopy")
        )?.version ?: "0.0.0"
        // Development versions might use -SNAPSHOT, -dev, or specific patterns
        version.contains("-dev") || version.contains("-SNAPSHOT") || version == "0.0.1"
    }
}
```

### Approach 4: Separate plugin.xml Files

Create two plugin descriptor files:
- `plugin.xml` - for release (only stable features)
- `plugin-dev.xml` - for development (all features)

In `build.gradle.kts`:
```kotlin
tasks.named<ProcessResources>("processResources") {
    val isDev = providers.gradleProperty("developmentBuild").orElse("false").get().toBoolean()
    if (!isDev) {
        exclude("**/plugin-dev.xml")
    }
    // Or use filesMatching to swap files
}
```

### Approach 5: Feature Flags via IntelliJ Registry (For Internal Testing)

IntelliJ has a built-in Registry for experimental features:

```kotlin
// Register in plugin.xml
<registryKey key="betterpy.experimental.features" 
             defaultValue="false" 
             description="Enable experimental BetterPy features"/>

// Check at runtime
val experimentalEnabled = Registry.`is`("betterpy.experimental.features")
```

This is great for internal testing but not for controlling published vs. development builds.

### Recommended Combined Approach

Combine **Approach 1 + Approach 2**:

1. **Continue using `FeatureMaturity`** to categorize features
2. **Add a build-time flag** that determines default behavior:
   - Development builds: All `STABLE` + `INCUBATING` features enabled by default
   - Release builds: Only `STABLE` features enabled by default

3. **Modify `PluginSettingsState`** to respect build type:

```kotlin
data class State(
    // Features with INCUBATING maturity would have their default 
    // determined by BuildConfig.IS_DEVELOPMENT
    @Feature(
        id = "experimental-feature",
        maturity = FeatureMaturity.INCUBATING,
        // ...
    )
    var enableExperimentalFeature: Boolean = BuildConfig.IS_DEVELOPMENT,
)
```

4. **In your CI/CD pipeline:**
   - Local development: `./gradlew runIde -PdevelopmentBuild=true`
   - Publishing: `./gradlew publishPlugin` (defaults to release mode)

### Quick Implementation Checklist

1. Add `developmentBuild` property to `gradle.properties` (default: `false`)
2. Create a `BuildConfig` object generated at build time
3. Update feature defaults in `PluginSettingsState.State` to use `BuildConfig.IS_DEVELOPMENT` for incubating features
4. Optionally, add UI indication in settings panel showing "(Development)" for incubating features

This approach gives you:
- Clear separation between stable and experimental features
- Easy toggling during development
- Safe defaults for published versions
- User ability to opt-in to experimental features even in release builds

---

## Testing Strategy: Matching Test Sets to Feature Maturity Levels

### Approach 1: JUnit 4 Categories (Recommended for This Setup)

Since the project uses JUnit 4, use **Categories** to tag tests by maturity level:

#### Step 1: Create Category Marker Interfaces

```kotlin
// src/test/kotlin/fixtures/categories/TestCategories.kt
package fixtures.categories

/** Tests for stable, production-ready features */
interface StableTest

/** Tests for incubating/experimental features */
interface IncubatingTest

/** Tests for hidden features (internal/registry-only) */
interface HiddenTest

/** Tests for deprecated features */
interface DeprecatedTest
```

#### Step 2: Annotate Test Classes

```kotlin
import org.junit.experimental.categories.Category
import fixtures.categories.IncubatingTest

@Category(IncubatingTest::class)
class MyExperimentalFeatureTest : TestBase() {
    // ...
}
```

#### Step 3: Configure Gradle to Filter by Category

```kotlin
// In build.gradle.kts
tasks.test {
    val testCategory = project.findProperty("testCategory")?.toString() ?: "all"
    
    useJUnit {
        when (testCategory) {
            "stable" -> includeCategories("fixtures.categories.StableTest")
            "incubating" -> includeCategories("fixtures.categories.IncubatingTest")
            "release" -> {
                // For release builds: only stable tests
                includeCategories("fixtures.categories.StableTest")
            }
            "development" -> {
                // For dev builds: stable + incubating
                includeCategories(
                    "fixtures.categories.StableTest",
                    "fixtures.categories.IncubatingTest"
                )
            }
            // "all" runs everything (default)
        }
    }
}
```

#### Usage:
```bash
# Run only stable tests (for release validation)
./gradlew test -PtestCategory=stable

# Run stable + incubating (for development)
./gradlew test -PtestCategory=development

# Run all tests
./gradlew test
```

### Approach 2: Package-Based Organization

Organize tests by maturity in separate packages:

```
src/test/kotlin/
├── stable/           # Tests for stable features
│   └── ...
├── incubating/       # Tests for experimental features
│   └── ...
└── fixtures/         # Test infrastructure
```

Then in `build.gradle.kts`:

```kotlin
tasks.test {
    val isDevelopmentBuild = project.findProperty("developmentBuild")?.toString()?.toBoolean() ?: true
    
    if (!isDevelopmentBuild) {
        // Release builds: exclude incubating tests
        exclude("**/incubating/**")
    }
}
```

### Approach 3: Annotation-Based with Custom Test Runner

Create a custom annotation that mirrors `FeatureMaturity`:

```kotlin
// src/test/kotlin/fixtures/TestMaturity.kt
package fixtures

import com.github.chbndrhnns.intellijplatformplugincopy.settings.FeatureMaturity

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TestMaturity(val level: FeatureMaturity)
```

```kotlin
// Usage
@TestMaturity(FeatureMaturity.INCUBATING)
class MyExperimentalFeatureTest : TestBase() {
    // ...
}
```

Then use a custom JUnit Rule or Runner to skip tests based on build type.

### Approach 4: Integrate with Existing Build Type Flag

Building on the `BuildConfig.IS_DEVELOPMENT` approach:

```kotlin
// In TestBase.kt or a new base class
abstract class TestBase : MyPlatformTestCase() {
    @Before
    fun checkTestMaturity() {
        val maturity = this::class.findAnnotation<TestMaturity>()?.level
        if (maturity == FeatureMaturity.INCUBATING && !BuildConfig.IS_DEVELOPMENT) {
            Assume.assumeTrue("Skipping incubating test in release build", false)
        }
    }
}
```

### Recommended Combined Strategy

Combine **Approach 1 (Categories)** with the existing Gradle property pattern:

```kotlin
// build.gradle.kts
val isDevelopmentBuild = providers.gradleProperty("developmentBuild")
    .orElse("true").get().toBoolean()

tasks.test {
    // Existing exclusion
    if (!project.hasProperty("runDocGenTest")) {
        exclude("**/GenerateFeatureDocsTest.class")
    }
    
    // New: Filter by maturity for release builds
    if (!isDevelopmentBuild) {
        useJUnit {
            excludeCategories("fixtures.categories.IncubatingTest")
            excludeCategories("fixtures.categories.HiddenTest")
        }
    }
}
```

### CI/CD Integration

```yaml
# GitHub Actions example
jobs:
  test-development:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew test -PdevelopmentBuild=true
  
  test-release:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew test -PdevelopmentBuild=false
  
  publish:
    needs: test-release
    steps:
      - run: ./gradlew publishPlugin -PdevelopmentBuild=false
```

---

## Summary Table

| Approach | Pros | Cons |
|----------|------|------|
| **JUnit Categories** | Native JUnit 4 support, flexible filtering | Requires annotating each test class |
| **Package-based** | Simple, visual organization | May require moving existing tests |
| **Custom Annotation** | Mirrors FeatureMaturity exactly | Requires custom runner/rule |
| **Build flag + Assume** | Minimal Gradle changes | Tests still run but skip (slower) |

The **JUnit Categories** approach is most aligned with the existing infrastructure and provides the cleanest separation between development and release test suites.
