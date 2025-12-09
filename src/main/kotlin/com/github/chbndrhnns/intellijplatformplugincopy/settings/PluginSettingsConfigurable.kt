package com.github.chbndrhnns.intellijplatformplugincopy.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsConfigurable : SearchableConfigurable {
    private var panel: JPanel? = null
    private lateinit var enableWrapCb: JBCheckBox
    private lateinit var enableWrapItemsCb: JBCheckBox
    private lateinit var enableUnwrapCb: JBCheckBox
    private lateinit var enableUnwrapItemsCb: JBCheckBox
    private lateinit var enableIntroduceCustomTypeCb: JBCheckBox
    private lateinit var enablePopulateArgumentsCb: JBCheckBox
    private lateinit var enableDunderAllInspectionCb: JBCheckBox
    private lateinit var enableCopyPackageContentCb: JBCheckBox
    private lateinit var enableRestoreSourceRootPrefixCb: JBCheckBox
    private lateinit var enableRelativeImportPreferenceCb: JBCheckBox
    private lateinit var enableDictAccessCb: JBCheckBox
    private lateinit var enableMakePublicCb: JBCheckBox
    private lateinit var enableIntroduceParameterObjectCb: JBCheckBox
    private lateinit var enableDataclassMissingInspectionCb: JBCheckBox
    private lateinit var enablePrivateModuleImportInspectionCb: JBCheckBox
    private lateinit var enableCopyBuildNumberCb: JBCheckBox
    private lateinit var enableCreateLocalVariableIntentionCb: JBCheckBox

    override fun getId(): String = "com.github.chbndrhnns.intellijplatformplugincopy.settings"
    override fun getDisplayName(): String = "DDD Toolkit"

    override fun createComponent(): JComponent {
        val p = JPanel(BorderLayout())
        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        enableWrapCb = JBCheckBox("Enable ‘Wrap with expected type’ intention")
        enableWrapItemsCb = JBCheckBox("Enable ‘Wrap items with expected type’ intention")
        enableUnwrapCb = JBCheckBox("Enable ‘Unwrap to expected type’ intention")
        enableUnwrapItemsCb = JBCheckBox("Enable ‘Unwrap items to expected type’ intention")
        enableIntroduceCustomTypeCb = JBCheckBox("Enable ‘Introduce custom type from stdlib’ intention")
        enablePopulateArgumentsCb = JBCheckBox("Enable ‘Populate arguments’ intention")
        enableDunderAllInspectionCb = JBCheckBox("Enable ‘__all__’ export inspection")
        enableCopyPackageContentCb = JBCheckBox("Enable ‘Copy Package Content’ context menu action")
        enableRestoreSourceRootPrefixCb = JBCheckBox("Enable ‘Restore Source Root Prefix’ in imports")
        enableRelativeImportPreferenceCb = JBCheckBox("Enable ‘Prefer relative imports’ in auto-import")
        enableDictAccessCb = JBCheckBox("Enable ‘Dictionary access’ intentions")
        enableMakePublicCb = JBCheckBox("Enable ‘Make public/private’ intentions")
        enableIntroduceParameterObjectCb = JBCheckBox("Enable ‘Introduce parameter object’ intention")
        enableDataclassMissingInspectionCb = JBCheckBox("Enable ‘Dataclass missing’ inspection")
        enablePrivateModuleImportInspectionCb = JBCheckBox("Enable ‘Private module import’ inspection")
        enableCopyBuildNumberCb = JBCheckBox("Enable ‘Copy Build Number’ context menu action")
        enableCreateLocalVariableIntentionCb = JBCheckBox("Enable ‘Create local variable’ intention")
        inner.add(enableWrapCb)
        inner.add(enableWrapItemsCb)
        inner.add(enableUnwrapCb)
        inner.add(enableUnwrapItemsCb)
        inner.add(enableIntroduceCustomTypeCb)
        inner.add(enablePopulateArgumentsCb)
        inner.add(enableDunderAllInspectionCb)
        inner.add(enableCopyPackageContentCb)
        inner.add(enableRestoreSourceRootPrefixCb)
        inner.add(enableRelativeImportPreferenceCb)
        inner.add(enableDictAccessCb)
        inner.add(enableMakePublicCb)
        inner.add(enableIntroduceParameterObjectCb)
        inner.add(enableDataclassMissingInspectionCb)
        inner.add(enablePrivateModuleImportInspectionCb)
        inner.add(enableCopyBuildNumberCb)
        inner.add(enableCreateLocalVariableIntentionCb)
        p.add(inner, BorderLayout.NORTH)
        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val state = PluginSettingsState.instance().state
        return (::enableWrapCb.isInitialized && enableWrapCb.isSelected != state.enableWrapWithExpectedTypeIntention) ||
                (::enableWrapItemsCb.isInitialized && enableWrapItemsCb.isSelected != state.enableWrapItemsWithExpectedTypeIntention) ||
                (::enableUnwrapCb.isInitialized && enableUnwrapCb.isSelected != state.enableUnwrapToExpectedTypeIntention) ||
                (::enableUnwrapItemsCb.isInitialized && enableUnwrapItemsCb.isSelected != state.enableUnwrapItemsToExpectedTypeIntention) ||
                (::enableIntroduceCustomTypeCb.isInitialized && enableIntroduceCustomTypeCb.isSelected != state.enableIntroduceCustomTypeFromStdlibIntention) ||
                (::enablePopulateArgumentsCb.isInitialized && enablePopulateArgumentsCb.isSelected != state.enablePopulateArgumentsIntention) ||
                (::enableDunderAllInspectionCb.isInitialized && enableDunderAllInspectionCb.isSelected != state.enablePyMissingInDunderAllInspection) ||
                (::enableCopyPackageContentCb.isInitialized && enableCopyPackageContentCb.isSelected != state.enableCopyPackageContentAction) ||
                (::enableRestoreSourceRootPrefixCb.isInitialized && enableRestoreSourceRootPrefixCb.isSelected != state.enableRestoreSourceRootPrefix) ||
                (::enableRelativeImportPreferenceCb.isInitialized && enableRelativeImportPreferenceCb.isSelected != state.enableRelativeImportPreference) ||
                (::enableDictAccessCb.isInitialized && enableDictAccessCb.isSelected != state.enableDictAccessIntention) ||
                (::enableMakePublicCb.isInitialized && enableMakePublicCb.isSelected != state.enableMakePublicIntention) ||
                (::enableIntroduceParameterObjectCb.isInitialized && enableIntroduceParameterObjectCb.isSelected != state.enableIntroduceParameterObjectIntention) ||
                (::enableDataclassMissingInspectionCb.isInitialized && enableDataclassMissingInspectionCb.isSelected != state.enableDataclassMissingInspection) ||
                (::enablePrivateModuleImportInspectionCb.isInitialized && enablePrivateModuleImportInspectionCb.isSelected != state.enablePrivateModuleImportInspection) ||
                (::enableCopyBuildNumberCb.isInitialized && enableCopyBuildNumberCb.isSelected != state.enableCopyBuildNumberAction) ||
                (::enableCreateLocalVariableIntentionCb.isInitialized && enableCreateLocalVariableIntentionCb.isSelected != state.enableCreateLocalVariableIntention)
    }

    override fun apply() {
        val svc = PluginSettingsState.instance()
        val s = svc.state.copy(
            enableWrapWithExpectedTypeIntention = enableWrapCb.isSelected,
            enableWrapItemsWithExpectedTypeIntention = enableWrapItemsCb.isSelected,
            enableUnwrapToExpectedTypeIntention = enableUnwrapCb.isSelected,
            enableUnwrapItemsToExpectedTypeIntention = enableUnwrapItemsCb.isSelected,
            enableIntroduceCustomTypeFromStdlibIntention = enableIntroduceCustomTypeCb.isSelected,
            enablePopulateArgumentsIntention = enablePopulateArgumentsCb.isSelected,
            enablePyMissingInDunderAllInspection = enableDunderAllInspectionCb.isSelected,
            enableCopyPackageContentAction = enableCopyPackageContentCb.isSelected,
            enableRestoreSourceRootPrefix = enableRestoreSourceRootPrefixCb.isSelected,
            enableRelativeImportPreference = enableRelativeImportPreferenceCb.isSelected,
            enableDictAccessIntention = enableDictAccessCb.isSelected,
            enableMakePublicIntention = enableMakePublicCb.isSelected,
            enableIntroduceParameterObjectIntention = enableIntroduceParameterObjectCb.isSelected,
            enableDataclassMissingInspection = enableDataclassMissingInspectionCb.isSelected,
            enablePrivateModuleImportInspection = enablePrivateModuleImportInspectionCb.isSelected,
            enableCopyBuildNumberAction = enableCopyBuildNumberCb.isSelected,
            enableCreateLocalVariableIntention = enableCreateLocalVariableIntentionCb.isSelected,
        )
        svc.loadState(s)
    }

    override fun reset() {
        val st = PluginSettingsState.instance().state
        if (::enableWrapCb.isInitialized) enableWrapCb.isSelected = st.enableWrapWithExpectedTypeIntention
        if (::enableWrapItemsCb.isInitialized) enableWrapItemsCb.isSelected =
            st.enableWrapItemsWithExpectedTypeIntention
        if (::enableUnwrapCb.isInitialized) enableUnwrapCb.isSelected = st.enableUnwrapToExpectedTypeIntention
        if (::enableUnwrapItemsCb.isInitialized) enableUnwrapItemsCb.isSelected =
            st.enableUnwrapItemsToExpectedTypeIntention
        if (::enableIntroduceCustomTypeCb.isInitialized) enableIntroduceCustomTypeCb.isSelected =
            st.enableIntroduceCustomTypeFromStdlibIntention
        if (::enablePopulateArgumentsCb.isInitialized) enablePopulateArgumentsCb.isSelected =
            st.enablePopulateArgumentsIntention
        if (::enableDunderAllInspectionCb.isInitialized) enableDunderAllInspectionCb.isSelected =
            st.enablePyMissingInDunderAllInspection
        if (::enableCopyPackageContentCb.isInitialized) enableCopyPackageContentCb.isSelected =
            st.enableCopyPackageContentAction
        if (::enableRestoreSourceRootPrefixCb.isInitialized) enableRestoreSourceRootPrefixCb.isSelected =
            st.enableRestoreSourceRootPrefix
        if (::enableRelativeImportPreferenceCb.isInitialized) enableRelativeImportPreferenceCb.isSelected =
            st.enableRelativeImportPreference
        if (::enableDictAccessCb.isInitialized) enableDictAccessCb.isSelected = st.enableDictAccessIntention
        if (::enableMakePublicCb.isInitialized) enableMakePublicCb.isSelected = st.enableMakePublicIntention
        if (::enableIntroduceParameterObjectCb.isInitialized) enableIntroduceParameterObjectCb.isSelected =
            st.enableIntroduceParameterObjectIntention
        if (::enableDataclassMissingInspectionCb.isInitialized) enableDataclassMissingInspectionCb.isSelected =
            st.enableDataclassMissingInspection
        if (::enablePrivateModuleImportInspectionCb.isInitialized) enablePrivateModuleImportInspectionCb.isSelected =
            st.enablePrivateModuleImportInspection
        if (::enableCopyBuildNumberCb.isInitialized) enableCopyBuildNumberCb.isSelected = st.enableCopyBuildNumberAction
        if (::enableCreateLocalVariableIntentionCb.isInitialized) enableCreateLocalVariableIntentionCb.isSelected = st.enableCreateLocalVariableIntention
    }

    override fun disposeUIResources() {
        panel = null
    }
}
