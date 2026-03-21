plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("22.14.0")
    npmVersion.set("10.9.0")
    download.set(true)
}

tasks {
    val npmInstall by getting

    val npmBuild by registering(com.github.gradle.node.npm.task.NpmTask::class) {
        dependsOn(npmInstall)
        args.set(listOf("run", "build", "--", "--configuration=production"))
        inputs.dir("src")
        inputs.files("angular.json", "tsconfig.json", "package.json")
        outputs.dir(layout.buildDirectory.dir("dist"))
    }

    val npmTest by registering(com.github.gradle.node.npm.task.NpmTask::class) {
        dependsOn(npmInstall)
        args.set(listOf("run", "test"))
    }

    named("build") { dependsOn(npmBuild) }
    named("test") { dependsOn(npmTest) }
}
