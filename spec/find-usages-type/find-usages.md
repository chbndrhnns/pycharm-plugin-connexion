### Approach

The "Go to Declaration" feature normally navigates directly to a definition. If you are looking to "only show references in type annotations," you are likely referring to the **"Find Usages"** (or "Show Usages") dialog, which lists references to a symbol. To extend the filter bar in this dialog, you need to implement a `UsageFilteringRule`.

This involves three main steps:
1.  **Create a Toggle Action**: A button for the filter bar that toggles a "Show only type annotations" state.
2.  **Create a Usage Filtering Rule**: A rule that checks the state and hides/shows usages based on whether they are inside a type annotation.
3.  **Register the Rule Provider**: Plug your rule into the standard Usage View.

### Implementation

#### 1. Define the Action and State
First, define a `ToggleAction` that controls a boolean flag. This flag determines whether the filter is active. You can use `PropertiesComponent` for simple persistence.

```java
package com.example.plugin;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

public class PyShowTypeAnnotationsAction extends ToggleAction {
    private static final String PROPERTY_KEY = "PyShowOnlyTypeAnnotations";

    public static boolean isShowOnlyTypeAnnotations(@NotNull Project project) {
        return PropertiesComponent.getInstance(project).getBoolean(PROPERTY_KEY, false);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        return project != null && isShowOnlyTypeAnnotations(project);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        Project project = e.getProject();
        if (project != null) {
            PropertiesComponent.getInstance(project).setValue(PROPERTY_KEY, state, false);
            // Notify the Usage View to update its rules
            project.getMessageBus().syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
        }
    }
}
```

#### 2. Implement the Filtering Rule
Create a rule that implements `UsageFilteringRule`. This rule filters usages when the action is enabled.

**Key Logic:** We use `PyTypingTypeProvider.isInsideTypeHint` to correctly identify if a reference is part of a type hint (including Python 3 annotations and type comments).

```java
package com.example.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usages.Usage;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageFilteringRule;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyTypeAnnotationFilteringRule implements UsageFilteringRule {
    private final Project myProject;

    public PyTypeAnnotationFilteringRule(@NotNull Project project) {
        myProject = project;
    }

    @Override
    public @NotNull String getActionId() {
        // Must match the ID in plugin.xml
        return "Python.ShowTypeAnnotations";
    }

    @Override
    public boolean isVisible(@NotNull Usage usage) {
        // If the filter is NOT active, show everything (return true)
        if (!PyShowTypeAnnotationsAction.isShowOnlyTypeAnnotations(myProject)) {
            return true;
        }

        // If filter IS active, only show usages inside type hints
        if (usage instanceof PsiElementUsage) {
            PsiElement element = ((PsiElementUsage) usage).getElement();
            if (element != null) {
                TypeEvalContext context = TypeEvalContext.userInitiated(myProject, element.getContainingFile());
                return PyTypingTypeProvider.isInsideTypeHint(element, context);
            }
        }
        return false;
    }
}
```

#### 3. Register the Provider
Implement `UsageFilteringRuleProvider` to expose your rule to the platform.

```java
package com.example.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Collection;

public class PyTypeAnnotationFilteringRuleProvider implements UsageFilteringRuleProvider {
    @Override
    public @NotNull Collection<? extends UsageFilteringRule> getApplicableRules(@NotNull Project project) {
        return Collections.singletonList(new PyTypeAnnotationFilteringRule(project));
    }
}
```

#### 4. Update `plugin.xml`
Register the action and the extension. The action ID must match the one returned by `getActionId()` in your rule.

```xml
<idea-plugin>
    <depends>com.intellij.modules.python</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- Register the rule provider -->
        <usageFilteringRuleProvider implementation="com.example.plugin.PyTypeAnnotationFilteringRuleProvider"/>
    </extensions>

    <actions>
        <!-- Register the toggle action -->
        <action id="Python.ShowTypeAnnotations" 
                class="com.example.plugin.PyShowTypeAnnotationsAction"
                text="Only Show References in Type Annotations"
                description="Filter usages to only show those occurring within type hints"
                icon="AllIcons.Nodes.Type"> <!-- Replace with suitable icon -->
        </action>
    </actions>
</idea-plugin>
```

### How it Works
*   When the user opens the "Find Usages" dialog, the `UsageView` asks all `usageFilteringRuleProvider` extensions for rules.
*   Your provider returns `PyTypeAnnotationFilteringRule`.
*   The rule provides the ID `Python.ShowTypeAnnotations`.
*   The `UsageView` looks up the action with that ID and adds it to the filter toolbar.
*   When the user toggles the button, `UsageFilteringRuleProvider.RULES_CHANGED` is fired, causing the `UsageView` to re-evaluate `isVisible()` for all usages.
