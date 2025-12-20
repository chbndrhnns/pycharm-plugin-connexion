# Callable Transformation: functools.partial ↔ lambda ↔ function

## Overview

This feature provides bidirectional transformations between three equivalent callable forms in Python:

1. **`functools.partial(func, *args, **kwargs)`** - Partial function application
2. **`lambda` expressions** - Anonymous functions with bound arguments
3. **Direct function calls** - When all arguments are pre-bound

These transformations help developers refactor between different coding styles while preserving semantics.

## Motivation

Different callable forms have different trade-offs:

| Form | Pros | Cons |
|------|------|------|
| `functools.partial` | Explicit, preserves function metadata, introspectable | Verbose, requires import |
| `lambda` | Concise, no import needed | Less readable for complex cases, no metadata |
| Direct call | Simplest when all args known | Only works when result is needed immediately |

Developers often need to switch between these forms during refactoring.

## Transformation Matrix

### Supported Transformations

| From | To | Availability |
|------|-----|--------------|
| `partial(f, a, b)` | `lambda: f(a, b)` | Always available |
| `partial(f, a, b, c=1)` | `lambda: f(a, b, c=1)` | Always available |
| `partial(f, a)` | `lambda x: f(a, x)` | When remaining params are known |
| `lambda: f(a, b)` | `partial(f, a, b)` | When lambda body is a simple call |
| `lambda x: f(a, x)` | `partial(f, a)` | When lambda params map to trailing call args |
| `partial(f, *all_args)` | `f(*all_args)` | When all required args are bound |
| `lambda: f(a, b)` | `f(a, b)` | When lambda takes no params (inline) |

### Not Supported (Out of Scope)

| Transformation | Reason |
|----------------|--------|
| `partial` with `*args`/`**kwargs` unpacking | Complex semantics, rare use case |
| Nested `partial` calls | Complexity, edge cases |
| `lambda` with complex body | Not equivalent to partial |
| `lambda` with side effects | Semantic differences |

## Requirements

### 1. Partial to Lambda Transformation

**Trigger**: Caret on a `functools.partial(...)` call expression.

**Availability Conditions**:
- Expression is a call to `functools.partial` or `partial` (with proper import)
- First argument is a callable reference
- Remaining arguments are positional and/or keyword arguments

**Action**: Replace `partial(func, arg1, arg2, kw=val)` with equivalent lambda.

**Examples**:

```python
# Before: All arguments bound
callback = partial(process, data, mode="fast")
# After:
callback = lambda: process(data, mode="fast")

# Before: Some arguments bound, function signature known
handler = partial(handle_event, user_id)  # handle_event(user_id, event) -> ...
# After:
handler = lambda event: handle_event(user_id, event)

# Before: Keyword-only binding
formatter = partial(format_string, prefix=">>")
# After:
formatter = lambda s: format_string(s, prefix=">>")
```

### 2. Lambda to Partial Transformation

**Trigger**: Caret on a `lambda` expression.

**Availability Conditions**:
- Lambda body is a single function call expression
- Lambda parameters (if any) are passed directly to the call as trailing positional arguments
- No complex expressions in the call arguments (only literals, names, lambda params)
- Lambda params appear in the same order as trailing call arguments

**Action**: Replace lambda with equivalent `partial(...)` call, adding import if needed.

**Examples**:

```python
# Before: No lambda params
callback = lambda: process(data, mode="fast")
# After:
callback = partial(process, data, mode="fast")

# Before: Lambda param maps to trailing arg
handler = lambda event: handle_event(user_id, event)
# After:
handler = partial(handle_event, user_id)

# Before: Multiple lambda params as trailing args
adder = lambda x, y: add_numbers(base, x, y)
# After:
adder = partial(add_numbers, base)
```

**Not Available When**:

```python
# Lambda param not in trailing position
f = lambda x: process(x, suffix)  # x is first arg, not trailing

# Lambda param used multiple times
f = lambda x: process(x, x)

# Lambda param in expression
f = lambda x: process(x + 1)

# Complex body
f = lambda x: process(x) if x else default()

# Multiple statements (not possible in lambda, but for clarity)
```

### 3. Partial to Direct Call (Inline)

**Trigger**: Caret on a `partial(...)` expression.

**Availability Conditions**:
- All required parameters of the target function are bound
- The partial is used in a context where immediate invocation makes sense
- Optional: Only offer when the partial is immediately called `partial(f, a, b)()`

**Action**: Replace `partial(func, *args)()` with `func(*args)`.

**Examples**:

```python
# Before: Partial immediately called
result = partial(calculate, x, y)()
# After:
result = calculate(x, y)

# Before: Partial assigned then called once
compute = partial(process, data)
output = compute()
# After (with usage update):
output = process(data)
```

### 4. Lambda to Direct Call (Inline)

