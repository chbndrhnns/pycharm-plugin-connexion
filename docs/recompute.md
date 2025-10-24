# Recompute Types Instead of Parsing Strings

Instead of parsing strings from range highlighters, we should use the proper type system APIs to recompute types when we
have a matching highlight.

## Approach

#### Python (PyCharm Platform)

- Actual type: use `TypeEvalContext.codeAnalysis()` to get type of expression:

```kotlin
TypeEvalContext context = TypeEvalContext . codeAnalysis (project, psiFile)
PyExpression expr = // obtain from PSI at caret
PyType actualType = context . getType (expr)
```

- Expected type: use assignment context or `PyTypeProvider` extension point:

```kotlin
Collection<PyExpectedType> expectedTypes = PyExpectedTypesProvider . getExpectedTypes (expr, context)
```

- Expression: Get from PSI element at cursor position

## Benefits

1. **Accurate**: Uses actual type inference instead of string parsing
2. **Robust**: Won't break if error message formats change
3. **Performance**: Direct API calls vs regex parsing
4. **Maintainable**: Less complex code, leverages platform APIs

## Implementation Notes

- Use `PyTypeProvider` extension points for cleaner type resolution
- TypeEvalContext provides the evaluation context for type inference
- Assignment expressions can provide expected type context
