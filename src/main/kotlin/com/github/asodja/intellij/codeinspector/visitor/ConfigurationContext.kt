package com.github.asodja.intellij.codeinspector.visitor

import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * Represents the context and state of the configuration blocks.
 */
data class ConfigurationContext(val configurationDeclarations: MutableMap<KtDeclaration, KtDeclarationData> = mutableMapOf())