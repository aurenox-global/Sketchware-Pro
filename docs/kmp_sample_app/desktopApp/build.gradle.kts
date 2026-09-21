import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
}

application {
    mainClass.set("pro.sketchware.kmpsample.desktop.MainKt")
}

tasks.register<Jar>("desktopJar") {
    group = "build"
    description = "Assembles runnable Desktop JAR including runtime dependencies."
    archiveClassifier.set("desktop")
    manifest {
        attributes["Main-Class"] = "pro.sketchware.kmpsample.desktop.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map {
            if (it.isDirectory) {
                it
            } else {
                zipTree(it)
            }
        }
    })
}
