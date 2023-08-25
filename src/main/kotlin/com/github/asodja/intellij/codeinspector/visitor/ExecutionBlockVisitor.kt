package com.github.asodja.intellij.codeinspector.visitor

import com.github.asodja.intellij.codeinspector.ConfigurationCacheUnsupportedTypes
import com.github.asodja.intellij.codeinspector.utils.isInstanceOf
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.getMethodName
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.structuralsearch.resolveDeclType
import org.jetbrains.kotlin.idea.structuralsearch.resolveReceiverType
import org.jetbrains.kotlin.lombok.utils.decapitalize
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes

/**
 * A visitor that visits the execution block: doLast and doFirst.
 */
class ExecutionBlockVisitor(private val context: ExecutionLikeContext) : KtTreeVisitorVoid() {

    override fun visitDeclaration(expression: KtDeclaration) {
        context.executionDeclarations.add(expression)
        super.visitDeclaration(expression)
    }


    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
        reportProblem(context, expression)
    }

    private
    fun reportProblem(context: ExecutionLikeContext, expression: KtReferenceExpression) {
        if (context.isAlreadyReported(expression)) {
            return
        }
        val problems = getConfigurationCacheProblem(context, expression)
        if (problems.isNotEmpty()) {
            problems.filter { it.originalExpression != expression }.forEach {
                context.registerProblem(it)
            }
            val problem = problems.firstOrNull { it.originalExpression == expression }
                ?: problems.firstOrNull { !it.isPotentialError }
                ?: problems.first()
            context.registerProblem(expression, problem)
        }
    }

    private
    fun getConfigurationCacheProblem(context: ExecutionLikeContext, expression: KtReferenceExpression): List<Problem> {
        val receiver = when (val result = expression.resolveReceiverType()) {
            null -> expression.resolveToCall(BodyResolveMode.PARTIAL)?.call?.getResolvedCall(expression.analyze(
                BodyResolveMode.PARTIAL))?.resultingDescriptor?.receiverType()
            else -> result
        }

        if (receiver?.fqName == FqName("Build_gradle")) {
            // Handle case when we have a reference to Build_gradle, e.g. variables defined on the top level
            return listOf(Problem(expression, "Cannot serialize Gradle script object references as these are not supported with the configuration cache."))
        }

        if (receiver.isInstanceOf(FqName("org.gradle.api.Task"))) {
            // Handle unsupported method invocations on a task
            val descriptor = expression.resolveToCall(BodyResolveMode.PARTIAL)?.call?.getResolvedCall(expression.analyze(
                BodyResolveMode.PARTIAL))?.resultingDescriptor
            if (descriptor?.valueParameters?.isEmpty() == true && descriptor.getMethodName() in ConfigurationCacheUnsupportedTypes.UNSUPPORTED_TASK_INVOCATIONS) {
                val methodName = descriptor.getMethodName().removePrefix("get").decapitalize()
                return listOf(Problem(expression, "Invocation of 'Task.$methodName' by task at execution time is unsupported.", isPotentialError = true))
            }
        }

        val reference = expression.mainReference.resolve()
        if (reference != null && reference.isDeclaredInExecutionBlock(context.executionDeclarations)) {
            // If the reference is declared in the execution block, then we detect problems via other mechanism
            return emptyList()
        } else if (reference is KtProperty && reference.isUnserializableLocalVariableDefinedOutsideBlock()) {
            // Case where we have a local variable defined outside of execution block, we can't serialize it
            return listOf(Problem(expression, "Cannot serialize object of type '${reference.resolveDeclType()?.fqName}', as these are not supported with the configuration cache."))
        } else if (reference?.isConfigurationDeclarationAndHasConfigurationCacheProblems(context) == true) {
            // Bubble up problems from methods declared in the configuration block and used at execution time
            return context.configurationDeclarations[reference]?.problems?.toList() ?: emptyList()
        }

        val type = expression.resolveType()
        if (type.isInstanceOf(FqName("org.gradle.api.Project")) || type.isInstanceOf(FqName("org.gradle.api.invocation.Gradle"))) {
            // Referencing or injecting Project or Gradle is never allowed, we can just report it no matter what
            // TODO: We could do a better job here, handle different cases, but this is good enough for now
            val isNullableType = type?.isNullable() == true
            return listOf(Problem(expression, "Accessing non-serializable type '${type?.fqName}' caused by invocation. These are not supported with the configuration cache.", isPotentialError = isNullableType))
        }
        return emptyList()
    }

    private
    fun PsiElement.isConfigurationDeclarationAndHasConfigurationCacheProblems(context: ExecutionLikeContext): Boolean =
        context.configurationDeclarations.contains(this) && context.configurationDeclarations[this]?.problems?.isNotEmpty() == true

    private
    fun PsiElement.isDeclaredInExecutionBlock(executionDeclarations: Set<KtDeclaration>): Boolean =
        executionDeclarations.contains(this)

    private
    fun KtProperty.isUnserializableLocalVariableDefinedOutsideBlock(): Boolean =
        this.isLocal && this.resolveDeclType()?.isNotConfigurationCacheSerializable() == true

    private
    fun KotlinType.isNotConfigurationCacheSerializable(): Boolean {
        return ConfigurationCacheUnsupportedTypes.UNSERIALIZABLE_TYPES.contains(this.fqName) || this.supertypes().any { ConfigurationCacheUnsupportedTypes.UNSERIALIZABLE_TYPES.contains(it.fqName) }
    }
}