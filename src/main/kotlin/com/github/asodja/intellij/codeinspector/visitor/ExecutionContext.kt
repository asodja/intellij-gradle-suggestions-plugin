package com.github.asodja.intellij.codeinspector.visitor

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Represents the context and state in the execution blocks.
 */
data class ExecutionContext(
    val holder: ProblemsHolder,
    override val configurationDeclarations: Map<KtDeclaration, KtDeclarationData>,
    val alreadyReported: MutableSet<KtExpression>
): ExecutionLikeContext {
    override val executionDeclarations = mutableSetOf<KtDeclaration>()

    override fun isAlreadyReported(expression: KtExpression): Boolean {
        return alreadyReported.contains(expression)
    }

    override fun registerProblem(problem: Problem, problemHighlightType: ProblemHighlightType) {
        if (alreadyReported.add(problem.originalExpression)) {
            holder.registerProblem(problem.originalExpression, problem.messageIfPotential(problem.originalExpression), problemHighlightType)
        }
    }

    override fun registerProblem(expression: KtExpression, problem: Problem, problemHighlightType: ProblemHighlightType) {
        if (alreadyReported.add(expression)) {
            holder.registerProblem(expression, problem.messageIfPotential(expression), problemHighlightType)
        }
    }
}