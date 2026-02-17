package com.github.chbndrhnns.betterpy.features.pytest.extractfixture

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.UiInterceptors

fun withMockExtractFixtureDialog(
    fixtureName: String = "extracted_fixture",
    action: () -> Unit
) {
    UiInterceptors.register(object :
        UiInterceptors.UiInterceptor<ExtractFixtureDialog>(ExtractFixtureDialog::class.java) {
        override fun doIntercept(component: ExtractFixtureDialog) {
            component.setFixtureName(fixtureName)
            component.close(DialogWrapper.OK_EXIT_CODE)
        }
    })
    try {
        action()
    } finally {
        UiInterceptors.clear()
    }
}
