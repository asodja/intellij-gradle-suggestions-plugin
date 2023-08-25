package com.github.asodja.intellijgradlesuggestionsplugin

import com.intellij.lang.annotation.HighlightSeverity
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.junit.Test

class ConfigurationCacheInspectorTest : KotlinGradleHighlightingTestCase() {

    override fun setUp() {
        super.setUp()
        writeToFile("settings.gradle.kts") {
            """
            rootProject.name = "intellij-plugin-test"
            """.trimIndent()
        }
    }

    @Test
    fun `test warnings for forbidden task#method invocations`() {
        val file = writeToFile("build.gradle.kts") {
            """
            tasks.register("myTask") {
                doLast {
                    println(project)
                    println(extensions)
                    println(taskDependencies)
                }
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING }
        assertThat(warnings).hasSize(3)
        assertThat(warnings[0].text).isEqualTo("project")
        assertThat(warnings[0].description).startsWith("Invocation of 'Task.project' by task at execution time is unsupported.")

        assertThat(warnings[1].text).isEqualTo("extensions")
        assertThat(warnings[1].description).startsWith("Invocation of 'Task.extensions' by task at execution time is unsupported.")

        assertThat(warnings[2].text).isEqualTo("taskDependencies")
        assertThat(warnings[2].description).startsWith("Invocation of 'Task.taskDependencies' by task at execution time is unsupported.")
    }

    @Test
    fun `test warnings for Build#gradle reference`() {
        val file = writeToFile("build.gradle.kts") {
            """
            val myVar = "myVar"
            fun myMethod() {}
            tasks.register("myTask") {
                doLast {
                    print(layout)
                    println(myVar)
                    println(myMethod())
                }
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING }
        assert(warnings.size == 4)
        warnings[0].apply {
            assert(text == "layout")
            assert(description.startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache."))
        }
        warnings[1].apply {
            assert(text == "myVar")
            assert(description.startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache."))
        }
        warnings[2].apply {
            assert(text == "myMethod")
            assert(description.startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache."))
        }
        warnings[3].apply {
            // TODO we probably should not highlight call expression
            assert(text == "myMethod()")
            assert(description.startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache."))
        }
    }

    @Test
    fun `test warnings are not shown for Build#gradle reference when used via local variables`() {
        val file = writeToFile("build.gradle.kts") {
            """
            val myVar = "myVar"
            fun myMethod() {}
            tasks.register("myTask") {
                val myVar = myVar
                val layout = layout
                val myMethodValue = myMethod()
                doLast {
                    print(layout)
                    println(myVar)
                    println(myMethodValue)
                }
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING }
        assert(warnings.isEmpty())
    }

    @Test
    fun `test warnings for non-serializable types`() {
        val file = writeToFile("build.gradle.kts") {
            """
            tasks.register("myTask") {
                val thread = Thread()
                val config = configurations.create("myConf")
                val fileCollection: FileCollection = configurations.create("myFileCollection")
                doLast {
                    print(thread)
                    val innerThread = Thread()
                    println(innerThread)
                    println(config)
                    println(fileCollection)
                }
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING }
        assert(warnings.size == 2)
        warnings[0].apply {
            assert(startOffset == 212 && endOffset == 218) {
                "Expected to find highlight for 'thread' at 212-218, but was at $startOffset-$endOffset"
            }
            assert(text == "thread")
            assert(description.startsWith("Cannot serialize object of type 'java.lang.Thread', as these are not supported with the configuration cache."))
        }
        warnings[1].apply {
            assert(startOffset == 300 && endOffset == 306) {
                "Expected to find highlight for 'thread' at 300-306, but was at $startOffset-$endOffset"
            }
            assert(text == "config")
            assert(description.startsWith("Cannot serialize object of type 'org.gradle.api.artifacts.Configuration', as these are not supported with the configuration cache."))
        }
    }

    @Test
    fun `test warnings for class declaration call methods`() {
        val file = writeToFile("build.gradle.kts") {
            """
            tasks.register("myTask") {
                val a = A()
                doLast {
                    a.run()
                }
            }
            
            class A {
                fun run() {
                    println(buildDir)
                }
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING }
        assert(warnings.size == 2)
        assertThat(warnings).hasSize(2)
        assertThat(warnings[0].text).isEqualTo("run")
        assertThat(warnings[0].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")

        assertThat(warnings[1].text).isEqualTo("buildDir")
        assertThat(warnings[1].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
    }

    @Test
    fun `test warnings are not shown when method called outside execution block or when method has no problems`() {
        val file = writeToFile("build.gradle.kts") {
            """
            tasks.register("myTask") {
                val a = A()
                a.run()
                doLast {
                    a.run2()
                }
            }
            
            class A {
                fun run() {
                    println(buildDir)
                }
                fun run2() {}
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING }
        assertThat(warnings).isEmpty()
    }
}
