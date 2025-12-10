### Modifying the Floating File Structure Popup

To apply your customizations (modified tree nodes and the "Show Private Members" toggle) to the **Floating File Structure Popup** (`Cmd+F12` / `Ctrl+F12`), you need to address two specific requirements that differ slightly from the Tool Window:

1.  **The Filter Interface**: The popup only displays filters that implement the `FileStructureFilter` interface. Standard `Filter` implementations are ignored in the popup UI.
2.  **The Builder Type**: The mechanism that creates the popup prefers a `TreeBasedStructureViewBuilder`.

Here is how to adapt your solution.

#### 1. Update the Filter
Modify your filter to implement `FileStructureFilter` instead of just `Filter`. This interface adds methods to define the checkbox text and keyboard shortcut.

```java
package com.example.plugin;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBundle;
import com.intellij.ide.util.FileStructureFilter; // Key interface
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;

public class MyPrivateMembersFilter implements FileStructureFilter { // Change interface

    @Override
    public boolean isVisible(TreeElement treeNode) {
        // ... same logic as before ...
        return true; 
    }

    @Override
    public @NotNull ActionPresentation getPresentation() {
        // This is for the Tool Window (icon/tooltip)
        return new ActionPresentationData("Show Private Members", null, AllIcons.Nodes.Private);
    }

    @Override
    public @NotNull String getName() {
        return "MY_SHOW_PRIVATE_MEMBERS";
    }

    @Override
    public boolean isReverted() {
        return true;
    }

    // --- New Methods for FileStructureFilter ---

    @Override
    public @NotNull String getCheckBoxText() {
        // This text appears next to the checkbox in the popup
        return "Show private members";
    }

    @Override
    public Shortcut @NotNull [] getShortcut() {
        // Optional: Provide a shortcut to toggle this filter while the popup is open
        return KeymapUtil.getActiveKeymapShortcuts("FileStructurePopup").getShortcuts();
    }
}
```

#### 2. Ensure Correct Builder Implementation
The `ViewStructureAction` (which triggers the popup) checks if the builder is a `TreeBasedStructureViewBuilder`. If your factory returns a generic `StructureViewBuilder`, the popup might try to initialize the full tool window view, which is unnecessary and heavier.

Ensure your Factory returns an anonymous implementation of `TreeBasedStructureViewBuilder`:

```java
package com.example.plugin;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyPythonStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        // 1. Get original builder
        StructureViewBuilder originalBuilder = LanguageStructureViewBuilder.INSTANCE
                .allForLanguage(psiFile.getLanguage()).stream()
                .filter(factory -> !(factory instanceof MyPythonStructureViewFactory))
                .findFirst()
                .map(factory -> factory.getStructureViewBuilder(psiFile))
                .orElse(null);

        if (originalBuilder == null) return null;

        // 2. Return a TreeBasedStructureViewBuilder
        // This is crucial for the floating popup to recognize it and use createStructureViewModel directly.
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                StructureViewModel originalModel;
                
                // Extract model from original builder
                if (originalBuilder instanceof TreeBasedStructureViewBuilder) {
                    originalModel = ((TreeBasedStructureViewBuilder) originalBuilder).createStructureViewModel(editor);
                } else {
                    // Fallback: create the full view to get the model (less efficient but safe)
                    originalModel = originalBuilder.createStructureView(null, psiFile.getProject()).getTreeModel();
                }
                
                // Wrap the model
                return new MyStructureViewModelWrapper(originalModel);
            }
            
            @Override
            public boolean isRootNodeShown() {
                // Respect the original builder's preference if possible, or default to false for Python
                return false; 
            }
        };
    }
}
```

### Summary
*   **Tree Nodes**: Since `FileStructurePopup` uses `StructureViewModel.getRoot()`, your existing `MyStructureViewModelWrapper` (from the previous plan) will automatically handle the node customization for the popup as well.
*   **Filter**: By implementing `FileStructureFilter`, your toggle will appear as a checkbox at the top or bottom of the popup window.