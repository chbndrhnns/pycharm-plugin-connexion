To move a symbol (like a class, function, or global variable) programmatically in a PyCharm plugin test, you should use the `PyMoveModuleMembersProcessor`. This processor handles the complex logic of moving Python symbols between files, including updating imports and references.

Here is how to implement this in your test code.

### 1. Required Dependencies
You will need to import the processor and relevant PSI classes:

```java
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersProcessor;
// For finding symbols (optional, depending on how you locate them)
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
```

### 2. Implementation Example
Here is a helper method that finds a symbol by name and moves it to a destination file. This mirrors the logic found in `PyMoveTest.java`.

```java
public void moveSymbolInTest(String symbolName, String destinationFilePath) {
    final Project project = myFixture.getProject();

    // 1. Find the symbol to move (e.g., a class or function)
    // You can use your own logic here, or a helper like this:
    PsiNamedElement symbolToMove = findSymbolByName(symbolName); 
    assertNotNull("Symbol not found: " + symbolName, symbolToMove);

    // 2. Prepare the destination path
    // The processor expects an absolute path to the *destination file* (not just the directory)
    VirtualFile destFile = myFixture.findFileInTempDir(destinationFilePath);
    String fullDestPath;
    if (destFile != null) {
        fullDestPath = destFile.getPath();
    } else {
        // If the file doesn't exist yet, construct the full path
        fullDestPath = myFixture.getTempDirFixture().getFile(".").getPath() + "/" + destinationFilePath;
    }

    // 3. Run the processor
    // It takes an array of elements, allowing you to move multiple symbols at once
    new PyMoveModuleMembersProcessor(
        new PsiNamedElement[]{symbolToMove}, 
        fullDestPath
    ).run();
}

// Helper to find a top-level symbol in the project
private PsiNamedElement findSymbolByName(String name) {
    // Implementation depends on your needs. 
    // Could utilize PyClassNameIndex, PyFunctionNameIndex, etc.
    // Or simpler for tests if you have a known file:
    // ((PyFile)myFixture.findFileInTempDir("src/source.py")).findTopLevelClass(name);
    return null; // implementation omitted
}
```

### 3. Key Components

*   **`PyMoveModuleMembersProcessor`**: The specific refactoring processor for Python module members.
*   **`PsiNamedElement[]`**: The constructor accepts an array, so you can move multiple symbols (e.g., a class and its helper function) in a single transaction.
*   **Destination Path**: Unlike the file mover, this processor typically expects the path to the **target python file** (e.g., `.../new_module.py`), not just a directory. If the file doesn't exist, the processor will create it.

### 4. Usage in a Test Case

```java
public void testMoveFunction() {
    // Setup: Create a source file with a function
    myFixture.addFileToProject("src/source.py", "def my_func():\n    pass");
    
    // Find the element (simplest way for tests)
    PsiFile sourceFile = myFixture.findFileInTempDir("src/source.py");
    PyFunction func = ((PyFile) sourceFile).findTopLevelFunction("my_func");
    
    // Execute Move
    String destPath = myFixture.getTempDirFixture().getFile(".").getPath() + "/src/dest.py";
    new PyMoveModuleMembersProcessor(new PsiNamedElement[]{func}, destPath).run();

    // Verify results
    assertNotNull(myFixture.findFileInTempDir("src/dest.py")); // File created
    // You would then check that source.py no longer has the function
    // and dest.py does.
}
```