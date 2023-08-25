package com.github.asodja.intellij.codeinspector

import com.github.asodja.intellij.codeinspector.visitor.BuildGradleKtsVisitor
import com.github.asodja.intellij.codeinspector.visitor.ConfigurationContext
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

@Suppress("InspectionDescriptionNotFoundInspection")
class ConfigurationCacheInspector : AbstractKotlinInspection() {

    override fun buildVisitor(
            holder: ProblemsHolder,
            isOnTheFly: Boolean
    ): KtTreeVisitorVoid {
        if (!holder.file.name.endsWith(".gradle.kts") || holder.file.name == "settings.gradle.kts" || holder.file.name == "init.gradle.kts") {
            return KtTreeVisitorVoid()
        }
        return BuildGradleKtsVisitor(holder, ConfigurationContext())
    }
}