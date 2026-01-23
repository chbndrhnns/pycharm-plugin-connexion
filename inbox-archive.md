# Inbox


## Completed Tasks (newest first)

- [x] feat: Show BetterPy for "Show private members"
- [x] feat: Add run configuration with package name to gutter
- [x] fix: Do not offer local variable here `if __name__ == '__main__':`
- [x] fix: Combine settings for parameter object
- [x] fix: Use actual outcome should not be available for exceptions or binary operations (assert False)
- [x] fix: When toggling pytest mark skip on param, add trailing comma
- [x] fix: Export to target needs to use import path when not using direct parent
- [x] feat: Offer NamedTuple, TypedDict and pydantic.BaseModel as bases for parameter object
- [x] fix: Missing introduce parameter action if caret on type annotation of argument
- [x] fix: Duplicate entries in refactoring menu
- [x] fix: Marks symbols referenced in `patch()` as unresolved although they exist (imported)
- [x] fix: Add to __all__ is missing the plugin prefix and shold probably go away
- [x] fix: Inspection "Symbol 'InMemNetboxPrefixMatches' is not exported in package __all__" should ignore private
  packages and not trigger if it's exported in the next public package
- [x] feat: copy special: copy test as pytest node id reference
- [x] feat: Introduce type alias for union type
- [x] feat: Support skip toggle for single parameters
- [x] fix: Go to openapi definition should only be available when there is an endpoint method
- [x] fix: Jump to test node should only show when a) test tree is not empty and b) we are in a test context
- [x] fix: Do not offer wrap for Protocol classes
- [x] fix: Runtime error

