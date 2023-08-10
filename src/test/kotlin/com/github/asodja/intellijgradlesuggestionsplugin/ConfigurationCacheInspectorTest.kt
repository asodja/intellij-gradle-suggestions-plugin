package com.github.asodja.intellijgradlesuggestionsplugin

import com.intellij.lang.annotation.HighlightSeverity
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
        assert(warnings.size == 3)
        warnings[0].apply {
            assert(text == "project")
            assert(description.startsWith("Invocation of 'Task.project' by task at execution time is unsupported."))
        }
        warnings[1].apply {
            assert(text == "extensions")
            assert(description.startsWith("Invocation of 'Task.extensions' by task at execution time is unsupported."))
        }
        warnings[2].apply {
            assert(text == "taskDependencies")
            assert(description.startsWith("Invocation of 'Task.taskDependencies' by task at execution time is unsupported."))
        }
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
}
