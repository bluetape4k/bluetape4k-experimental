pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
        id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
    }
}

val baseProjectName = "bluetape4k"

rootProject.name = "$baseProjectName-experimental"

//val bluetape4kProjectsDir = file("../bluetape4k-projects")
//if (bluetape4kProjectsDir.exists()) {
//    includeBuild(bluetape4kProjectsDir) {
//        dependencySubstitution {
//            substitute(module("io.github.bluetape4k:bluetape4k-cache-lettuce"))
//                .using(project(":bluetape4k-cache-lettuce"))
//        }
//    }
//}

include("shared")

includeModules("kotlin", false, false)
includeModules("coroutines", false, false)
includeModules("ai", false, false)
includeModules("data", false, false)
includeModules("io", false, false)
includeModules("examples", false, false)

fun includeModules(baseDir: String, withProjectName: Boolean = true, withBaseDir: Boolean = true) {
    files("$rootDir/$baseDir").files
        .filter { it.isDirectory }
        .forEach { moduleDir ->
            moduleDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && File(it, "build.gradle.kts").exists() }
                ?.forEach { dir ->
                    val basePath = baseDir.replace("/", "-")
                    val projectName = when {
                        !withProjectName && !withBaseDir -> dir.name
                        withProjectName && !withBaseDir -> baseProjectName + "-" + dir.name
                        withProjectName                 -> baseProjectName + "-" + basePath + "-" + dir.name
                        else                             -> basePath + "-" + dir.name
                    }

                    include(projectName)
                    project(":$projectName").projectDir = dir
                }
        }
}
