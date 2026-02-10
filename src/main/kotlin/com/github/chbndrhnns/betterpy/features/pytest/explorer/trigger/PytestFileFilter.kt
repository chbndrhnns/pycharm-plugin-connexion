package com.github.chbndrhnns.betterpy.features.pytest.explorer.trigger

/**
 * Determines whether a file change should trigger pytest re-collection.
 * Matches test files (test_*.py, *_test.py) and conftest.py.
 */
object PytestFileFilter {

    fun isTestRelatedFile(fileName: String?): Boolean {
        if (fileName == null) return false
        if (!fileName.endsWith(".py")) return false
        return fileName.startsWith("test_") ||
                fileName.endsWith("_test.py") ||
                fileName == "conftest.py"
    }
}
