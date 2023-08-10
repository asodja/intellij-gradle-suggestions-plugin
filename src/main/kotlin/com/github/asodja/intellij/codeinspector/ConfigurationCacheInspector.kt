package com.github.asodja.intellij.codeinspector

import com.github.asodja.intellij.codeinspector.ConfigurationCacheUnsupportedTypes.UNSERIALIZABLE_TYPES
import com.github.asodja.intellij.codeinspector.ConfigurationCacheUnsupportedTypes.UNSUPPORTED_TASK_INVOCATIONS
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.debugger.sequence.psi.callName
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

@Suppress("InspectionDescriptionNotFoundInspection")
class ConfigurationCacheInspector : AbstractKotlinInspection() {

    override fun buildVisitor(
            holder: ProblemsHolder,
            isOnTheFly: Boolean
    ): KtTreeVisitorVoid {
        if (!holder.file.name.endsWith(".gradle.kts")) {
            return KtTreeVisitorVoid()
        }

        val context = Context()
        return this.callExpressionRecursiveVisitor(context) { expression: KtReferenceExpression ->
            if (context.alreadyReported.contains(expression)) {
                return@callExpressionRecursiveVisitor
            }
            val problems = getConfigurationCacheProblem(context, expression)
            if (context.isInDeclaration && !context.isInExecutionBlock) {
                context.configurationDeclarationData[context.declaration]?.problems?.addAll(problems)
            } else if (context.isInExecutionBlock && problems.isNotEmpty() && context.alreadyReported.add(expression)) {
                problems.filter { it.originalExpression != expression }.forEach {
                    if (context.alreadyReported.add(it.originalExpression)) {
                        holder.registerProblem(it.originalExpression, it.messageIfPotential(it.originalExpression), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    }
                }
                val problem = problems.firstOrNull { it.originalExpression == expression }
                        ?: problems.firstOrNull { !it.isPotentialError }
                        ?: problems.first()
                holder.registerProblem(expression, problem.messageIfPotential(expression), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
        }
    }

    private
    fun getConfigurationCacheProblem(context: Context, expression: KtReferenceExpression): List<Problem> {
        val receiver = when (val result = expression.resolveReceiverType()) {
            null -> expression.resolveToCall(BodyResolveMode.PARTIAL)?.call?.getResolvedCall(expression.analyze(BodyResolveMode.PARTIAL))?.resultingDescriptor?.receiverType()
            else -> result
        }

        if (context.isInVariableDeclaration
                && !context.isInExecutionBlock
                && !expression.isDeclaredInCallableBlockInVariableDeclaration(context)
                && receiver?.fqName == FqName("Build_gradle")) {
            val reference = expression.mainReference.resolve()
            if (context.configurationDeclarations.contains(reference)) {
                // TODO: Figure out a better way to handle this. This doesn't work correctly all the time, e.g.:
                //  it shows errors on reference, although potential problem is on a callable
                // Handle case when we have a reference to Build_gradle and we deference it, e.g.
                // val otherVar = <declaration> // otherVar has a reference to Build_gradle
                // tasks.register("myTask") {
                //      val myVar = otherVar // myVar doesn't have it anymore
                // }
                return context.configurationDeclarationData[reference]?.problems?.toList() ?: emptyList()
            }
            return emptyList()
        }

        if (receiver?.fqName == FqName("Build_gradle")) {
            // Handle case when we have a reference to Build_gradle, e.g. variables defined on the top level
            return listOf(Problem(expression, "Cannot serialize Gradle script object references as these are not supported with the configuration cache."))
        }

        if (receiver.isInstanceOf(FqName("org.gradle.api.Task"))) {
            // Handle unsupported method invocations on a task
            val descriptor = expression.resolveToCall(BodyResolveMode.PARTIAL)?.call?.getResolvedCall(expression.analyze(BodyResolveMode.PARTIAL))?.resultingDescriptor
            if (descriptor?.valueParameters?.isEmpty() == true && descriptor.getMethodName() in UNSUPPORTED_TASK_INVOCATIONS) {
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
            return context.configurationDeclarationData[reference]?.problems?.toList() ?: emptyList()
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
    fun PsiElement.isDeclaredInCallableBlockInVariableDeclaration(context: Context): Boolean {
        val variable = context.variableDeclaration
        var parent = this.parent
        while (parent != variable) {
            if (parent is KtLambdaExpression || parent is KtClassBody || parent is KtClassLikeDeclaration || parent is KtFunction) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private
    fun PsiElement.isConfigurationDeclarationAndHasConfigurationCacheProblems(context: Context): Boolean =
            context.configurationDeclarations.contains(this) && context.configurationDeclarationData[this]?.problems?.isNotEmpty() == true

    private
    fun PsiElement.isDeclaredInExecutionBlock(declarations: MutableSet<KtDeclaration>): Boolean =
            declarations.contains(this)

    private
    fun KtProperty.isUnserializableLocalVariableDefinedOutsideBlock(): Boolean =
            this.isLocal && this.resolveDeclType()?.isNotConfigurationCacheSerializable() == true

    private
    fun KotlinType.isNotConfigurationCacheSerializable(): Boolean {
        return UNSERIALIZABLE_TYPES.contains(this.fqName) || this.supertypes().any { UNSERIALIZABLE_TYPES.contains(it.fqName) }
    }

    private
    fun KotlinType?.isInstanceOf(fqName: FqName): Boolean {
        if (this == null) return false
        return this.fqName == fqName || this.supertypes().any { it.fqName == fqName }
    }

    private
    fun callExpressionRecursiveVisitor(context: Context, block: (KtReferenceExpression) -> Unit) =
            object : KtTreeVisitorVoid() {

                override fun visitDeclaration(expression: KtDeclaration) {
                    val previousDeclaration = context.declaration
                    context.declaration = expression
                    val previousVariableDeclaration = context.variableDeclaration
                    if (expression is KtVariableDeclaration) {
                        context.variableDeclaration = expression
                    }
                    if (context.isInExecutionBlock) {
                        context.executionDeclarations.add(expression)
                    } else {
                        // If declaration is in the top level, then we will anyway report reference to "Build_gradle" as an error
                        // so inspect only methods that are not on the top level
                        // TODO if it's method visit the method body and inspect if any problems are there,
                        //  then check in doLast/doFirst if this method is called at a execution time
                        context.configurationDeclarations.add(expression)
                        context.configurationDeclarationData.computeIfAbsent(expression) {
                            DeclarationData(expression)
                        }
                    }
                    super.visitDeclaration(expression)
                    context.declaration = previousDeclaration
                    context.variableDeclaration = previousVariableDeclaration
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    val isExecutionBlock = isExecutionBlock(expression)
                    if (isExecutionBlock) {
                        context.isInExecutionBlock = true
                    }
                    super.visitCallExpression(expression)
                    if (isExecutionBlock) {
                        context.isInExecutionBlock = false
                        context.executionDeclarations.clear()
                    }
                }

                override fun visitReferenceExpression(expression: KtReferenceExpression) {
                    super.visitReferenceExpression(expression)
                    if (context.isInExecutionBlock || context.isInDeclaration) {
                        block(expression)
                    }
                }

                private
                fun isExecutionBlock(expression: KtCallExpression): Boolean {
                    if (context.isInExecutionBlock) return false
                    val callName = expression.callName()
                    return (callName == "doLast" || callName == "doFirst")
                            && expression.calleeExpression?.resolveReceiverType()?.isInstanceOf(FqName("org.gradle.api.Task")) == true
                }
            }

    private class Context {
        var isInExecutionBlock = false
        val isInDeclaration
            get() = declaration != null
        var declaration: KtDeclaration? = null
        val isInVariableDeclaration
            get() = variableDeclaration != null
        var variableDeclaration: KtVariableDeclaration? = null
        val alreadyReported = mutableSetOf<KtExpression>()
        val executionDeclarations = mutableSetOf<KtDeclaration>()
        val configurationDeclarations = mutableSetOf<KtDeclaration>()
        val configurationDeclarationData = mutableMapOf<KtDeclaration, DeclarationData>()
    }

    private data class DeclarationData(val declaration: KtDeclaration, val problems: MutableSet<Problem> = mutableSetOf())

    private data class Problem(
            val originalExpression: KtExpression,
            val message: String,
            val suggestion: String? = null,
            // Potential means, that we detected a potential problem, that is triggered only when some code path is executed
            val isPotentialError: Boolean = false
    ) {

        fun messageIfPotential(originalExpression: KtExpression): String {
            val addLineNumber = originalExpression != this.originalExpression
            val message = when (addLineNumber) {
                true -> "$message At line: ${this.originalExpression.getLineNumber(start = true) + 1}"
                else -> message
            }
            return when (isPotentialError) {
                true -> "$message\n\nThis is just potentially a configuration cache error.\n\nWhy is it just potentially a problem? Because it might be triggered only when some code path is executed, but with code analysis we are not sure if it will. It's still better to fix the issue."
                else -> message
            }
        }
    }
}