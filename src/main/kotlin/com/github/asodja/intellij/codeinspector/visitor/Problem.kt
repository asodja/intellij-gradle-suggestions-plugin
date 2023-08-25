package com.github.asodja.intellij.codeinspector.visitor

import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression


data class Problem(
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

data class KtDeclarationData(val declaration: KtDeclaration, val problems: MutableSet<Problem> = mutableSetOf())
