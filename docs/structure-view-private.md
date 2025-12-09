### Plan to Modify Structure View in an External Plugin

To modify the structure view of an existing language (like Python) and add a custom toggle (Filter), you need to intercept the creation of the `StructureViewModel` and inject your own logic. Since you cannot modify the source code of the Python plugin, you must use the `lang.psiStructureViewFactory` extension point with high precedence to wrap the existing implementation.

#### 1. Registration (`plugin.xml`)
Register a `lang.psiStructureViewFactory` for Python. Use `order="first"` to ensure your factory is invoked instead of (or before) the default one.

```xml
<extensions defaultExtensionNs="com.intellij">
    <lang.psiStructureViewFactory language="Python"
                                  implementationClass="com.example.plugin.MyPythonStructureViewFactory"
                                  order="first"/>
</extensions>
```

#### 2. Implementation Logic
The implementation involves a chain of wrappers:
1.  **Factory Wrapper**: `MyPythonStructureViewFactory` finds the original Python factory and delegates the creation of the `StructureViewBuilder`.
2.  **Builder Wrapper**: Wraps the original builder to intercept `createStructureViewModel`.
3.  **Model Wrapper**: Wraps the original `StructureViewModel`. It overrides `getFilters()` to add your toggle and `getRoot()` if you need to modify the tree nodes themselves.

#### 3. Code Implementation

**A. The Factory and Builder**
```java
package com.example.plugin;

import com.intellij.ide.structureView.*;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyPythonStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        // 1. Get the original builder from the default Python implementation
        // We use LanguageStructureViewBuilder to find all implementations and pick the one that isn't ours
        StructureViewBuilder originalBuilder = LanguageStructureViewBuilder.INSTANCE
                .allForLanguage(psiFile.getLanguage()).stream()
                .filter(factory -> !(factory instanceof MyPythonStructureViewFactory))
                .findFirst()
                .map(factory -> factory.getStructureViewBuilder(psiFile))
                .orElse(null);

        if (originalBuilder == null) return null;

        // 2. Wrap the builder
        return new StructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                StructureViewModel originalModel = originalBuilder.createStructureViewModel(editor);
                return new MyStructureViewModelWrapper(originalModel);
            }
        };
    }
}
```

**B. The Model Wrapper**
This wrapper delegates all calls to the original model but injects the custom filter.

```java
package com.example.plugin;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class MyStructureViewModelWrapper implements StructureViewModel {
    private final StructureViewModel delegate;

    public MyStructureViewModelWrapper(StructureViewModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NotNull Filter[] getFilters() {
        Filter[] originalFilters = delegate.getFilters();
        Filter[] newFilters = new Filter[originalFilters.length + 1];
        System.arraycopy(originalFilters, 0, newFilters, 0, originalFilters.length);
        newFilters[originalFilters.length] = new MyPrivateMembersFilter(); // Add your custom toggle
        return newFilters;
    }

    @Override
    public @NotNull StructureViewTreeElement getRoot() {
        // If you need to modify the tree structure (nodes), wrap the root here:
        // return new MyTreeElementWrapper(delegate.getRoot());
        return delegate.getRoot();
    }

    // Delegate all other methods to 'delegate'
    @Override public Grouper @NotNull [] getGroupers() { return delegate.getGroupers(); }
    @Override public Sorter @NotNull [] getSorters() { return delegate.getSorters(); }
    @Override public Object getCurrentEditorElement() { return delegate.getCurrentEditorElement(); }
    @Override public void addEditorPositionListener(@NotNull FileEditorPositionListener listener) { delegate.addEditorPositionListener(listener); }
    @Override public void removeEditorPositionListener(@NotNull FileEditorPositionListener listener) { delegate.removeEditorPositionListener(listener); }
    @Override public void addModelListener(@NotNull ModelListener modelListener) { delegate.addModelListener(modelListener); }
    @Override public void removeModelListener(@NotNull ModelListener modelListener) { delegate.removeModelListener(modelListener); }
    @Override public void dispose() { Disposer.dispose(delegate); }
    @Override public boolean shouldEnterElement(Object element) { return delegate.shouldEnterElement(element); }
}
```

**C. The Filter (Toggle)**
```java
package com.example.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;

public class MyPrivateMembersFilter implements Filter {
    @Override
    public boolean isVisible(TreeElement treeNode) {
        // Implement logic to check if node is private
        // Example: check the name of the element
        ItemPresentation presentation = treeNode.getPresentation();
        String name = presentation.getPresentableText();
        // Simple heuristic for Python private members
        return name == null || !name.startsWith("_"); 
    }

    @Override
    public boolean isReverted() {
        return true; // true = "Show Private Members" (checked = show, unchecked = hide)
    }

    @Override
    public @NotNull ActionPresentation getPresentation() {
        return new ActionPresentationData("Show Private Members", "Show or hide private members", AllIcons.Nodes.Private);
    }

    @Override
    public @NotNull String getName() {
        return "MY_SHOW_PRIVATE_MEMBERS";
    }
}
```

#### 4. Tests

Use `BasePlatformTestCase` to verify the filter logic.

```java
package com.example.plugin;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;

public class MyStructureViewTest extends BasePlatformTestCase {

    public void testPrivateMembersFilter() {
        // 1. Setup a Python file with private members
        PsiFile file = myFixture.configureByText("test.py", 
                "class MyClass:\n" +
                "    def public_method(self): pass\n" +
                "    def _private_method(self): pass\n");

        // 2. Get the builder (this mimics what the IDE does)
        StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(file);
        assertNotNull(builder);

        // 3. Create the model
        StructureViewModel model = builder.createStructureViewModel(null);
        
        // 4. Verify our filter is present
        Filter[] filters = model.getFilters();
        Filter myFilter = Arrays.stream(filters)
                .filter(f -> f instanceof MyPrivateMembersFilter)
                .findFirst()
                .orElse(null);
        assertNotNull("My custom filter should be registered", myFilter);

        // 5. Test the filter logic manually
        // Note: We don't need to test the UI, just the logic that 'isVisible' returns correct values
        StructureViewTreeElement root = model.getRoot();
        StructureViewTreeElement classElement = Arrays.stream(root.getChildren())
                .filter(e -> "MyClass".equals(e.getPresentation().getPresentableText()))
                .findFirst().orElseThrow();

        StructureViewTreeElement[] methods = classElement.getChildren();
        StructureViewTreeElement publicMethod = findMethod(methods, "public_method");
        StructureViewTreeElement privateMethod = findMethod(methods, "_private_method");

        assertTrue("Public method should be visible", myFilter.isVisible(publicMethod));
        assertFalse("Private method should be hidden by default filter logic", myFilter.isVisible(privateMethod));
    }

    private StructureViewTreeElement findMethod(StructureViewTreeElement[] methods, String name) {
        return Arrays.stream(methods)
                .filter(e -> name.equals(e.getPresentation().getPresentableText()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Method not found: " + name));
    }
}
```