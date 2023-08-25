package com.github.asodja.intellij.codeinspector.utils

import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes


fun KotlinType?.isInstanceOf(fqName: FqName): Boolean {
    if (this == null) return false
    return this.fqName == fqName || this.supertypes().any { it.fqName == fqName }
}