### Analysis
The error `java.lang.IllegalStateException: Calling invokeAndWait from read-action leads to possible deadlock` occurs because `IntentionAction.generatePreview` (where `CustomTypeApplier.apply` is called) often runs within a read action (or at least on a background thread where waiting for the EDT is unsafe due to potential lock inversions).

The previous fix attempted to force the write action onto the EDT using `invokeAndWait`. While this solved the "Background write action not permitted" error, it introduced a deadlock risk because the background thread (holding a read lock) was waiting for the EDT (which might be waiting for a write lock).

### Solution
The correct way to perform write operations on a non-physical file copy during an intention preview is to use `IntentionPreviewUtils.write()`. This utility:
1.  Executes the provided block immediately if permitted (e.g., on a non-physical file where global write lock is not strictly enforced by the model but threading assertions usually block it).
2.  Avoids the need for `Application.runWriteAction` (which enforces EDT) or `WriteCommandAction`.
3.  Is safe to call from the background thread used by the preview system.

I have modified `src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/intention/customtype/CustomTypeApplier.kt` to use `IntentionPreviewUtils.write` when `isPreview` is true.

### Changes Applied

**File:** `src/main/kotlin/com/github/chbndrhnns/intellijplatformplugincopy/intention/customtype/CustomTypeApplier.kt`

1.  Added import: `import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils`
2.  Updated the execution logic in `apply`:

```kotlin
        if (isPreview) {
            // IntentionPreviewUtils.write handles the "write action" simulation for the preview copy
            // on the background thread without requiring EDT or causing deadlocks.
            IntentionPreviewUtils.write<RuntimeException> { executionBlock() }
        } else {
            WriteCommandAction.runWriteCommandAction(project, executionBlock)
        }
```

This resolves the deadlock while ensuring the preview generation can still modify the file copy as needed.