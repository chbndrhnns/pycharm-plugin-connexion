# Refactoring plugin.xml for Better Organization

## Overview

The current `plugin.xml` is 329 lines with multiple extension types mixed together. This document explores options for splitting it into smaller, more maintainable files.

## Current Structure Analysis

### Extension Count by Category

| Category | Count | Lines (approx) |
|----------|-------|----------------|
| Intention Actions | 22 | 33-147 |
| Local Inspections | 4 | 178-216 |
| Actions | 12 | 252-326 |
| Project Listeners | 2 | 245-249 |
| Application Configurables (Settings) | 5 | 149-176 |
| Completion Contributors | 2 | 13-16 |
| PSI Reference Contributors | 2 | 17-20 |
| Pythonid Extensions | 3 | 236-243 |
| Other (service, notification, startup, etc.) | ~8 | scattered |

### Logical Groupings Identified

1. **Intentions** (~22 entries) - Type mismatch, Custom types, Arguments, Refactorings, Visibility, Type hints, Dictionaries, Exceptions, Local variables, Pytest, Abstract methods
2. **Inspections** (~4 entries) - PyMissingInDunderAll, PyPrivateModuleImport, PyDataclassMissing, PyAbstractMethodNotImplementedInChild
3. **Actions** (~12 entries) - Copy actions, Refactoring menu actions, Test tree actions, Editor popup actions
4. **Settings/Configurables** (~5 entries) - Main settings, Intentions, Inspections, Editor Actions, Imports & Project View
5. **Python-specific Extensions** (~3 entries) - canonicalPathProvider, importCandidateProvider
6. **Infrastructure** - Services, listeners, startup activities, notification groups

---

## Available Mechanisms for Splitting plugin.xml

### 1. XInclude (`<xi:include>`)

**Description:** XML Inclusions (XInclude) is a W3C standard that allows including content from other XML files. IntelliJ Platform fully supports this mechanism.

**Syntax:**
```xml
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <!-- Core plugin metadata -->
    <id>com.example.plugin</id>
    <name>My Plugin</name>
    
    <!-- Include separate files -->
    <xi:include href="/META-INF/intentions.xml"/>
    <xi:include href="/META-INF/inspections.xml"/>
    <xi:include href="/META-INF/actions.xml"/>
</idea-plugin>
```

**Included file format:**
```xml
<!-- intentions.xml -->
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <intentionAction>
            <className>com.example.MyIntention</className>
            <category>My Category</category>
        </intentionAction>
        <!-- more intentions... -->
    </extensions>
</idea-plugin>
```

**Key Points:**
- The `href` attribute uses paths relative to the resources root (e.g., `/META-INF/file.xml`)
- Included files must be valid XML with `<idea-plugin>` as root
- The namespace declaration `xmlns:xi="http://www.w3.org/2001/XInclude"` is required in the main plugin.xml
- IntelliJ Platform uses this extensively (see `PlatformLangPlugin.xml` which includes 30+ files)

**Optional Fallback:**
```xml
<xi:include href="/META-INF/optional-feature.xml">
    <xi:fallback/>
</xi:include>
```
This allows the include to silently fail if the file doesn't exist.

### 2. Optional Dependency Config Files (`<depends config-file="...">`)

**Description:** When declaring optional plugin dependencies, you can specify a separate configuration file that is only loaded when the dependency is available.

**Syntax:**
```xml
<depends optional="true" config-file="python-extensions.xml">com.intellij.modules.python</depends>
```

**Use Case:** This is specifically for features that depend on optional plugins. The config file is only loaded if the dependency plugin is installed.

**Key Points:**
- The `config-file` attribute is only valid when `optional="true"`
- The config file path is relative to META-INF
- Useful for IDE-specific features (e.g., Python-only features in a multi-IDE plugin)

---

## Recommended File Structure

### Option A: By Feature Category (Recommended)

```
src/main/resources/META-INF/
├── plugin.xml                    # Core metadata, dependencies, includes
├── intentions.xml                # All 22 intention actions
├── inspections.xml               # All 4 local inspections
├── actions.xml                   # All 12 actions and action groups
├── settings.xml                  # All 5 application configurables
├── python-extensions.xml         # Pythonid namespace extensions
└── infrastructure.xml            # Services, listeners, startup, notifications
```

**Estimated file sizes:**
- `plugin.xml`: ~40 lines (metadata + includes)
- `intentions.xml`: ~120 lines
- `inspections.xml`: ~45 lines
- `actions.xml`: ~80 lines
- `settings.xml`: ~35 lines
- `python-extensions.xml`: ~15 lines
- `infrastructure.xml`: ~30 lines

