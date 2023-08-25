package com.github.asodja.intellij.codeinspector.visitor

import com.github.asodja.intellij.codeinspector.utils.isInstanceOf
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.debugger.sequence.psi.callName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.structuralsearch.resolveReceiverType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.toml.lang.psi.ext.elementType


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
        } else if (expression is KtProperty && expression.isLambdaLikeExpression()) {
            ExecutionBlockVisitor(ExecutionPotentialContext(declarationData, context.configurationDeclarations)).visitDeclaration(expression)
        } else {
            super.visitDeclaration(expression)
        }
    }

    private
    fun KtProperty.isLambdaLikeExpression(): Boolean {
        // E.g. val lambda = { <body> }
        val lambdaExpression = this.children.firstOrNull { it is KtLambdaExpression } as? KtLambdaExpression
        if (lambdaExpression != null) {
            return true
        }
        // E.g. val runner = Runnable { <body> }
        val callExpression = this.children.firstOrNull { it is KtCallExpression } as? KtCallExpression
        return (callExpression?.children?.get(0) as? KtNameReferenceExpression)?.mainReference?.resolve()?.elementType.toString() == "CLASS"
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