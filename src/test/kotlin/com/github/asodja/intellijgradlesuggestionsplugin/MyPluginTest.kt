package com.github.asodja.intellijgradlesuggestionsplugin

import com.github.asodja.intellij.codeinspector.ConfigurationCacheInspector
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    private fun setup() {
        myFixture.configureByFile("TestFile.kt")
        myFixture.enableInspections(ConfigurationCacheInspector())
    }

    fun testProblemsAreHighlighted() {
        setup()
        val highlights = myFixture.doHighlighting()
        Assert.assertFalse(highlights.isEmpty())
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
