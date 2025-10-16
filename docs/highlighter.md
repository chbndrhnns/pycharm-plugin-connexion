# Highlighter Documentation

## TypeMismatchQuickFixIntention

This intention provides a quick-fix action for type mismatch highlights that come from specific code inspections.

### Supported Inspections

The intention now filters highlights to only show for specific inspection tool IDs:

- **PyTypeCheckerInspection**: Python type checking inspection that reports type mismatches
- Extensible for additional inspections (see below)

### How It Works

1. **Inspection Filtering**: The intention checks if a highlight comes from a supported inspection using multiple
   detection methods:
    - **HighlightInfo Analysis**: Examines quick-fix actions and descriptions for inspection clues
    - **Text Pattern Matching**: Falls back to analyzing tooltip text patterns
    - **Reflection**: As a last resort, uses reflection to access internal fields

2. **Pattern Detection**: Each supported inspection has configurable text patterns:
   ```kotlin
   "PyTypeCheckerInspection" to { text ->
       text.contains("expected type") && (text.contains("got") || text.contains("actual")) ||
       text.contains("type checker") && text.contains("python") ||
       text.contains("incompatible type") && text.contains("expected")
   }
   ```

3. **Type Mismatch Validation**: Additionally validates that the message contains type mismatch patterns:
    - "expected" + "type" keywords
    - "actual" or "got" or "found" keywords

### Extending for New Inspections

To add support for a new inspection:

1. Add the inspection ID to `supportedInspectionIds`:
   ```kotlin
   private val supportedInspectionIds = setOf(
       "PyTypeCheckerInspection",
       "YourNewInspectionId"  // Add here
   )
   ```

2. Add pattern detection logic to `inspectionPatterns`:
   ```kotlin
   "YourNewInspectionId" to { text ->
       // Your pattern matching logic here
       text.contains("your pattern") && text.contains("keywords")
   }
   ```

3. Update the `getInspectionToolId` method to recognize the new inspection:
   ```kotlin
   // Add new patterns in the quick-fix analysis section
   if (className.contains("yournew") && className.contains("inspection")) {
       return "YourNewInspectionId"
   }
   ```

### API Usage

The intention automatically filters highlights and only shows the quick-fix for supported inspections. Users will see
the "Show type mismatch details" option only when the caret is positioned on a type mismatch highlight from a supported
inspection.

### Testing

The test enables `PyTypeCheckerInspection` and verifies that the intention is available for Python type mismatches:

```kotlin
myFixture.enableInspections(PyTypeCheckerInspection::class.java)
myFixture.findSingleIntention("Show type mismatch details")
```