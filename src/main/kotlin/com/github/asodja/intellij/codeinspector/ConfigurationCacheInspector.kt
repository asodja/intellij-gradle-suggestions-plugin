package com.github.asodja.intellij.codeinspector

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.structuralsearch.resolveReceiverType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

@Suppress("InspectionDescriptionNotFoundInspection")
class ConfigurationCacheInspector : AbstractKotlinInspection() {

    override fun buildVisitor(
            holder: ProblemsHolder,
            isOnTheFly: Boolean
    ): KtVisitorVoid {
        if (!holder.file.name.endsWith(".gradle.kts")) {
            return KtVisitorVoid()
        }

        val alreadyReported = mutableSetOf<KtExpression>()
        return this.callExpressionRecursiveVisitor { expression: KtReferenceExpression ->
            if (alreadyReported.contains(expression)) {
                return@callExpressionRecursiveVisitor
            }
            val problem = getConfigurationCacheProblem(expression)
            if (problem != null && alreadyReported.add(expression)) {
                holder.registerProblem(expression, problem, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
        }
    }

    private
    fun getConfigurationCacheProblem(expression: KtReferenceExpression): String? {
        val receiver = when (val result = expression.resolveReceiverType()?.fqName) {
            null -> expression.resolveToCall(BodyResolveMode.PARTIAL)?.call?.getResolvedCall(expression.analyze(BodyResolveMode.PARTIAL))?.resultingDescriptor?.receiverType()?.fqName
            else -> result
        }
        val type = expression.resolveType()?.fqName
        return if (receiver == FqName("Build_gradle")) {
            "Reference to Build_gradle, not compatible with cc"
        } else if (receiver == FqName("org.gradle.kotlin.dsl.support.delegates.ProjectDelegate")) {
            "Reference to ProjectDelegate, not compatible with cc"
        } else if (type == FqName("org.gradle.api.Project")) {
            "Type 'org.gradle.api.Project' is a not serializable with cc"
        } else if (type == FqName("org.gradle.api.DefaultTask")) {
            "Type 'org.gradle.api.DefaultTask' is a not serializable with cc"
        } else if (type == FqName("org.gradle.api.artifacts.Configuration")) {
            "Type 'org.gradle.api.artifacts.Configuration' is a not serializable with cc"
        } else {
            null
        }
    }

    private
    fun callExpressionRecursiveVisitor(block: (KtReferenceExpression) -> Unit) =
            object : KtTreeVisitorVoid() {
                var isIn = false
                var expression: KtCallExpression? = null

                override fun visitCallExpression(expression: KtCallExpression) {
                    if (expression.calleeExpression?.text == "doLast" || expression.calleeExpression?.text == "doFirst") {
                        this.expression = expression
                        isIn = true
                    }
                    super.visitCallExpression(expression)
                    if (this.expression == expression) {
                        isIn = false
                        this.expression = null
                    }
                }

                override fun visitReferenceExpression(expression: KtReferenceExpression) {
                    super.visitReferenceExpression(expression)
                    if (isIn) {
                        block(expression)
                    }
                }
            }
}