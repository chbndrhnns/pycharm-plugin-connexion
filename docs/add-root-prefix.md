Hooking into PyCharm's Import Quickfix to Restore Stripped Source Root Prefixes
After analyzing the IntelliJ Community codebase specifically the Python plugin, here's a comprehensive guide on how to hook into the
import quickfix mechanism and restore stripped prefixes.

Understanding the Problem
When you configure a source root in PyCharm e.g., marking src/ as a source root, PyCharm computes import paths relative to that source
root. This happens in:

• QualifiedNameFinder.computeQualifiedNameInRoot() python-psi-impl/src/com/jetbrains/python/psi/resolve/QualifiedNameFinder.java, line
296
• It uses VfsUtilCore.getRelativePath(file, root) which strips the source root prefix

For example, if you have src/mypackage/module.py and src/ is marked as a source root, the import will be from mypackage import module
instead of from src.mypackage import module.

Solution: Two Extension Points Available
Option 1: PyCanonicalPathProvider Recommended
This extension point allows you to modify the qualified name after it's computed. It's called in
QualifiedNameFinder.canonizeQualifiedName().

Interface python-psi-api/src/com/jetbrains/python/psi/resolve/PyCanonicalPathProvider.java:

public interface PyCanonicalPathProvider {
ExtensionPointName<PyCanonicalPathProvider> EP_NAME =
ExtensionPointName.create("Pythonid.canonicalPathProvider");
@Nullable
QualifiedName getCanonicalPath(@Nullable PsiElement symbol,
@NotNull QualifiedName qName,
@Nullable PsiElement foothold);
}

Example Implementation based on PyStdlibCanonicalPathProvider:

class MySourceRootPrefixProvider : PyCanonicalPathProvider {
override fun getCanonicalPath(
symbol: PsiElement?,
qName: QualifiedName,
foothold: PsiElement?
): QualifiedName? {
// Check if this symbol should have a prefix restored
val virtualFile = PsiUtilCore.getVirtualFile(symbol) ?: return null

           // Your logic to determine if prefix should be restored
           // For example, check if file is under a specific directory
           val prefix = determinePrefix(virtualFile, foothold)

           return if (prefix != null) {
               // Prepend the prefix to the qualified name
               QualifiedName.fromDottedString(prefix).append(qName)
           } else {
               null // Return null if no modification needed
           }
       }

       private fun determinePrefix(file: VirtualFile, foothold: PsiElement?): String? {
           // Implement your logic to determine what prefix to restore
           // e.g., check project settings, file location, etc.
           return null
       }
}

Register in plugin.xml:

   <extensions defaultExtensionNs="Pythonid">
       <canonicalPathProvider
           implementation="com.yourplugin.MySourceRootPrefixProvider"/>
   </extensions>

Option 2: PyImportCandidateProvider
This extension point allows you to add additional import candidates to the quickfix popup.

Interface python-psi-impl/src/com/jetbrains/python/codeInsight/imports/PyImportCandidateProvider.java:

public interface PyImportCandidateProvider {
ExtensionPointName<PyImportCandidateProvider> EP_NAME =
ExtensionPointName.create("Pythonid.importCandidateProvider");
void addImportCandidates(PsiReference reference,
String name,
AutoImportQuickFix quickFix);
}

Example Implementation based on PyJavaImportCandidateProvider:

class MyImportCandidateProvider : PyImportCandidateProvider {
override fun addImportCandidates(
reference: PsiReference,
name: String,
quickFix: AutoImportQuickFix
) {
val element = reference.element
val project = element.project

           // Find symbols and add them with your custom import path
           val customPath = QualifiedName.fromDottedString("src.mypackage")

           // Add your custom import candidate
           // quickFix.addImport(importable, file, customPath, alias)
       }
}

Register in plugin.xml:

   <extensions defaultExtensionNs="Pythonid">
       <importCandidateProvider
           implementation="com.yourplugin.MyImportCandidateProvider"/>
   </extensions>

Key Classes to Understand
| Class | Location | Purpose |
|-------|----------|---------|
| AutoImportQuickFix | python-psi-impl/.../imports/ | Main quickfix class, holds import candidates |
| ImportCandidateHolder | python-psi-impl/.../imports/ | Holds individual import candidate info |
| PyImportCollector | python-psi-impl/.../imports/ | Collects import candidates |
| QualifiedNameFinder | python-psi-impl/.../resolve/ | Computes import paths |
| RootVisitorHost | python-psi-impl/.../resolve/ | Visits source roots |

Testing Your Implementation
Test Setup Pattern
Use PsiTestUtil.addSourceRoot() to configure source roots in tests:

class MyImportQuickFixTest : PyQuickFixTestCase() {

       fun testRestoreSourceRootPrefix() {
           // Copy test files
           myFixture.copyDirectoryToProject("testData", "")

           // Configure source roots
           val sourceRoot = myFixture.findFileInTempDir("src")
           myFixture.runWithSourceRoots(listOf(sourceRoot)) {
               // Configure the file with unresolved reference
               myFixture.configureByFile("src/main.py")

               // Enable the inspection
               myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)

               // Get the quickfix and verify import candidates
               val fixes = myFixture.getAllQuickFixes()
               // Assert your expected import path is present
           }
       }
}

Using runWithSourceRoots Helper
The test fixture provides runWithSourceRoots() which:

1. Adds source roots via PsiTestUtil.addSourceRoot(module, root)
2. Runs your test code
3. Cleans up via PsiTestUtil.removeSourceRoot(module, root)

Test Data Structure
testData/
├── src/                    # Mark as source root
│   ├── mypackage/
│   │   ├── __init__.py
│   │   └── module.py       # Contains symbol to import
│   └── main.py             # Has unresolved reference

Recommendation
Use PyCanonicalPathProvider if you want to:

• Modify the import path for existing candidates
• Apply a transformation rule like prepending a prefix

Use PyImportCandidateProvider if you want to:

• Add completely new import candidates
• Provide alternative import paths alongside the default ones

For your use case of restoring stripped source root prefixes, PyCanonicalPathProvider is the cleaner approach since you're modifying
existing paths rather than adding new candidates.