```
java.lang.RuntimeException: Document is locked by write PSI operations. Use PsiDocumentManager.doPostponedOperationsAndUnblockDocument() to commit PSI changes to the document.
Unprocessed elements: Py:ASSIGNMENT_STATEMENT(0,38)
	at com.intellij.psi.impl.source.PostprocessReformattingAspectImpl.assertDocumentChangeIsAllowed(PostprocessReformattingAspectImpl.java:332)
	at com.intellij.psi.impl.PsiDocumentManagerImpl.beforeDocumentChangeOnUnlockedDocument(PsiDocumentManagerImpl.java:83)
	at com.intellij.psi.impl.PsiDocumentManagerBase.beforeDocumentChange(PsiDocumentManagerBase.java:1044)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at com.intellij.util.EventDispatcher.dispatchVoidMethod(EventDispatcher.java:120)
	at com.intellij.util.EventDispatcher.lambda$doCreateMulticaster$1(EventDispatcher.java:85)
	at jdk.proxy2/jdk.proxy2.$Proxy100.beforeDocumentChange(Unknown Source)
	at com.intellij.openapi.editor.impl.DocumentImpl.lambda$beforeChangedUpdate$5(DocumentImpl.java:935)
	at com.intellij.openapi.progress.impl.CoreProgressManager.lambda$executeNonCancelableSection$3(CoreProgressManager.java:325)
	at com.intellij.openapi.progress.impl.CoreProgressManager.registerIndicatorAndRun(CoreProgressManager.java:792)
	at com.intellij.openapi.progress.impl.CoreProgressManager.computeUnderProgress(CoreProgressManager.java:748)
	at com.intellij.openapi.progress.impl.CoreProgressManager.lambda$computeInNonCancelableSection$4(CoreProgressManager.java:333)
	at com.intellij.openapi.progress.Cancellation.computeInNonCancelableSection(Cancellation.java:156)
	at com.intellij.openapi.progress.impl.CoreProgressManager.computeInNonCancelableSection(CoreProgressManager.java:333)
	at com.intellij.openapi.progress.impl.CoreProgressManager.executeNonCancelableSection(CoreProgressManager.java:324)
	at com.intellij.openapi.editor.impl.DocumentImpl.beforeChangedUpdate(DocumentImpl.java:932)
	at com.intellij.openapi.editor.impl.DocumentImpl.updateText(DocumentImpl.java:867)
	at com.intellij.openapi.editor.impl.DocumentImpl.insertString(DocumentImpl.java:595)
	at com.intellij.openapi.editor.impl.TrailingSpacesStripper$1.run(TrailingSpacesStripper.java:86)
	at com.intellij.openapi.command.impl.CoreCommandProcessor.runUndoTransparentAction(CoreCommandProcessor.java:272)
	at com.intellij.openapi.editor.impl.TrailingSpacesStripper.lambda$performUndoableWrite$1(TrailingSpacesStripper.java:127)
	at com.intellij.openapi.application.impl.AppImplKt$runnableUnitFunction$1.invoke(appImpl.kt:121)
	at com.intellij.openapi.application.impl.AppImplKt$runnableUnitFunction$1.invoke(appImpl.kt:121)
	at com.intellij.platform.locking.impl.NestedLocksThreadingSupport.runWriteAction(NestedLocksThreadingSupport.kt:998)
	at com.intellij.openapi.application.impl.ApplicationImpl.runWriteAction(ApplicationImpl.java:1132)
	at com.intellij.openapi.editor.impl.TrailingSpacesStripper.performUndoableWrite(TrailingSpacesStripper.java:126)
	at com.intellij.openapi.editor.impl.TrailingSpacesStripper.strip(TrailingSpacesStripper.java:78)
	at com.intellij.openapi.editor.impl.TrailingSpacesStripper.beforeDocumentSaving(TrailingSpacesStripper.java:54)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:565)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl$FileDocumentManagerListenerBackgroundableBridge.multiCast(FileDocumentManagerImpl.java:1051)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl$FileDocumentManagerListenerBackgroundableBridge.lambda$new$0(FileDocumentManagerImpl.java:1076)
	at jdk.proxy2/jdk.proxy2.$Proxy34.beforeDocumentSaving(Unknown Source)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl$FileDocumentManagerListenerBackgroundableBridge.lambda$beforeDocumentSaving$3(FileDocumentManagerImpl.java:1095)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl$FileDocumentManagerListenerBackgroundableBridge.invokeOnEdt(FileDocumentManagerImpl.java:1061)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl$FileDocumentManagerListenerBackgroundableBridge.beforeDocumentSaving(FileDocumentManagerImpl.java:1095)
	at com.intellij.util.messages.impl.MessageBusImplKt.invokeMethod(MessageBusImpl.kt:820)
	at com.intellij.util.messages.impl.MessageBusImplKt.invokeListener(MessageBusImpl.kt:760)
	at com.intellij.util.messages.impl.MessageBusImplKt.executeOrAddToQueue(MessageBusImpl.kt:585)
	at com.intellij.util.messages.impl.ToDirectChildrenMessagePublisher.publish$intellij_platform_core(CompositeMessageBus.kt:156)
	at com.intellij.util.messages.impl.MessagePublisher.invoke(MessageBusImpl.kt:533)
	at jdk.proxy2/jdk.proxy2.$Proxy104.beforeDocumentSaving(Unknown Source)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.lambda$doSaveDocumentInWriteAction$9(FileDocumentManagerImpl.java:446)
	at com.intellij.pom.core.impl.PomModelImpl.guardPsiModificationsIn(PomModelImpl.java:329)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.doSaveDocumentInWriteAction(FileDocumentManagerImpl.java:444)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.lambda$doSaveDocument$5(FileDocumentManagerImpl.java:389)
	at com.intellij.openapi.application.impl.AppImplKt$rethrowCheckedExceptions$2.invoke(appImpl.kt:123)
	at com.intellij.platform.locking.impl.NestedLocksThreadingSupport.runWriteAction(NestedLocksThreadingSupport.kt:998)
	at com.intellij.openapi.application.impl.ApplicationImpl.runWriteAction(ApplicationImpl.java:1156)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.doSaveDocument(FileDocumentManagerImpl.java:388)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.saveDocumentInWriteSafeEnvironment(FileDocumentManagerImpl.java:337)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.saveDocument(FileDocumentManagerImpl.java:326)
	at com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl.saveDocument(FileDocumentManagerImpl.java:315)
	at com.intellij.formatting.service.AsyncDocumentFormattingService.prepareForFormatting(AsyncDocumentFormattingService.java:82)
	at com.intellij.formatting.service.AsyncDocumentFormattingService.prepareForFormatting(AsyncDocumentFormattingService.java:144)
	at com.intellij.formatting.service.AsyncDocumentFormattingSupportImpl.formatDocument(AsyncDocumentFormattingSupportImpl.kt:62)
	at com.intellij.formatting.service.AsyncDocumentFormattingService.formatDocument(AsyncDocumentFormattingService.java:44)
	at com.intellij.formatting.service.AbstractDocumentFormattingService.formatElement(AbstractDocumentFormattingService.java:47)
	at com.intellij.formatting.service.AbstractDocumentFormattingService.formatElement(AbstractDocumentFormattingService.java:31)
	at com.intellij.formatting.service.FormattingServiceUtil.formatElement(FormattingServiceUtil.java:67)
	at com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl.reformat(CodeStyleManagerImpl.java:82)
	at com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl.reformat(CodeStyleManagerImpl.java:66)
	at com.github.chbndrhnns.betterpy.features.exports.PyAllExportUtil.insertStatementBelowDocstring(PyAllExportUtil.kt:208)
	at com.github.chbndrhnns.betterpy.features.exports.PyAllExportUtil.createNewDunderAll(PyAllExportUtil.kt:178)
	at com.github.chbndrhnns.betterpy.features.exports.PyAllExportUtil.ensureSymbolExported(PyAllExportUtil.kt:47)
	at com.github.chbndrhnns.betterpy.features.exports.PyExportSymbolToTargetIntention.exportToTarget$lambda$6(PyExportSymbolToTargetIntention.kt:166)
```

