package com.github.asodja.intellij.codeinspector.visitor

import com.github.asodja.intellij.codeinspector.utils.isInstanceOf
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.debugger.sequence.psi.callName
import org.jetbrains.kotlin.idea.structuralsearch.resolveReceiverType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

class BuildGradleKtsVisitor(private val holder: ProblemsHolder,
                            private val context: ConfigurationContext,
                            private val alreadyReported: MutableSet<KtExpression> = mutableSetOf()
) : KtTreeVisitorVoid() {

    override fun visitDeclaration(expression: KtDeclaration) {
        val declarationData = context.configurationDeclarations.computeIfAbsent(expression) {
            KtDeclarationData(expression)
        }
        if (expression is KtNamedFunction) {
            ExecutionBlockVisitor(ExecutionPotentialContext(declarationData, context.configurationDeclarations)).visitDeclaration(expression)
        } else {
            super.visitDeclaration(expression)
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        if (expression.isExecutionBlock()) {
            val executionContext = ExecutionContext(holder, context.configurationDeclarations, alreadyReported)
            ExecutionBlockVisitor(executionContext).visitCallExpression(expression)
        } else {
            super.visitCallExpression(expression)
        }
    }

    @Suppress("RedundantOverride")
    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
    }

    private
    fun KtCallExpression.isExecutionBlock(): Boolean {
        val callName = this.callName()
        return (callName == "doLast" || callName == "doFirst")
                && this.calleeExpression?.resolveReceiverType()?.isInstanceOf(FqName("org.gradle.api.Task")) == true
    }
}