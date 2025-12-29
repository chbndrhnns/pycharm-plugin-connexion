### Suggested public approach
Use the public `intentionActionFilter` extension point (`com.intellij.codeInsight.intentionActionFilter`) and implement `IntentionActionFilter` to veto specific intentions/quick fixes. This runs before the intention list is shown and lets you hide items based on the action instance, context, or attributes.

### How to wire it
```xml
<!-- plugin.xml -->
<extensions defaultExtensionNs="com.intellij">
  <intentionActionFilter implementation="com.example.MyIntentionFilter"/>
</extensions>
```

```java
public final class MyIntentionFilter implements IntentionActionFilter {
  @Override
  public boolean accept(@NotNull IntentionAction action,
                        @NotNull PsiFile file,
                        @Nullable Editor editor) {
    // Example: hide specific quick-fix class
    if (action instanceof MyUnwantedQuickFix) return false;
    // Example: check family name/inspection id if needed
    if (Objects.equals(action.getFamilyName(), "Unwanted fix")) return false;
    return true;
  }
}
```

### Notes
- `IntentionActionFilter` is stable/public, unlike `IntentionMenuContributor`.
- It sees both intentions and quick fixes (`IntentionAction` covers `LocalQuickFix` wrappers too), so you can filter by class, family name, or custom marker interface.
- Keep the filtering fast; it is called frequently during highlighting/intentions gathering.

If you need to hide only for certain files/contexts, add checks on `file`, PSI, or editor state inside `accept` instead of globally blocking the action.