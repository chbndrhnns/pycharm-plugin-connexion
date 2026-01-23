package com.github.chbndrhnns.betterpy.features.pytest.testtree

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

data class PytestTestRecord(
    val nodeid: String,
    val psiElement: PsiElement?,
    val file: VirtualFile?,
    val locationUrl: String,
    val metainfo: String?
)