### Option B: By Functional Domain

```
src/main/resources/META-INF/
├── plugin.xml                    # Core metadata, dependencies, includes
├── type-system.xml               # Type-related intentions (wrap/unwrap, custom types)
├── refactoring.xml               # Refactoring intentions and actions
├── testing.xml                   # Pytest intentions, test tree actions
├── code-quality.xml              # Inspections, visibility intentions
├── navigation.xml                # Search everywhere, structure view
└── settings.xml                  # All configurables
```

### Option C: Minimal Split

```
src/main/resources/META-INF/
├── plugin.xml                    # Core metadata + smaller sections
├── intentions.xml                # All intentions (largest section)
└── actions.xml                   # All actions
```

---

## Implementation Example

### Main plugin.xml (after refactoring)

```xml
<!-- Plugin Configuration File -->
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <id>com.github.chbndrhnns.betterpycom.github.chbndrhnns.betterpy</id>
    <name>Python DDD Toolkit</name>
    <vendor>chbndrhnns</vendor>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.python</depends>

    <resource-bundle>messages.MyBundle</resource-bundle>

    <!-- Include organized extension files -->
    <xi:include href="/META-INF/intentions.xml"/>
    <xi:include href="/META-INF/inspections.xml"/>
    <xi:include href="/META-INF/actions.xml"/>
    <xi:include href="/META-INF/settings.xml"/>
    <xi:include href="/META-INF/python-extensions.xml"/>
    <xi:include href="/META-INF/infrastructure.xml"/>
</idea-plugin>
```

### Example: intentions.xml

```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <!-- Type Mismatch Intentions -->
        <intentionAction>
            <className>com.github.chbndrhnns.betterpy.features.intentions.WrapWithExpectedTypeIntention</className>
            <category>Type mismatch</category>
        </intentionAction>
        <!-- ... more intentions ... -->
    </extensions>
</idea-plugin>
```

---

## Pros and Cons

### Pros of Splitting

1. **Improved Maintainability**
   - Easier to find and modify specific extensions
   - Smaller files are easier to review in PRs
   - Clear separation of concerns

2. **Better Organization**
   - Logical grouping makes the codebase more understandable
   - New contributors can find relevant configuration faster
   - Reduces cognitive load when working on specific features

3. **Reduced Merge Conflicts**
   - Changes to intentions won't conflict with changes to actions
   - Multiple developers can work on different areas simultaneously

4. **Follows IntelliJ Platform Conventions**
   - JetBrains uses this pattern extensively in their own plugins
   - Well-supported and documented approach

### Cons of Splitting

1. **Increased File Count**
   - More files to manage in the project
   - Need to remember which file contains what

2. **Potential for Errors**
   - Missing includes could cause features to not load
   - XML syntax errors in any file affect the whole plugin

3. **Slight Complexity Increase**
   - Developers need to understand the include mechanism
   - IDE support for navigating between files may vary

4. **Build/Packaging Considerations**
   - All included files must be properly packaged in the JAR
   - Need to verify includes work correctly after build

---

## Implementation Considerations

### Testing After Refactoring

1. **Verify all extensions load:**
   - Run the plugin and check all intentions appear in Settings > Intentions
   - Verify all inspections appear in Settings > Inspections
   - Check all actions are available in menus

2. **Automated verification:**
   - The existing test suite should catch missing registrations
   - Consider adding a smoke test that verifies extension point counts

### IDE Support

- IntelliJ IDEA fully supports navigation between included files
- Ctrl+Click on `href` values navigates to the included file
- Plugin DevKit provides validation for included files

### Migration Strategy

1. **Phase 1:** Extract intentions (largest section) to separate file
2. **Phase 2:** Extract actions
3. **Phase 3:** Extract remaining sections
4. **Phase 4:** Verify and test thoroughly

### Rollback Plan

If issues arise, reverting is straightforward:
1. Copy content from included files back to main plugin.xml
2. Remove xi:include elements
3. Remove the xmlns:xi namespace declaration

---

## Recommendation

**Recommended approach: Option A (By Feature Category)**

This provides the best balance of:
- Clear organization by extension type
- Manageable number of files (7 total)
- Easy to locate specific configurations
- Follows IntelliJ Platform conventions

Start with extracting `intentions.xml` as it's the largest section (22 entries, ~120 lines), then proceed with other sections incrementally.