**Trigger**: Caret on a `lambda` expression that takes no parameters.

**Availability Conditions**:
- Lambda takes no parameters
- Lambda body is a simple function call
- Lambda is immediately invoked OR assigned and called exactly once

**Action**: Replace `(lambda: f(a, b))()` with `f(a, b)`.

**Examples**:

```python
# Before: Immediately invoked lambda
result = (lambda: calculate(x, y))()
# After:
result = calculate(x, y)
```

## Implementation Plan

### Phase 1: Core Infrastructure

1. **Create base classes and utilities**
   - `CallableTransformationBase` - shared logic for all transformations
   - `PartialCallAnalyzer` - parse and analyze `partial(...)` calls
   - `LambdaCallAnalyzer` - parse and analyze lambda expressions with call bodies
   - `FunctionSignatureResolver` - resolve target function signatures

2. **Settings integration**
   - Add `enablePartialToLambdaIntention` toggle
   - Add `enableLambdaToPartialIntention` toggle
   - Add `enableInlineCallableIntention` toggle

### Phase 2: Partial → Lambda

1. **Create `PartialToLambdaIntention`**
   - Implement `isAvailable()`: detect `partial(...)` calls
   - Implement `invoke()`: generate equivalent lambda
   - Handle import removal if `partial` no longer used

2. **Handle edge cases**
   - Preserve comments where possible
   - Handle `*args` in partial (make unavailable)
   - Handle `**kwargs` in partial (convert to explicit kwargs in lambda)

### Phase 3: Lambda → Partial

1. **Create `LambdaToPartialIntention`**
   - Implement `isAvailable()`: detect convertible lambdas
   - Implement `invoke()`: generate equivalent partial
   - Add `from functools import partial` if needed

2. **Handle edge cases**
   - Detect when lambda params don't map cleanly to trailing args
   - Handle keyword-only arguments in lambda

### Phase 4: Inline Transformations

1. **Create `InlinePartialIntention`**
   - Detect immediately-invoked partials
   - Replace with direct call

2. **Create `InlineLambdaIntention`**
   - Detect immediately-invoked zero-arg lambdas
   - Replace with direct call

### Phase 5: Testing & Polish

1. Comprehensive unit tests
2. Integration tests with real-world patterns
3. Performance testing with large files
4. Documentation updates

## Edge Cases

### Partial-Specific Edge Cases

| Case | Example | Handling |
|------|---------|----------|
| Partial with `*args` | `partial(f, *items)` | Unavailable - cannot determine bound args |
| Partial with `**kwargs` | `partial(f, **opts)` | Unavailable - cannot determine bound kwargs |
| Nested partial | `partial(partial(f, a), b)` | Unavailable - too complex |
| Partial of method | `partial(obj.method, arg)` | Available - treat as regular callable |
| Partial of lambda | `partial(lambda x: x+1, 5)` | Available - unusual but valid |
| Partial of builtin | `partial(print, end="")` | Available |
| Partial with no extra args | `partial(f)` | Available - converts to `lambda *a, **k: f(*a, **k)` or unavailable |

### Lambda-Specific Edge Cases

| Case | Example | Handling |
|------|---------|----------|
| Lambda with default args | `lambda x=1: f(x)` | Unavailable for partial conversion |
| Lambda with `*args` | `lambda *a: f(*a)` | Unavailable - cannot map to partial |
| Lambda with `**kwargs` | `lambda **k: f(**k)` | Unavailable |
| Lambda param reordering | `lambda x, y: f(y, x)` | Unavailable - args not in order |
| Lambda param in expression | `lambda x: f(x + 1)` | Unavailable |
| Lambda with multiple calls | `lambda: f() or g()` | Unavailable |
| Lambda calling method | `lambda: obj.method(a)` | Available |
| Async lambda (not valid Python) | N/A | N/A |

### Scope and Binding Edge Cases

| Case | Example | Handling |
|------|---------|----------|
| Closure over mutable | `partial(f, items)` where `items` is mutable | Available - same semantics |
| Late binding in lambda | `lambda: f(x)` where `x` changes | Warning or note in preview |
| Name shadowing | Lambda param shadows outer name | Detect and handle correctly |

### Import Edge Cases

| Case | Handling |
|------|----------|
| `from functools import partial` | Standard case |
| `import functools` then `functools.partial` | Detect qualified usage |
| `from functools import partial as p` | Detect aliased import |
| Removing unused partial import | Clean up if last usage removed |
| Adding partial import | Use `AddImportHelper` |

## Test Plan

### Unit Tests: PartialToLambdaIntention

#### Positive Cases (Intention Available & Works)

