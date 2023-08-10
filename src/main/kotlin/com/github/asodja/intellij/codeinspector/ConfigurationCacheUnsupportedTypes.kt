package com.github.asodja.intellij.codeinspector

import org.jetbrains.kotlin.name.FqName

object ConfigurationCacheUnsupportedTypes {

    val UNSUPPORTED_TASK_INVOCATIONS = setOf(
            "project",
            "getProject",
            "extensions",
            "getExtensions",
            "taskDependencies",
            "getTaskDependencies"
    )

    val UNSERIALIZABLE_TYPES = setOf(
            // Not yet implemented
            FqName("java.io.Externalizable"),

            // Live JVM state
            FqName("java.lang.ClassLoader"),
            FqName("java.lang.Thread"),
            FqName("java.util.concurrent.ThreadFactory"),
            FqName("java.util.concurrent.Executor"),
            FqName("java.io.InputStream"),
            FqName("java.io.OutputStream"),
            FqName("java.io.FileDescriptor"),
            FqName("java.io.RandomAccessFile"),
            FqName("java.net.Socket"),
            FqName("java.net.ServerSocket"),

            // Gradle Scripts
            FqName("org.gradle.internal.scripts.GradleScript"),

            // Gradle Build Model
            FqName("org.gradle.api.invocation.Gradle"),
            FqName("org.gradle.api.initialization.Settings"),
            FqName("org.gradle.api.Project"),
            FqName("org.gradle.api.tasks.TaskContainer"),
            FqName("org.gradle.api.tasks.TaskDependency"),
            FqName("org.gradle.api.tasks.SourceSetContainer"),
            FqName("org.gradle.api.tasks.SourceSet"),

            // Dependency Resolution Types
            FqName("org.gradle.api.artifacts.Configuration"),
            FqName("org.gradle.api.artifacts.ConfigurationContainer"),
            FqName("org.gradle.api.artifacts.ResolutionStrategy"),
            FqName("org.gradle.api.artifacts.ResolvedConfiguration"),
            FqName("org.gradle.api.artifacts.LenientConfiguration"),
            FqName("org.gradle.api.artifacts.ResolvableDependencies"),
            FqName("org.gradle.api.artifacts.result.ResolutionResult"),
            FqName("org.gradle.api.artifacts.DependencyConstraintSet"),
            FqName("org.gradle.api.artifacts.dsl.RepositoryHandler"),
            FqName("org.gradle.api.artifacts.repositories.ArtifactRepository"),
            FqName("org.gradle.api.artifacts.dsl.DependencyHandler"),
            FqName("org.gradle.api.artifacts.dsl.DependencyConstraintHandler"),
            FqName("org.gradle.api.artifacts.dsl.ComponentMetadataHandler"),
            FqName("org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler"),
            FqName("org.gradle.api.artifacts.type.ArtifactTypeContainer"),
            FqName("org.gradle.api.attributes.AttributesSchema"),
            FqName("org.gradle.api.attributes.AttributeMatchingStrategy"),
            FqName("org.gradle.api.attributes.CompatibilityRuleChain"),
            FqName("org.gradle.api.attributes.DisambiguationRuleChain"),
            FqName("org.gradle.api.artifacts.query.ArtifactResolutionQuery"),
            FqName("org.gradle.api.artifacts.DependencySet"),
            FqName("org.gradle.api.artifacts.Dependency"),
            FqName("org.gradle.api.artifacts.dsl.DependencyLockingHandler"),
            FqName("org.gradle.api.artifacts.ResolvedDependency"),
            FqName("org.gradle.api.artifacts.ResolvedArtifact"),
            FqName("org.gradle.api.artifacts.ArtifactView"),
            FqName("org.gradle.api.artifacts.result.ArtifactResolutionResult"),
            FqName("org.gradle.api.artifacts.result.ComponentArtifactsResult"),
            FqName("org.gradle.api.artifacts.result.UnresolvedComponentResult"),
            FqName("org.gradle.api.artifacts.result.ArtifactResult"),

            // Publishing types
            FqName("org.gradle.api.publish.Publication"),

            // Direct build service references
            // Build services must always be referenced via their providers.
            FqName("org.gradle.api.services.BuildService"),

            // Gradle implementation types
            FqName("org.gradle.internal.service.DefaultServiceRegistry"),
    )

}