package com.github.asodja.intellijgradlesuggestionsplugin

import com.intellij.lang.annotation.HighlightSeverity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TODO: Implement custom assertions and make tests a bit nicer
 */
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

    @Test
    fun `test warnings are shown for simple lambda references`() {
        val file = writeToFile("build.gradle.kts") {
            """
            val a = Runnable { println(buildDir) }
            val b: () -> Unit = { println(buildDir) }
            tasks.register("myTask") {
                val c = a
                val d = b
                val e = Runnable { println(buildDir) }
                val f = e
                doLast {
                    a
                    b
                    c
                    d
                    e
                    f
                    val g = Runnable { println(buildDir) }
                    g
                }
            }
            """.trimIndent()
        }
        importProject(false)

        val warnings = file.getHighlights().filter { it.severity == HighlightSeverity.WARNING && !it.description.startsWith("[UNUSED_EXPRESSION]") }
        assertThat(warnings).hasSize(10)
        assertThat(warnings[0].text).isEqualTo("buildDir")
        assertThat(warnings[0].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
        assertThat(warnings[0].startOffset).isEqualTo(27)
        assertThat(warnings[0].endOffset).isEqualTo(35)

        assertThat(warnings[1].text).isEqualTo("buildDir")
        assertThat(warnings[1].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
        assertThat(warnings[1].startOffset).isEqualTo(69)
        assertThat(warnings[1].endOffset).isEqualTo(77)

        assertThat(warnings[2].text).isEqualTo("buildDir")
        assertThat(warnings[2].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
        assertThat(warnings[2].startOffset).isEqualTo(167)
        assertThat(warnings[2].endOffset).isEqualTo(175)

        assertThat(warnings[3].text).isEqualTo("a")
        assertThat(warnings[3].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
        assertThat(warnings[3].startOffset).isEqualTo(214)
        assertThat(warnings[3].endOffset).isEqualTo(215)

        assertThat(warnings[4].text).isEqualTo("b")
        assertThat(warnings[4].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
        assertThat(warnings[4].startOffset).isEqualTo(224)
        assertThat(warnings[4].endOffset).isEqualTo(225)

        assertThat(warnings[5].text).isEqualTo("c")
        assertThat(warnings[5].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache. At line: 1")
        assertThat(warnings[5].startOffset).isEqualTo(234)
        assertThat(warnings[5].endOffset).isEqualTo(235)

        assertThat(warnings[6].text).isEqualTo("d")
        assertThat(warnings[6].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache. At line: 2")
        assertThat(warnings[6].startOffset).isEqualTo(244)
        assertThat(warnings[6].endOffset).isEqualTo(245)

        assertThat(warnings[7].text).isEqualTo("e")
        assertThat(warnings[7].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache. At line: 6")
        assertThat(warnings[7].startOffset).isEqualTo(254)
        assertThat(warnings[7].endOffset).isEqualTo(255)

        assertThat(warnings[8].text).isEqualTo("f")
        assertThat(warnings[8].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache. At line: 6")
        assertThat(warnings[8].startOffset).isEqualTo(264)
        assertThat(warnings[8].endOffset).isEqualTo(265)

        assertThat(warnings[9].text).isEqualTo("buildDir")
        assertThat(warnings[9].description).startsWith("Cannot serialize Gradle script object references as these are not supported with the configuration cache.")
        assertThat(warnings[9].startOffset).isEqualTo(301)
        assertThat(warnings[9].endOffset).isEqualTo(309)
    }
}
