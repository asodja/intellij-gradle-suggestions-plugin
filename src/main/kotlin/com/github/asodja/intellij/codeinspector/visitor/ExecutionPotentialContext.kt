package com.github.asodja.intellij.codeinspector.visitor

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression

class ExecutionPotentialContext(
    private val ktDeclarationData: KtDeclarationData,
    override val configurationDeclarations: Map<KtDeclaration, KtDeclarationData>,
    private val alreadyReported: MutableSet<KtExpression> = mutableSetOf()
) : ExecutionLikeContext {

        override val executionDeclarations: MutableSet<KtDeclaration> = mutableSetOf()

        override fun isAlreadyReported(expression: KtExpression): Boolean {
            return alreadyReported.contains(expression)
        }

        override fun registerProblem(problem: Problem, problemHighlightType: ProblemHighlightType) {
            if (alreadyReported.add(problem.originalExpression)) {
                ktDeclarationData.problems.add(problem)
            }
        }

        override fun registerProblem(expression: KtExpression, problem: Problem, problemHighlightType: ProblemHighlightType) {
            if (alreadyReported.add(expression)) {
                ktDeclarationData.problems.add(Problem(expression, problem.message, problem.suggestion, problem.isPotentialError))
            }
        }
}