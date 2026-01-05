package com.github.chbndrhnns.intellijplatformplugincopy.inspections

import com.github.chbndrhnns.intellijplatformplugincopy.PluginConstants
import com.github.chbndrhnns.intellijplatformplugincopy.intention.abstractmethod.AbstractMethodUtils
import com.github.chbndrhnns.intellijplatformplugincopy.settings.PluginSettingsState
import com.github.chbndrhnns.intellijplatformplugincopy.util.isOwnCode
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction

class PyAbstractMethodNotImplementedInChildInspection : PyInspection() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        if (!holder.file.isOwnCode()) return PsiElementVisitor.EMPTY_VISITOR
        if (!PluginSettingsState.instance().state.enableAbstractMethodNotImplementedInspection) {
            return object : PyElementVisitor() {}
        }
        return object : PyElementVisitor() {
            override fun visitPyClass(node: PyClass) {
                super.visitPyClass(node)
                
                val abstractMethods = node.methods.filter { AbstractMethodUtils.isAbstractMethod(it) }
                if (abstractMethods.isEmpty()) return

                // Check inheritors
                val inheritors = AbstractMethodUtils.findInheritorsInScope(node, node.project)
                
                val missingPerInheritor = mutableMapOf<PyClass, List<PyFunction>>()
                
                for (inheritor in inheritors) {
                    // Check if inheritor is abstract
                    if (isAbstract(inheritor)) continue

                    val missing = abstractMethods.filter { abstractMethod ->
                        val implementedMethod = inheritor.findMethodByName(abstractMethod.name, true, null)
                        if (implementedMethod is PyFunction) {
                            AbstractMethodUtils.isAbstractMethod(implementedMethod)
                        } else {
                            // If null or not PyFunction (e.g. PyTargetExpression), assume implemented if not null.
                            implementedMethod == null
                        }
                    }
                    
                    if (missing.isNotEmpty()) {
                        missingPerInheritor[inheritor] = missing
                    }
                }

                if (missingPerInheritor.isNotEmpty()) {
                    val description = if (missingPerInheritor.size == 1) {
                         val (child, methods) = missingPerInheritor.entries.first()
                         "Child class '${child.name}' is missing implementation for abstract methods: ${methods.joinToString { it.name ?: "?" }}"
                    } else {
                         val missingMethods = missingPerInheritor.values.flatten().distinct().mapNotNull { it.name }.joinToString(", ")
                         "Child classes are missing implementations for abstract methods: $missingMethods"
                    }
                    
                    val nameIdentifier = node.nameIdentifier ?: return
                    val fix = ImplementAbstractMethodsFix(node)
                    holder.registerProblem(nameIdentifier, description, fix)
                }
            }
        }
    }
    
    private fun isAbstract(pyClass: PyClass): Boolean {
         return pyClass.methods.any { AbstractMethodUtils.isAbstractMethod(it) }
    }

    class ImplementAbstractMethodsFix(baseClass: PyClass) : LocalQuickFix {
        
        private val baseClassPointer: SmartPsiElementPointer<PyClass> = 
            SmartPointerManager.getInstance(baseClass.project).createSmartPsiElementPointer(baseClass)

        override fun getFamilyName(): String =
            PluginConstants.ACTION_PREFIX + "Implement missing abstract methods in child classes"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val baseClass = baseClassPointer.element ?: return
            
            val abstractMethods = baseClass.methods.filter { AbstractMethodUtils.isAbstractMethod(it) }
            val inheritors = AbstractMethodUtils.findInheritorsInScope(baseClass, project)
            
            val missingPerInheritor = mutableMapOf<PyClass, List<PyFunction>>()

            for (inheritor in inheritors) {
                 if (inheritor.methods.any { AbstractMethodUtils.isAbstractMethod(it) }) continue

                 val missing = abstractMethods.filter { abstractMethod ->
                    val implementedMethod = inheritor.findMethodByName(abstractMethod.name, true, null)
                    if (implementedMethod is PyFunction) {
                        AbstractMethodUtils.isAbstractMethod(implementedMethod)
                    } else {
                        implementedMethod == null
                    }
                }
                
                if (missing.isNotEmpty()) {
                    missingPerInheritor[inheritor] = missing
                }
            }
            
            if (missingPerInheritor.isNotEmpty()) {
                AbstractMethodUtils.implementMethodsInInheritors(missingPerInheritor, baseClass.containingFile, null)
            }
        }
    }
}
