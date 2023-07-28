import com.intellij.openapi.project.Project

class MyClass(val project: Project) {

    fun run() {
       doLast {
            println("Hello World! " + project)
       }
    }

    fun doLast(action: (String?) -> Unit) {
        action(null)
    }
}