- [x] refactor: `IntentionPreviewUtils.write<RuntimeException> { symbol.setName(newName) }` -> ok
- [x] fix: Does not filter rename to self quickfix
- [x] fix: warning is displayed in green with a checkmark when a file was created that shadows a stdlib module. Show in
  orange and offer link to rename the file.
- [x] Wrap case which failed initially

```python
import dataclasses


@dataclasses.dataclass
class Inner:
    id: int


@dataclasses.dataclass
class Outer:
    inner: Inner


val2: list[Outer] = [
    Outer(
        inner=Inner(id=1),
    )
]
```

- [x] feat: Wrap value in lambda if callable is expected
- [x] feat: Wrap value in lambda if callable is expected
- [x] fix: Toggle type alias on symbol (not annotation) destroys instance attribute
- [x] feat: When a public symbol is not used, do not display the refactoring preview
- [x] fix: Make optional destroys instance attribute
- [x] feat: Update names for TypeVar and NewType on rename
- [x] feat: When creating and renaming, check against stdlib modules/packages and warn if name clashes (
  spec/shadow-stdlib.md)
- [x] feat: Annotate constant with `Final[T]` (spec/final-wrapper.md)
- [x] fix: Never offer wrap for None
- [x] feat: Do not allow to make private if used
- [x] fix: Does not preview make private
- [x] feat: Suppress rename to self and add "Add self" (spec/add-self.md)
- [x] fix: Do not offer wrap if takes collection or single and either is satisfied
- [x] feat: Offer toggle skip also inside `pytest.param`
- [x] feat: Connexion settings group and toggle
- [x] fix: populate arguments should re-use import aliases
- [x] fix: Do not offer export for import aliases
- [x] feat: Connexion: connect endpoints in code and spec
- [x] feat: Convert between Protocol and Callable annotation
- [x] feat: After return, suggest `list[T()]` if return type is a container type (?)
- [x] fix: Should not offer custom type on keywords
- [x] fix: Wrap suggests set instead of type: `vals: set[str] = {1}`
- [x] fix: Wrap should use set literal instead of set call, `vals: set[T] = T`
- [x] feat: Enable parameter object action also if only one parameter
- [x] fix: Wrap should pick inner problem first, offers `Prefix` here
- [N] feat: Show red bubble if introduce parameter object is not available
- [x] feat: Introduce custom type from str should add declaration annotation
- [x] fix: Wrap with __add__ should not be available
- [x] fix: Introduce parameter object should create valid class identifiers
- [x] fix: Introduce parameter object should not be available for test methods and pytest fixtures (
  spec/parameter-object/blocklist.md)