```python
# Test: Basic partial with positional args
from functools import partial
callback = partial(process, arg1, arg2)
# Expected: callback = lambda: process(arg1, arg2)

# Test: Partial with keyword args
formatter = partial(format, prefix=">>", suffix="<<")
# Expected: formatter = lambda s: format(s, prefix=">>", suffix="<<")

# Test: Partial with mixed args
handler = partial(handle, user_id, mode="async")
# Expected: handler = lambda event: handle(user_id, event, mode="async")

# Test: Partial of method
processor = partial(obj.process, data)
# Expected: processor = lambda: obj.process(data)

# Test: Partial of builtin
printer = partial(print, end="")
# Expected: printer = lambda *args: print(*args, end="")

# Test: Qualified functools.partial
import functools
cb = functools.partial(func, arg)
# Expected: cb = lambda: func(arg)

# Test: Aliased partial
from functools import partial as p
cb = p(func, arg)
# Expected: cb = lambda: func(arg)
```

#### Negative Cases (Intention Not Available)

```python
# Test: Partial with *args unpacking
partial(f, *items)  # Not available

# Test: Partial with **kwargs unpacking  
partial(f, **options)  # Not available

# Test: Nested partial
partial(partial(f, a), b)  # Not available

# Test: Not a partial call
some_other_function(f, arg)  # Not available

# Test: Partial with no function arg
partial()  # Not available (invalid anyway)
```

### Unit Tests: LambdaToPartialIntention

#### Positive Cases

```python
# Test: Zero-arg lambda with simple call
callback = lambda: process(data, mode)
# Expected: callback = partial(process, data, mode)

# Test: Lambda with trailing param
handler = lambda event: handle(user_id, event)
# Expected: handler = partial(handle, user_id)

# Test: Lambda with multiple trailing params
adder = lambda x, y: compute(base, x, y)
# Expected: adder = partial(compute, base)

# Test: Lambda with keyword args in call
formatter = lambda s: format(s, prefix=">>")
# Expected: formatter = partial(format, prefix=">>")

# Test: Lambda calling method
processor = lambda: obj.process(data)
# Expected: processor = partial(obj.process, data)
```

#### Negative Cases

```python
# Test: Lambda param not trailing
f = lambda x: process(x, suffix)  # Not available

# Test: Lambda param used twice
f = lambda x: process(x, x)  # Not available

# Test: Lambda param in expression
f = lambda x: process(x + 1)  # Not available

# Test: Lambda with conditional
f = lambda x: a if x else b  # Not available

# Test: Lambda with default param
f = lambda x=1: process(x)  # Not available

# Test: Lambda with *args
f = lambda *a: process(*a)  # Not available

# Test: Lambda body not a call
f = lambda x: x + 1  # Not available

# Test: Lambda with multiple expressions (tuple)
f = lambda: (a(), b())  # Not available
```

### Unit Tests: Inline Transformations

```python
# Test: Immediately invoked partial
result = partial(calc, x, y)()
# Expected: result = calc(x, y)

# Test: Immediately invoked lambda
result = (lambda: calc(x, y))()
# Expected: result = calc(x, y)

# Test: Partial not immediately invoked
cb = partial(calc, x, y)  # Inline not available here
cb()  # Would need multi-site refactoring
```

### Integration Tests

```python
# Test: Round-trip partial -> lambda -> partial
from functools import partial
original = partial(process, data, mode="fast")
# -> lambda: process(data, mode="fast")
# -> partial(process, data, mode="fast")
# Should be semantically equivalent

# Test: Import management
# File with no functools import
callback = lambda: process(data)
# After lambda->partial: should add import

# Test: Import cleanup
from functools import partial
cb = partial(func, arg)
# After partial->lambda: should remove import if unused
```

## UI/UX Considerations

### Intention Names

- "Convert partial to lambda"
- "Convert lambda to partial"  
- "Inline partial call"
- "Inline lambda call"

### Intention Grouping

All intentions should be in the same family: "Callable transformations"

### Preview

All transformations should support `IntentionPreviewInfo.DIFF` to show the change before applying.

### Warnings

Consider showing warnings for:
- Late binding issues when converting partial to lambda
- Loss of function metadata (name, docstring) when converting to lambda

## Dependencies

- `functools` module awareness
- Python type inference for function signatures
- `AddImportHelper` for import management
- Existing `PyElementGenerator` patterns

## Related Features

- `fn-to-lambda.md` - Converts named functions to/from lambdas (different scope)
- `CallableToProtocolIntention` - Converts `Callable` type hints to Protocol

## Future Enhancements

1. **Multi-site refactoring**: When a partial/lambda is assigned and used multiple times, offer to inline all usages
2. **Partial chain flattening**: `partial(partial(f, a), b)` → `partial(f, a, b)`
3. **Method reference style**: `partial(str.upper)` ↔ `str.upper` (when used as callback)
4. **Operator module integration**: `partial(operator.add, 1)` ↔ `lambda x: x + 1`
