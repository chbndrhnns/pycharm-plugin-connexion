package com.github.chbndrhnns.betterpy.features.refactoring.parameterobject

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors

const val INTRODUCE_PARAMETER_OBJECT_ACTION_ID =
    "com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.IntroduceParameterObjectRefactoringAction"
const val INLINE_PARAMETER_OBJECT_ACTION_ID =
    "com.github.chbndrhnns.betterpy.features.refactoring.parameterobject.InlineParameterObjectRefactoringAction"
const val INTRODUCE_PARAMETER_OBJECT_INTENTION_TEXT = "BetterPy: Introduce parameter object"

fun withMockIntroduceParameterObjectDialog(
    configure: (IntroduceParameterObjectDialog) -> Unit = {},
    action: () -> Unit
) {
    UiInterceptors.register(object :
        UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
        override fun doIntercept(component: IntroduceParameterObjectDialog) {
            configure(component)
            component.close(DialogWrapper.OK_EXIT_CODE)
        }
    })
    try {
        action()
    } finally {
        UiInterceptors.clear()
    }
}