- [x] fix: Only offer actual outcome if diff exists, not for all failures
- [x] fix: Introduce parameter object needs to respect existing * arg
- [x] feat: Make introduce parameter object available on call site
- [x] feat: Support actual value for such case where one parameter is ok

```python
@pytest.mark.parametrize("arg,expected", [("abc", "defg"), ("defg", "defg"), ])
def test_str(arg, expected):
    assert arg == expected
```
- [x] fix: Deprecation of PsiUtilBase.findEditor
- [x] fix: Deprecation of TransactionGuard.getInstance().submitTransaction
- [x] fix: Convert parametrize argument should only be available inside decorator
- [x] feat: Convert between pytest.param() and no param representation
- [x] fix: Strip type annotations should only be available on function declaration, not decorators or body.
- [x] fix: Introduce type fails for pytest.mark.parametrize[x] fix: Limit search for protocol implementations to project
  code, no stdlib, no libraries
- [x] feat: Replace expected with actual test outcome
- [x] feat: Toggle skip marker from test panel
- [-] feat: Intention: Make function abstract (add @abc.abstractmethod or @abstractmethod)
- [x] feat: Remove "Update usages" quick fix menu
- [x] feat: Jump from test to test node
- [x] feat: When a local field exists with the same name as the parameter, show `param=param` as a suggestion
- [x] fix: module-level pytest skip should only be available when caret is on module-level scope
- [x] fix: When suggesting expected types, skip LiteralStr instances or similar
- [x] fix: Do not add completion suggestion for expected type when we have `item.` already
- [x] fix: copy stack trace should only be available for failed test
- [x] feat: Implement method from abstractmethod sitae
- [x] feat: Remove signature annotations
- [x] fix: When implementing abstract method, take care of existing ellipsis
- [x] fix: populate arguments from locals should only include required arguments
- [x] feat: Offer wrap in exception calls
- [x] fix: Parse tests in console: Parse args with single quotes correctly, like
  `tests/test_.py::test_this[<class 'src.MyClass'>]`
- [x] fix: Parse tests in console: Strip spaces in the beginning
- [x] feat: Configure dunder all inspection to be only available if __all__ is present
- [x] feat: Add __slots__ = () when introducing custom type for str
- [x] feat: Add quickfix to parametrize test
  - [x] arg ends up outside of parentheses if no args
- [x] feat: Introduce parameter object should be available on call site method name
- [x] feat: Check which parts are not toggleable in settings so far
- [x] refactor: `if (!aliasName.isNullOrBlank() && !PyBuiltinNames.isBuiltin(aliasName)) {`
- [x] refactor: `MakeParameterMandatoryIntention`
- [x] refactor: `if (fn.isAsync && returnType is PyCollectionType) {`
- [x] feat: Add quickfix to skip/unskip test
- [x] fix: Suggests Coroutine for async methods after return statement
- [x] feat: In assignments or calls, offer the annotated return types first in the suggestions
- [x] feat: When typing "return", offer the annotated return types first in the suggestions
- [x] feat: Add inspection that renders a problem in the base class if methods are missing in child classes
- [x] feat: Implement abstract methods in child classes, starting from abstract method
- [x] feat: Make argument mandatory
- [x] feat: Reload plugin without restart
- [x] fix: Populate: need to import leaf node types
- [x] feat: Create settings group
- [x] feat: Move "copy with dependencies" intention to copy special
- [x] feat: Add intention: make parameter optional (`arg: str` -> `arg: str | None = None`)
- [x] fix: do not offer "use exported" if in private child package: `from .._bases import BaseBlaModel`
- [x] fix: do not offer make private/public on test methods and test classes
- [x] feat: Use "change visibility" intention for private/public
- [x] feat: Copy names of failed tests
  - [x] Validate FQN
  - [x] Validate pytest node ids
  - [x] Copy with stacktrace
