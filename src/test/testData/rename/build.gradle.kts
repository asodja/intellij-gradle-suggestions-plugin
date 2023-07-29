val message = "Hello World"
val configurableFileCollection = configurations.create("myConfig")

fun myFun() {
    println("Hello")
}

tasks.register("myTask") {
    doLast {
        project.name
        buildDir
    }
}