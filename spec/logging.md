Yes, you can programmatically enable debugging for specific classes or packages in a PyCharm plugin. This is typically done using the `LogLevelConfigurationManager` service, which manages persistent debug log categories (the same ones you can configure manually via **Help | Diagnostic Tools | Debug Log Settings...**).

### Recommended Approach: `LogLevelConfigurationManager`

The `LogLevelConfigurationManager` is the standard way to manage debug logging levels programmatically in the IntelliJ Platform (including PyCharm). It ensures that the settings are applied immediately and persisted across IDE restarts.

#### 1. Add Log Categories
To enable `DEBUG` or `TRACE` level logging for a specific class or package, use the `addCategories` method.

**In Kotlin:**
```kotlin
import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager

fun enableDebugLogging() {
    val manager = LogLevelConfigurationManager.getInstance()
    val category = LogCategory("com.your.plugin.package.YourClass", DebugLogLevel.DEBUG)
    manager.addCategories(listOf(category))
}
```

**In Java:**
```java
import com.intellij.diagnostic.logs.DebugLogLevel;
import com.intellij.diagnostic.logs.LogCategory;
import com.intellij.diagnostic.logs.LogLevelConfigurationManager;
import java.util.Collections;

public void enableDebugLogging() {
    LogLevelConfigurationManager manager = LogLevelConfigurationManager.getInstance();
    LogCategory category = new LogCategory("com.your.plugin.package.YourClass", DebugLogLevel.DEBUG);
    manager.addCategories(Collections.singletonList(category));
}
```

#### 2. Log Level Options
The `DebugLogLevel` enum provides three levels:
- `DebugLogLevel.DEBUG`: Maps to `java.util.logging.Level.FINE`.
- `DebugLogLevel.TRACE`: Maps to `java.util.logging.Level.FINER`.
- `DebugLogLevel.ALL`: Maps to `java.util.logging.Level.ALL`.

#### 3. Important Considerations
- **Category Name**: The category name should match the name used when initializing your logger (usually the fully qualified class name or package name). Note that the IntelliJ platform often prepends a `#` to categories in `idea.log`, but when using `LogLevelConfigurationManager`, you should provide the base name; the manager handles both variations automatically.
- **Persistence**: Using `addCategories` or `setCategories` on the `LogLevelConfigurationManager` will persist these settings in `options/log-categories.xml` within the IDE's configuration directory.
- **Immediate Effect**: The manager applies the level changes to the underlying logging backend (usually JUL - `java.util.logging`) immediately upon calling these methods.

### Alternative (Transient): `com.intellij.openapi.diagnostic.Logger`

If you only want to change the log level for the current session without persisting it to the IDE configuration, you can use the `Logger` instance directly:

```kotlin
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.LogLevel

val LOG = Logger.getInstance("com.your.plugin.package.YourClass")
LOG.setLevel(LogLevel.DEBUG)
```

However, `LogLevelConfigurationManager` is the preferred way for a settings-style dialog where the user expects the choice to be remembered.