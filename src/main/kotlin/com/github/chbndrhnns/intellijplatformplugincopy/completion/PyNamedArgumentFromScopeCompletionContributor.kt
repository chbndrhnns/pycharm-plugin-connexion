package com.github.chbndrhnns.intellijplatformplugincopy.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.TypeEvalContext

class PyNamedArgumentFromScopeCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            psiElement().withLanguage(PythonLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val position = parameters.position
                    val file = position.containingFile as? PyFile ?: return

                    val call = PsiTreeUtil.getParentOfType(position, PyCallExpression::class.java) ?: return
                    val argumentList = call.argumentList ?: return

                    val offset = parameters.offset
                    if (!argumentList.textRange.contains(offset)) return

                    if (isInsideKeywordValue(position, offset)) return

                    val typeEvalContext = TypeEvalContext.codeAnalysis(position.project, file)
                    val candidateNames = getCandidateKeywordParameterNames(call, typeEvalContext)
                    if (candidateNames.isEmpty()) return

                    val alreadyPassedKeywords = argumentList.arguments
                        .asSequence()
                        .filterIsInstance<PyKeywordArgument>()
                        .mapNotNull { it.keyword }
                        .toSet()

                    for (name in candidateNames) {
                        if (alreadyPassedKeywords.contains(name)) continue
                        if (!isNameVisibleAtCallSite(name, call, file, typeEvalContext)) continue

                        val text = "$name=$name"
                        val element = LookupElementBuilder.create(text)
                            .withTypeText("From scope")
                            .withInsertHandler { insertionContext, _ ->
                                // Replace whatever prefix was used for completion with the full kw-arg text.
                                val doc = insertionContext.document
                                doc.replaceString(insertionContext.startOffset, insertionContext.tailOffset, text)
                                insertionContext.editor.caretModel.moveToOffset(insertionContext.startOffset + text.length)
                            }

                        result.addElement(PrioritizedLookupElement.withPriority(element, 200.0))
                    }
                }
            }
        )
    }

    private fun getCandidateKeywordParameterNames(call: PyCallExpression, context: TypeEvalContext): List<String> {
        // We intentionally do NOT use "missing parameters" here.
        // When the user types an unresolved reference like `f(fo<caret>)`, PSI treats it as a positional arg,
        // and argument mapping may consider that parameter "already provided". UX-wise we still want to
        // propose `foo=foo` to turn it into a keyword argument.
        val resolveContext = PyResolveContext.defaultContext(context)
        val mappings = call.multiMapArguments(resolveContext)
        if (mappings.isEmpty()) return emptyList()

        val mapping = mappings.first()
        val callableType: PyCallableType = mapping.callableType ?: return emptyList()

        val params = callableType.getParameters(context) ?: return emptyList()
        return params
            .asSequence()
            .filter { !it.isSelf }
            .filter { !it.isPositionalContainer && !it.isKeywordContainer }
            .mapNotNull { it.name }
            .filterNot { it.startsWith("_") }
            .toList()
    }

    private fun isInsideKeywordValue(position: com.intellij.psi.PsiElement, offset: Int): Boolean {
        val kwArg = PsiTreeUtil.getParentOfType(position, PyKeywordArgument::class.java) ?: return false

        val valueExpression = kwArg.valueExpression
        if (valueExpression != null && valueExpression.textRange.contains(offset)) return true

        // For incomplete values like `foo=<caret>` the valueExpression may be null.
        // Treat any caret position after '=' within the keyword argument as "inside value".
        val eqIndex = kwArg.text.indexOf('=')
        if (eqIndex >= 0) {
            val eqOffset = kwArg.textRange.startOffset + eqIndex
            if (offset > eqOffset) return true
        }

        return false
    }

    private fun isNameVisibleAtCallSite(
        name: String,
        call: PyCallExpression,
        file: PyFile,
        context: TypeEvalContext
    ): Boolean {
        val owner = ScopeUtil.getScopeOwner(call) ?: file
        val qName = QualifiedName.fromDottedString(name)
        val resolved = PyResolveUtil.resolveQualifiedNameInScope(qName, owner, context)
        if (resolved.isNotEmpty()) return true

        val scope = ControlFlowCache.getScope(owner)
        return scope.containsDeclaration(name)
    }
}
