package fixtures

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Helper to execute a basic inspection test on a single file.
 *
 * @param testFile The file to configure and test (relative to testData).
 * @param inspection The inspection class to enable.
 * @param fixFamilyName If provided, applies the fix with this family name.
 * @param expectedResultFile If provided, checks the result against this file (relative to testData).
 * @param checkHighlighting Whether to run highlighting check.
 */
fun CodeInsightTestFixture.doInspectionTest(
    testFile: String,
    inspection: Class<out LocalInspectionTool>,
    fixFamilyName: String? = null,
    expectedResultFile: String? = null,
    checkHighlighting: Boolean = true,
    checkWeakWarnings: Boolean = false
) {
    configureByFile(testFile)
    enableInspections(inspection)
    if (checkHighlighting) {
        checkHighlighting(true, false, checkWeakWarnings)
    }

    if (fixFamilyName != null) {
        val fixes = getAllQuickFixes()
        val fixesToApply = fixes.filter { it.familyName == fixFamilyName }
        
        if (fixesToApply.isEmpty()) {
             throw AssertionError("Fix '$fixFamilyName' not found. Available: ${fixes.map { it.familyName }}")
        }
        
        fixesToApply.forEach { launchAction(it) }

        if (expectedResultFile != null) {
            checkResultByFile(expectedResultFile)
        }
    }
}

/**
 * Helper to execute a multi-file inspection test.
 *
 * @param files List of files to configure (relative to testData).
 * @param inspection The inspection class to enable.
 * @param targetFile The file to open/focus on for highlighting/fixing (path as created in temp dir).
 *                   If null, stays on the file opened by configureByFiles (usually the first one).
 * @param fixFamilyName If provided, applies all fixes with this family name.
 * @param resultFileToCheck The file to verify the result in (path as created in temp dir).
 *                          If null, verifies the currently open file.
 * @param expectedResultFile The file containing the expected content (relative to testData).
 * @param expectedResultFiles Map of <File to check in temp dir> to <Expected content file relative to testData>.
 *                            Use this for checking multiple files.
 * @param checkHighlighting Whether to run highlighting check (on the initially opened file or targetFile?).
 *                          This helper runs it on the targetFile if specified, or current file.
 */
fun CodeInsightTestFixture.doMultiFileInspectionTest(
    files: List<String>,
    inspection: Class<out LocalInspectionTool>,
    targetFile: String? = null,
    fixFamilyName: String? = null,
    resultFileToCheck: String? = null,
    expectedResultFile: String? = null,
    expectedResultFiles: Map<String, String> = emptyMap(),
    checkHighlighting: Boolean = true,
    checkWeakWarnings: Boolean = false
) {
    configureByFiles(*files.toTypedArray())
    enableInspections(inspection)

    if (targetFile != null) {
        val file = findFileInTempDir(targetFile)
            ?: throw AssertionError("Target file '$targetFile' not found in temp dir.")
        openFileInEditor(file)
    }

    if (checkHighlighting) {
        checkHighlighting(true, false, checkWeakWarnings)
    }

    if (fixFamilyName != null) {
        val fixes = getAllQuickFixes()
        val fixesToApply = fixes.filter { it.familyName == fixFamilyName }
        
        if (fixesToApply.isEmpty()) {
             throw AssertionError("Fix '$fixFamilyName' not found. Available: ${fixes.map { it.familyName }}")
        }
        
        fixesToApply.forEach { launchAction(it) }

        if (expectedResultFile != null) {
            if (resultFileToCheck != null) {
                val file = findFileInTempDir(resultFileToCheck)
                    ?: throw AssertionError("Result file '$resultFileToCheck' not found in temp dir.")
                openFileInEditor(file)
            }
            checkResultByFile(expectedResultFile)
        }

        expectedResultFiles.forEach { (fileToCheck, expected) ->
            val file = findFileInTempDir(fileToCheck)
                ?: throw AssertionError("Result file '$fileToCheck' not found in temp dir.")
            openFileInEditor(file)
            checkResultByFile(expected)
        }
    }
}
