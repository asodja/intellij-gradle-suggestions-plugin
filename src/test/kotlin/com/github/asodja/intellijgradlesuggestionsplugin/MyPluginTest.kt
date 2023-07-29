package com.github.asodja.intellijgradlesuggestionsplugin

import com.github.asodja.intellij.codeinspector.ConfigurationCacheInspector
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    private fun setup() {
        val file = myFixture.copyFileToProject("build.gradle.kts", "build.gradle.kts")
        myFixture.copyFileToProject("settings.gradle.kts", "settings.gradle.kts")
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.enableInspections(ConfigurationCacheInspector())
    }

    fun testProblemsAreHighlighted() {
        setup()
        val highlights = myFixture.doHighlighting()
        Assert.assertFalse(highlights.isEmpty())
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
