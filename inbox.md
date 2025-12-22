# Inbox

## Pending Issues

- [ ] fix: Inspection "Symbol 'InMemNetboxPrefixMatches' is not exported in package __all__" should ignore private
  packages and not trigger if it's exported in the next public package
- [ ] refactor: why do we need a different check during tests? `PyWrapHeuristics.isProtocol`
- [ ] fix: Add to __all__ is missing the plugin prefix and shold probably go away
- [ ] fix: Marks symbols referenced in `path()` as unresolved although they exist (imported)
- [ ] fix: Duplicate entries in refactoring menu
- [ ] fix: Combine settings for parameter object
- [ ] fix: Offer parameter object on whole function signature
- [ ] fix: Do not offer local variable here `if __name__ == '__main__':`
- [ ] fix: Making one symbol public via import place quickfix makes all other symbols unimported
- [ ] fix: When prefering relative import inside same package, do not add candidate if relative import exists already
- [ ] fix: implement abstract method is put above __init__
- [ ] feat: Consider if this should only work for dataclass/classes, for others, there is change signature
- [ ] fix: cancel introduce type rename dialog still keeps it
- [ ] fix: Import `import bla as xyz` triggers warning about not exported in all -> repro?
- [ ] fix: How to handle ProcessCanceledException correctly?

## Pending Features

- [ ] feat: copy special: copy test as pytest node id reference
- [ ] feat: If callable is expected, complete without parens
- [ ] feat: Inline parameter object should ask to remove/other invocations
- [ ] feat: Transform between lambda, function, partial
- [ ] feat: Import should reuse existing module import (domain.ABC, domain.DEFG)
- [ ] feat: Render quick doc for pydantic/dataclass, showing inherited members, as well
- [ ] feat: In case of unexpected type, offer cast to the expected type
- [S] feat: Turn dict into dataclass/pydantic model (spec/upgrade-dict.md)
- [ ] feat: introduce custom type as newtype, as well, or other custom constructs
- [ ] feat: Change visibility: make private (we only have two options and one is always true)
- [ ] feat: Convert parametrized test to multiple unparametrized tests
- [ ] feat: Add field to dataclass/model in case of unexpected argument
- [ ] feat: Suggest expected type for container types, like `vals: set[MyType] = set(<caret>)`
- [ ] feat: pytest param: treat text as ref
- [ ] feat: Move test inside of class
- [ ] feat: When suggesting the expected type and it is an inner class, reference it using outer_class.inner_class
  syntax, recursively
- [ ] feat: "Choose implementation" should search in class names, as well.
- [ ] feat: Ignore third-party packages in import suggestions
- [ ] feat: Treat unresolved references as errors
- [ ] feat: Wrap with pytest.raises()
- [ ] feat: Move symbol to top level (or one scope up?)
- [ ] feat: Add pytest nodes to separate view in search everywhere (currently, `pytest` seems not respected)
- [ ] feat: When implementing abstract method, present dialog to choose or show edits
- [ ] feat: Move instance method
- [ ] feat: Move to inner class
- [ ] feat: Move symbol to any of the currently imported packages/modules, change references
- [ ] feat: Inspect duplicates in __all__
- [ ] feat: search everywhere: search for partial matches in test names
- [ ] feat: copy element with dependencies should generate a preview about the direct copy (not including dependencies)
- [ ] When populating arguments, highlight the target function (area) when the popup appears
- [ ] feat: quick fix to remove unresolved import from __all__
- [ ] feat: Turn into type checking import
- [ ] roots: adopt when moving files
- [ ] roots: mark as unresolved reference without
- [ ] feat: populate union (docs/populate/populate-union.md)
- [ ] feat: Generate dataclass, basemodel
- [ ] feat: Create run config using package mode
- [ ] Copy pydantic mock?
- [ ] feat: Wrap default/default_factory after introducing custom type in dataclass/pydantic
- [ ] feat: Convert fn to lambda
- [ ] feat: Block list: Do not allow names from typing or collections.abc.
- [ ] feat: Configure default base classes
- [ ] commit: if it fails for hooks, add option keep selection of files (docs/keep-commit-selection/spec.md, stash)
- [ ] feat: Show red bubble if introduce parameter object is not available
- [ ] feat: Collect symbols to check against for stdlib clashes dynamically
- [ ] feat: Support what is tested in _testNestedConstructor_InsideDict_WrapsInnerArgument

## In Progress Tasks

- [ ] feat: Offer NamedTuple, TypedDict and pydantic.BaseModel as bases for parameter object
- [ ] feat: Re-use import for qualified names
- [ ] refactor: Performance (spec/performance.md)

## Completed Tasks (newest first)

- [ ] feat: Introduce type alias for union type
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
	at com.github.chbndrhnns.intellijplatformplugincopy.exports.PyAllExportUtil.insertStatementBelowDocstring(PyAllExportUtil.kt:208)
	at com.github.chbndrhnns.intellijplatformplugincopy.exports.PyAllExportUtil.createNewDunderAll(PyAllExportUtil.kt:178)
	at com.github.chbndrhnns.intellijplatformplugincopy.exports.PyAllExportUtil.ensureSymbolExported(PyAllExportUtil.kt:47)
	at com.github.chbndrhnns.intellijplatformplugincopy.exports.PyExportSymbolToTargetIntention.exportToTarget$lambda$6(PyExportSymbolToTargetIntention.kt:166)
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
