package com.github.chbndrhnns.betterpy.core.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.PyFile

/**
 * File-based index that maps method/attribute names to files containing classes that define them.
 * This enables efficient lookup of files containing classes with specific members,
 * which is then used to find candidate classes for Protocol implementation matching.
 *
 * Unlike [PyClassMembersIndex] (stub-based), this index works independently of the Python plugin's
 * stub infrastructure and is automatically updated when files change.
 *
 * This index only processes project source files, excluding stdlib and third-party dependencies.
 *
 * Used by [com.github.chbndrhnns.betterpy.features.search.PyProtocolImplementationsSearch]
 * to efficiently find candidate classes that might implement a Protocol.
 */
class PyClassMembersFileIndex : ScalarIndexExtension<String>() {

    companion object {
        val NAME: ID<String, Void> = ID.create("betterpy.class.members.file")

        /**
         * Finds all files that contain classes with a member (method or attribute) with the given name.
         *
         * @param memberName The name of the member to search for
         * @param project The project to search in
         * @param scope The search scope
         * @return Collection of virtual files containing classes with the given member
         */
        fun findFilesWithMember(
            memberName: String,
            project: Project,
            scope: GlobalSearchScope
        ): Collection<VirtualFile> {
            return FileBasedIndex.getInstance().getContainingFiles(NAME, memberName, scope)
        }
    }

    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { inputData ->
            val result = mutableMapOf<String, Void?>()

            if (inputData.fileType == PythonFileType.INSTANCE) {
                val psiFile = inputData.psiFile as? PyFile ?: return@DataIndexer result

                // Index all class members (methods and attributes)
                psiFile.topLevelClasses.forEach { pyClass ->
                    pyClass.methods.forEach { method ->
                        method.name?.let { name ->
                            // Skip dunder methods and private methods for indexing
                            if (!name.startsWith("_")) {
                                result[name] = null
                            }
                        }
                    }
                    pyClass.classAttributes.forEach { attr ->
                        attr.name?.let { name ->
                            // Skip private attributes for indexing
                            if (!name.startsWith("_")) {
                                result[name] = null
                            }
                        }
                    }
                }
            }
            result
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 2

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
