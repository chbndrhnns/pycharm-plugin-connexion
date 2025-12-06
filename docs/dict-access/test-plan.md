### Test Cases Plan

You should add a test file `testData/intention/dictAccess/ToggleDictAccess.py` and a corresponding test class extending
`PyIntentionTestCase`.

#### Positive Cases (Should Transform)

1. **Basic**: `d['a']` $\leftrightarrow$ `d.get('a')`
2. **Variable**: `d[k]` $\leftrightarrow$ `d.get(k)`
3. **Complex Operand**: `(a + b)[k]` $\leftrightarrow$ `(a + b).get(k)` (ensure parens preserved)
4. **Tuple Key**: `d[a, b]` $\leftrightarrow$ `d.get((a, b))` (ensure parens added/removed correctly)
5. **Existing Parens**: `d[(a, b)]` $\leftrightarrow$ `d.get((a, b))`
6. **Keyword Arg**: `d.get(key='a')` $\to$ `d['a']`
7. **Inheritance**: `UserDict()['a']` $\leftrightarrow$ `UserDict().get('a')` (verifies `PyABCUtil` check)
8. **Comments**: `d[ /*c*/ k ]` $\leftrightarrow$ `d.get( /*c*/ k )`

#### Negative Cases (Should NOT Transform)

1. **Assignment**: `d['a'] = 1`
2. **Augmented Assignment**: `d['a'] += 1`
3. **Deletion**: `del d['a']`
4. **Unpacking Target**: `d['a'], x = 1, 2` (Part of LHS)
5. **Default Value**: `d.get('a', 0)` (Semantics differ: `get` returns 0, `[]` raises KeyError)
6. **No Args**: `d.get()`
7. **Multiple Args**: `d.get('a', 'b', 'c')`
8. **Non-Mapping**: `'s'['0']` (String supports subscription but no `get`), `[1][0]` (List supports subscription but no
   `get`).