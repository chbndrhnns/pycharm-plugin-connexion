# Internal APIs

## Todo

- [ ] #Internal interface com.intellij.codeInsight.daemon.impl.IntentionMenuContributor reference
  Internal interface com.intellij.codeInsight.daemon.impl.IntentionMenuContributor is referenced in
  com.github.chbndrhnns.betterpy.features.intentions.suppressor.SuppressSuggestedRefactoringIntentionMenuContributor.
  This interface is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the class is not supposed to be used in
  client code.

## Keep

- [x] #Internal method com.intellij.util.SlowOperations.knownIssue(String) invocation
  Internal method com.intellij.util.SlowOperations.knownIssue(java.lang.String ytIssueId) :
  com.intellij.openapi.application.AccessToken is invoked in
  com.github.chbndrhnns.betterpy.features.intentions.customtype.CustomTypeApplier.startInlineRename$lambda$6(
  SmartPsiElementPointer, Project, Editor) : void. This method is marked with
  @org.jetbrains.annotations.ApiStatus.Internal annotation or @com.intellij.openapi.util.IntellijInternalApi annotation
  and indicates that the method is not supposed to be used in client code.

  -> spec/slow-operations.md

## Done

- [x] #Internal method com.jetbrains.python.psi.PyImportElement.resolve() invocation
  Internal method com.jetbrains.python.psi.PyImportElement.resolve() : com.intellij.psi.PsiElement is invoked in
  com.github.chbndrhnns.betterpy.core.psi.PyResolveUtils.findMember(PsiElement, String, boolean) :
  PsiElement. This method is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed to be used in
  client code.
  Internal method com.jetbrains.python.psi.PyImportElement.resolve() : com.intellij.psi.PsiElement is invoked in
  com.github.chbndrhnns.betterpy.features.intentions.populate.PyValueGenerator.generateClassTypeValue(
  PyClassType, TypeEvalContext, int, PyElementGenerator, LanguageLevel, ScopeOwner) : PyValueGenerator.GenerationResult.
  This method is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed to be used in
  client code.


- [x] #Internal class com.jetbrains.python.psi.types.PyTypeUtil reference
  Internal class com.jetbrains.python.psi.types.PyTypeUtil is referenced in
  com.github.chbndrhnns.betterpy.features.intentions.wrap.UnionCandidates.collectFromTypes(PyTypedElement,
  PyExpression) : List. This class is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the class is not supposed to be used in
  client code.


- [x] #Internal class com.jetbrains.python.sdk.legacy.PythonSdkUtil reference
  Internal class com.jetbrains.python.sdk.legacy.PythonSdkUtil is referenced in
  com.github.chbndrhnns.betterpy.core.services.PythonStdlibService.getStdlibModules(Sdk) : Set. This class
  is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the class is not supposed to be used in
  client code.
  Internal class com.jetbrains.python.sdk.legacy.PythonSdkUtil is referenced in
  com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard.isSatisfied(Project) : boolean. This class
  is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the class is not supposed to be used in
  client code.

- [x] #Internal method com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.isCustomEnum(PyClass,
  TypeEvalContext) invocation
  Internal method com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider.isCustomEnum(
  com.jetbrains.python.psi.PyClass cls, com.jetbrains.python.psi.types.TypeEvalContext context) : boolean is invoked in
  com.github.chbndrhnns.betterpy.features.intentions.customtype.TargetDetector.isEnumAssignment(PyExpression,
  TypeEvalContext) : boolean. This method is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed to be used in
  client code.


- [x] #Internal method com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk(Module) invocation
 Internal method com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk(com.intellij.openapi.module.Module
 module) : com.intellij.openapi.projectRoots.Sdk is invoked in
  com.github.chbndrhnns.betterpy.core.python.PythonVersionGuard.isSatisfied(Project) : boolean. This method
 is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
 @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed to be used in
 client code.
 Internal method com.jetbrains.python.sdk.legacy.PythonSdkUtil.findPythonSdk(com.intellij.openapi.module.Module
 module) : com.intellij.openapi.projectRoots.Sdk is invoked in
  com.github.chbndrhnns.betterpy.core.services.PythonStdlibService.getStdlibModules(Sdk) : Set. This method
 is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
 @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed to be used in
 client code.

- [x] #Internal method com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.getHighlights(Document,
  HighlightSeverity, Project) invocation
  Internal method com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.getHighlights(
  com.intellij.openapi.editor.Document document, com.intellij.lang.annotation.HighlightSeverity minSeverity,
  com.intellij.openapi.project.Project project) : java.util.List is invoked in
  com.github.chbndrhnns.betterpy.features.intentions.IntroduceCustomTypeFromStdlibIntention.hasBlockingInspections(
  Project, Editor, TextRange) : boolean. This method is marked with @org.jetbrains.annotations.ApiStatus.Internal
  annotation or @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed
  to be used in client code.

- [x] #Internal class com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl reference
  Internal class com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl is referenced in
  com.github.chbndrhnns.betterpy.features.intentions.IntroduceCustomTypeFromStdlibIntention.hasBlockingInspections(
  Project, Editor, TextRange) : boolean. This class is marked with @org.jetbrains.annotations.ApiStatus.Internal
  annotation or @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the class is not supposed
  to be used in client code.

- [x] #Internal method com.jetbrains.python.psi.types.PyTypeUtil.toStream(PyType) invocation
  Internal method com.jetbrains.python.psi.types.PyTypeUtil.toStream(com.jetbrains.python.psi.types.PyType type) :
  one.util.streamex.StreamEx is invoked in
  com.github.chbndrhnns.betterpy.features.intentions.wrap.UnionCandidates.collectFromTypes(PyTypedElement,
  PyExpression) : List. This method is marked with @org.jetbrains.annotations.ApiStatus.Internal annotation or
  @com.intellij.openapi.util.IntellijInternalApi annotation and indicates that the method is not supposed to be used in
  client code.
