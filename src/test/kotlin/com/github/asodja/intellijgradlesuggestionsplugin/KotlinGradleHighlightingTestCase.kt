package com.github.asodja.intellijgradlesuggestionsplugin

import com.android.tools.idea.sdk.Jdks
import com.github.asodja.intellij.codeinspector.ConfigurationCacheInspector
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.utils.vfs.createFile
import org.gradle.internal.jvm.Jvm
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

abstract class KotlinGradleHighlightingTestCase: ExternalSystemImportingTestCase() {

    private var myProjectSettings: GradleProjectSettings? = null
    private var jdk: Sdk? = null
    private var myCodeInsightFixture: CodeInsightTestFixture? = null
    val myInsightsFixture: CodeInsightTestFixture
        get() = myCodeInsightFixture!!

    override fun setUp() {
        myProjectSettings = GradleProjectSettings()
        super.setUp()
        jdk = Jdks.getInstance().createJdk(Jvm.current().javaHome.absolutePath)
        myProjectSettings!!.gradleJvm = ExternalSystemJdkUtil.USE_PROJECT_JDK
        WriteAction.runAndWait<RuntimeException> { ProjectRootManager.getInstance(myProject).projectSdk = jdk }
    }

    override fun tearDown() {
        WriteAction.runAndWait<RuntimeException> { 
            ProjectJdkTable.getInstance().removeJdk(jdk!!)
            ProjectRootManager.getInstance(myProject).projectSdk = null
        }
        super.tearDown()
    }

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name, useDirectoryBasedStorageFormat()).fixture
        myCodeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        myCodeInsightFixture!!.setUp()
    }

    override fun getTestsTempDir() = "src/test/testData/rename"

    override fun getExternalSystemConfigFileName(): String {
        return "build.gradle.kts"
    }

    override fun getCurrentExternalProjectSettings(): ExternalProjectSettings {
        return myProjectSettings!!
    }

    override fun getExternalSystemId(): ProjectSystemId {
        return GradleConstants.SYSTEM_ID;
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }

    fun writeToFile(fileName: String, content: () -> String): VirtualFile {
        var file: VirtualFile? = null
        WriteAction.runAndWait<Throwable> {
            file = myProjectRoot.createFile(fileName)
            file!!.writeText(content.invoke())
        }
        return file!!
    }

    fun VirtualFile.getHighlights(): List<HighlightInfo> {
        myInsightsFixture.configureFromExistingVirtualFile(this)
        myInsightsFixture.enableInspections(ConfigurationCacheInspector())
        return myInsightsFixture.doHighlighting()
    }
}