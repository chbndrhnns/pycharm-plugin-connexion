package com.github.chbndrhnns.intellijplatformplugincopy.intention.customtype

import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile

/**
 * Responsible for creating and inserting the newly introduced custom type
 * class. Behaviour mirrors the inline logic that previously lived inside
 * IntroduceCustomTypeFromStdlibIntention.
 */
class CustomTypeGenerator {

    /** Create a simple custom type definition: `class <name>(<builtin>): pass`. */
    fun createClass(project: Project, name: String, builtin: String): PyClass {
        val generator = PyElementGenerator.getInstance(project)
        val classText = "class $name($builtin):\n    pass"
        return generator.createFromText(
            LanguageLevel.getLatest(),
            PyClass::class.java,
            classText,
        )
    }

    /**
     * Insert [newClass] into [targetFile], keeping imports at the top.
     *
     * If the file has imports, the class is inserted *after* the last import
     * statement; otherwise it is inserted at the beginning of the file.
     */
    fun insertClass(targetFile: PyFile, newClass: PyClass): PyClass {
        val importAnchor = targetFile.importBlock.lastOrNull()
        val inserted = if (importAnchor != null) {
            targetFile.addAfter(newClass, importAnchor)
        } else {
            val firstChild = targetFile.firstChild
            targetFile.addBefore(newClass, firstChild)
        }

        return inserted as PyClass
    }
}
