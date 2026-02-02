package com.github.chbndrhnns.betterpy.core.index

import com.github.chbndrhnns.betterpy.core.pytest.PytestFixtureUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyFile

/**
 * File-based index that maps pytest fixture names to files containing them.
 * This enables efficient lookup of candidate files when resolving fixture overrides.
 *
 * This index only processes project source files, excluding stdlib and third-party dependencies.
 */
class PytestFixtureFileIndex : ScalarIndexExtension<String>() {

    companion object {
        val NAME: ID<String, Void> = ID.create("betterpy.pytest.fixture.file")

        /**
         * Finds all files that define a fixture with the given name.
         *
         * @param fixtureName The fixture name to search for
         * @param project The project to search in
         * @param scope The search scope
         * @return Collection of virtual files containing the fixture
         */
        fun findFilesWithFixture(
            fixtureName: String,
            project: Project,
            scope: GlobalSearchScope
        ): Collection<VirtualFile> {
            return FileBasedIndex.getInstance().getContainingFiles(NAME, fixtureName, scope)
        }
    }

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { inputData ->
            val result = mutableMapOf<String, Void?>()

            if (inputData.fileType == PythonFileType.INSTANCE) {
                val psiFile = inputData.psiFile as? PyFile ?: return@DataIndexer result

                psiFile.topLevelFunctions.forEach { function ->
                    val fixtureName = PytestFixtureUtil.getFixtureName(function) ?: return@forEach
                    result[fixtureName] = null
                }

                psiFile.topLevelClasses.forEach { pyClass ->
                    pyClass.methods.forEach { method ->
                        val fixtureName = PytestFixtureUtil.getFixtureName(method) ?: return@forEach
                        result[fixtureName] = null
                    }
                }

                psiFile.statements.filterIsInstance<PyAssignmentStatement>().forEach { assignment ->
                    val fixtureName = PytestFixtureUtil.getAssignedFixture(assignment)?.fixtureName ?: return@forEach
                    result[fixtureName] = null
                }
            }
            result
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 4

    /**
     * Input filter that accepts Python files in project content or library roots.
     */
    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return object : FileBasedIndex.ProjectSpecificInputFilter {

            override fun acceptInput(file: IndexedFile): Boolean {
                val vFile = file.file
                if (vFile.fileType != PythonFileType.INSTANCE) return false
                val project = file.project ?: return false
                val fileIndex = ProjectFileIndex.getInstance(project)
                return (fileIndex.isInContent(vFile) || fileIndex.isInLibrary(vFile)) &&
                        !fileIndex.isExcluded(vFile)
            }
        }
    }

    override fun dependsOnFileContent(): Boolean = true
}
