package com.github.chbndrhnns.intellijplatformplugincopy.intention.parameterobject

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors

fun withMockIntroduceParameterObjectDialog(action: () -> Unit) {
    UiInterceptors.register(object :
        UiInterceptors.UiInterceptor<IntroduceParameterObjectDialog>(IntroduceParameterObjectDialog::class.java) {
        override fun doIntercept(component: IntroduceParameterObjectDialog) {
            component.close(DialogWrapper.OK_EXIT_CODE)
        }
    })
    try {
        action()
    } finally {
        UiInterceptors.clear()
    }
}
