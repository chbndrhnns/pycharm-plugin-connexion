package com.github.chbndrhnns.intellijplatformplugincopy.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.jetbrains.python.psi.PyClass

/**
 * Stub index that maps method/attribute names directly to classes that define them.
 * This enables O(1) lookup of classes by member name instead of O(n) iteration over all classes.
 *
 * Used by [com.github.chbndrhnns.intellijplatformplugincopy.search.PyProtocolImplementationsSearch]
 * to efficiently find candidate classes that might implement a Protocol.
 */
class PyClassMembersIndex : StringStubIndexExtension<PyClass>() {

    companion object {
        @JvmField
        val KEY: StubIndexKey<String, PyClass> = StubIndexKey.createIndexKey("Py.class.members")

        /**
         * Finds all classes that have a member (method or attribute) with the given name.
         *
         * @param memberName The name of the member to search for
         * @param project The project to search in
         * @param scope The search scope
         * @return Collection of classes that have a member with the given name
         */
        fun findClassesWithMember(
            memberName: String,
            project: Project,
            scope: GlobalSearchScope
        ): Collection<PyClass> {
            return StubIndex.getElements(KEY, memberName, project, scope, PyClass::class.java)
        }
    }

    override fun getKey(): StubIndexKey<String, PyClass> = KEY

    override fun getVersion(): Int = 1
}