- [x] feat: Treat references as PSI elements, pytest.mark.filterwarnings, patch()
- [x] feat: Copy selection with imports
- [x] feat: populate with argument names that exist in the current scope
- [x] feat: filter structure view by private/public
- [x] feat: Add local variable from unresolved reference
- [x] Unsupported python version appears too early, when no interpreter is set up
- [x] fix: Remove deprecated usage: `FilenameIndex.getFilesByName`
- [x] feat: exception: if using err/exc/error, offer `except bla as X`
- [x] feat: exception: wrap list with parentheses to resolve syntax error
- [x] feat: Parse pytest node ids in the terminal as links
- [x] feat: Parse pytest identifier in search all dialog
- [x] feat: Support export of attributes
- [x] Offer unwrap for redundant cast

```python
def test_():
  val: int = 1
  return int(val)
```

- [x] wrap: Do not offer t.Final -> Wrap with Typevar
- [x] unwrap: do not offer here `access_level: str = group_access.get("access_level")`
- [x] feat: Transform between dict access .get and []
  - [x] refactor `PyDictGetToTryExceptIntention`,
- [x] fix: do not offer introduce custom type on string annotation
- [x] feat: Guard required python version, maybe with settings note?
- [x] fix: ignore conftest when suggesting __all__
- [x] fix: introduce paramter object should only be available when caret on: function, function args, call site the same
- [N] fix: do not offer introduce type if unresolved reference on statement: `MY_CONSTANT: Final[str] = "VALUE"`]
- [x] fix: introduce type fails for `MY_CONSTANT: Final[str] = "VALUE"`]
- [x] fix: populate arguments creates extra spaces when used on decorator
- [x] fix: do not offer introduce type on import statement (constant,eg)
- [x] fix: Update annotation when introducing custom type

```python
d: dict[str, int] = {}
val = d["<caret>abc"]
```

- [x] fix: Make visibility intention only available on names
- [x] feat: Copy build number
- [x] feat: Introduce parameter object (docs/parameter-object/state.md)
  - [x] refactor: String and imports handling in PyIntroduceParameterObjectProcessor
  - [x] check if there is a better way for `addDataclassImport`
  - [x] refactor: updateCallSites
  - [x] Add to Refactoring this context menu
  - [x] fix: investigate strange `.trimIndent() + "\n\n",` in testMethodIntroduceParameterObject
  - [x] Use kwargs by default for dataclass
  - [x] Refactor tests to include the interceptor
  - [x] Deal with union types and annotated and forward refs
  - [x] Deal with *args, **kwargs, *, / correctly
  - [x] fix: no name conflicts if same class name exists
  - [x] Add to Refactoring menu
- [x] fix: Populate should perform imports for leaf node types
- [x] refactor: PopulateArgumentsService
- [x] feat: When populating parameters, offer the quickfix only when inside the parentheses
- [x] feat: When populating parameters, ignore any starting with an underscore
- [N] feat: Do not show popup when populating required args only
- [x] test normal classes when introducing types
- [x] Make private/public
- [x] fix: make quick fix appear on whole import statement if single one? `from domain._other import MyStr`
- [x] refactor: More PSI-based helpers
- [x] fix: FP export: `from prance import BaseParse<caret>r`
- [x] refactor:
  `com.github.chbndrhnns.betterpy.features.inspections.PyPrivateModuleImportInspection.buildVisitor`
    - Use either node.fromImports, node.importBlock or node.importtargets
- [x] Generate TypeEvalContext once, reuse
- [x] fix: Do not rename assignment target when introducing custom type
- [x] fix: populate nothing if arguments there already: `map(str, range(5))`
- [x] fix: Do not offer populate... if only kwargs/args(?)
- [N] refactor: How do add class name to dataclass preview?
- [x] Suggest most specific import if parent package is same: Revisit test
- [x] refactor: Better way for `isEnumAssignment`?
- [x] refactor: PyDataclassMissingInspection
- [x] feat: Inspect dataclass subclasses for @dataclass decorator

```python
import dataclasses


@dataclasses.dataclass
class Base: ...


class Child(Base):
    field: int


def test_():
    Child()
```

- [x] fix: Do not offer custom type on docstring
- [x] feat: Filter usages by type annotations
- [x] fix: Export newtype
- [x] feat: Suggest most specific import if parent package is same
- [x] feat: Suggest import from package instead of module if exported
- [x] fix: Fixed depth of 10 when resolving references
- [x] Use PSI helpers to find isinstance checks (look for more opportunities)
- [x] feat: Wrap when Indirect reference
- [x] feat: Wrap for enum should use variant instead of calling
- [x] fix: Do not offer custom type in loop variable
- [x] fix: Do not offer custom type for isinstance checks
- [x] fix: Do not make available when parsing error in file
- [x] ignore_testParameterAnnotationUpdateWrapsCallSites
- [x] Restore roots prefix
- [x] fix: No wrap with Self

