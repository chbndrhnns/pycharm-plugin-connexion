package com.github.chbndrhnns.intellijplatformplugincopy.core.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyLambdaExpression

/**
 * File-based index that tracks files containing lambda expressions.
 * This enables efficient lookup of files that contain lambdas, avoiding expensive
 * iteration over all Python files when searching for lambda implementations of protocols.
 *
 * The index stores a marker key "lambda" for each file that contains at least one lambda expression.
 *
 * This index only processes project source files, excluding stdlib and third-party dependencies.
 *
 * Used by [com.github.chbndrhnns.intellijplatformplugincopy.features.search.PyCallableProtocolMatcher]
 * to efficiently find files containing lambdas for protocol matching.
 */
class PyLambdaFileIndex : ScalarIndexExtension<String>() {

    companion object {
        val NAME: ID<String, Void> = ID.create("Py.lambda.file")

        /**
         * Marker key used to indicate a file contains lambdas.
         */
        const val LAMBDA_MARKER = "lambda"

        /**
         * Finds all files that contain lambda expressions.
         *
         * @param project The project to search in
         * @param scope The search scope
         * @return Collection of virtual files containing lambda expressions
         */
        fun findFilesWithLambdas(
            project: Project,
            scope: GlobalSearchScope
        ): Collection<VirtualFile> {
            return FileBasedIndex.getInstance().getContainingFiles(NAME, LAMBDA_MARKER, scope)
        }
    }

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { inputData ->
            val result = mutableMapOf<String, Void?>()

            if (inputData.fileType == PythonFileType.INSTANCE) {
                val psiFile = inputData.psiFile as? PyFile ?: return@DataIndexer result

                // Check if the file contains any lambda expressions
                val hasLambda = PsiTreeUtil.findChildOfType(psiFile, PyLambdaExpression::class.java) != null

                if (hasLambda) {
                    result[LAMBDA_MARKER] = null
                }
            }
            result
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    /**
     * Input filter that only accepts Python files in project source content.
     * This excludes stdlib, third-party dependencies, and library files.
     */
    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return object : FileBasedIndex.ProjectSpecificInputFilter {
            override fun acceptInput(file: IndexedFile): Boolean {
                // Only index Python files that are in project source content (not libraries/SDK)
                val vFile = file.file
                if (vFile.fileType != PythonFileType.INSTANCE) return false
                val project = file.project ?: return false
                val fileIndex = ProjectFileIndex.getInstance(project)
                return fileIndex.isInSourceContent(vFile) && !fileIndex.isInLibrary(vFile)
            }
        }
    }

    override fun dependsOnFileContent(): Boolean = true
}
