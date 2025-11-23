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

    /**
     * Create a simple custom type definition: `class <name>(<builtin>): pass`.
     *
     * When the builtin originates from a subscripted container annotation
     * (e.g. ``dict[str, list[int]]``) the full text – including its type
     * arguments – should be passed in via [builtin]. This ensures that
     * existing type arguments are carried over to the base class of the
     * generated container wrapper. For example, starting from
     *
     * ```python
     * def do(arg: dict[str, list[int]]) -> None: ...
     * ```
     *
     * we generate:
     *
     * ```python
     * class CustomDict(dict[str, list[int]]):
     *     pass
     *
     * def do(arg: CustomDict[str, list[int]]) -> None: ...
     * ```
     */
    fun createClass(project: Project, name: String, builtin: String): PyClass {
        val generator = PyElementGenerator.getInstance(project)
        val classText = buildClassText(name, builtin)
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

    private fun buildClassText(name: String, builtin: String): String {
        // [builtin] may already include concrete type arguments (for example
        // ``dict[str, list[int]]``). We simply embed it as the base class so
        // the generated definition is always of the form
        //
        //   class Name(<builtin>):
        //       pass
        //
        // without adding extra generic parameters on the custom class.
        return "class $name($builtin):\n    pass"
    }
}