```python
class C:
    def do(self, val: str):
        ...

    def other(self):
        self.do("abc")

```

- [x] fix: Missing description for PyPrivateModuleImportInspection
- [x] fix: Messed up imports

```python

# missing/__init__.py
class PublicClass:
    pass


__all__ = ['PublicClass']


# missing/_mob.py
def public_<


caret > function():
pass
```

leads to:

```python
# missing/__init__.py
class PublicClass:
    pass


__all__ = ['PublicClass', 'public_function']

from ._mob import PublicClass, public_function

```

- [x] feat: Copy package as code snippet
- [x] Use exported symbol instead of private... should not be suggested in this case:

```python
# ./myp/__init__.py
__all__ = ['One']

from ._one import One


# ./myp/one.py
class One: ...


# ./myp/two.py
from ._one import One

_ = One
```

- [x] Export: Quick fix no longer available for public modules]
- [x] Export: Only add warning for export if module is private
- [x] Do not show custom type intent for docstrings

## Completed Tasks (oldest first)

- [x] Extract helpers between intentions
- [x] Use actual values in preview
- [x] Perform imports
- [x] Wrap correctly if function argument has unnecessary parentheses
- [x] introduce type should not remove function call

```
def foo(x: str):
    pass

foo(*[1])
```

- [x] Do not offer wrap if unwrap does the job:
    ```
    class CustomInt(int):
        pass

    val: list[int] = [
    CustomInt(1),
    ] 
  ```
- [x] Bug: Offers wrap with int: `va<caret>l: dict[str, int] = dict(a=1, b=2, c=3)`
- [x] Assert on complete text: testDict_GeneratesGenericCustomTypeAndKeepsArguments
- [x] Assert on complete text: testList_GeneratesCustomType
- [x] Move heavy test base out of wrap
- [x] Should not wrap content in f-string

```
abc: str = "abc"
s = f"{abc}"
```

- [x] Should wrap INSIDE of f-string

```
def do(a: str):
    return f"{a}"
```

- [x] FP: Suggests type of class variable instead of parameter name

```python
class Client:
    version: int

    def __init__(self, val: str) -> None:
        self.val = val


Client(val="abc")
```

- [x] How to deal with this: `print("a<caret>bc")`. It suggests to wrap with object()
- [x] Generate name correctly if caret is on default value:
  `def extract_saved_reels(self, output_dir: str = "saved_ree<caret>ls"):`
- [x] Disable introduce type intention at function call result

```py
def do():
    return 1


val = d < caret > o()
```

- [x] Introduce type puts it on LHS

```py
def do():
    return 1


v < caret > al = do()
```

- [x] FP: Wrap `print("abc")` with object...
- [x] FP: Populate missing: `time.sleep(1)`
- [x] Custom Type: Use field name from dataclass
- [x] Wrap: Do not wrap ellipsis
- [x] Fix testWrapWithOptionalPicksInnerTypeNotNone
- [x] Fix testWrapWithPep604UnionChoosesFirstBranch
- [x] Fix testWrapWithUnionCustomFirstPicksCustom
- [x] Remove hard-coded: `if (calleeName == "print")`
- [x] Introduce wrap hierachies for unions: builtin, stdlib, custom -> do not offer if from same group
- [x] How to handle library code? Disable introduce type for library code?
- [x] Wrap: on type inspection error OR on union type with different groups
- [x] Update tests to match complete document
- [x] Loses annotation:

```python
class CustomWrapper(str): ...


def f(x: CustomWrapper | str) -> None:
    pass


f("abc")
```

- [x] Fix PluginException Caused by PyMissingInDunderAllInspection
- [x] Create __all__ if not exists
- [x] Can we turn builtinName into a type instead of a string? -> no, too heavy
- [x] Do not inspect libraries/dependencies