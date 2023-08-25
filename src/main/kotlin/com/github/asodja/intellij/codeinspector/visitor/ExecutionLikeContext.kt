package com.github.asodja.intellij.codeinspector.visitor

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression

interface ExecutionLikeContext {

    val configurationDeclarations: Map<KtDeclaration, KtDeclarationData>

    val executionDeclarations: MutableSet<KtDeclaration>

    fun isAlreadyReported(expression: KtExpression): Boolean

    fun registerProblem(problem: Problem, problemHighlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

    fun registerProblem(expression: KtExpression, problem: Problem, problemHighlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

}