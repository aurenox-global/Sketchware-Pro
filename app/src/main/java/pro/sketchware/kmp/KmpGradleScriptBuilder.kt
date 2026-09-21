package pro.sketchware.kmp

object KmpGradleScriptBuilder {
    @JvmStatic
    fun build(project: KmpProject): KmpGradleScripts {
        val namespace = escapeForKtsString(project.packageName)
        val rootProjectName = escapeForKtsString(project.name)
        val androidApplicationId = escapeForKtsString("${project.packageName}.androidapp")
        val desktopMainClass = escapeForKtsString("${project.packageName}.desktop.MainKt")

        val settingsGradleKts = """
            import org.gradle.api.initialization.resolve.RepositoriesMode

            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            rootProject.name = "$rootProjectName"

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            include(":shared")
            include(":androidApp")
            include(":desktopApp")
        """.trimIndent() + "\n"

        val rootBuildGradleKts = """
            plugins {
                alias(libs.plugins.kotlin.multiplatform) apply false
                alias(libs.plugins.kotlin.android) apply false
                alias(libs.plugins.kotlin.jvm) apply false
                alias(libs.plugins.android.library) apply false
                alias(libs.plugins.android.application) apply false
            }
        """.trimIndent() + "\n"

        val gradleProperties = """
            kotlin.code.style=official
            org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8
            android.useAndroidX=true
        """.trimIndent() + "\n"

        val sharedBuildGradleKts = """
            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.android.library)
            }

            kotlin {
                androidTarget()
                jvm("desktop")
                jvmToolchain(17)

                sourceSets {
                    val commonMain by getting {
                        dependencies {
                            implementation(libs.kotlinx.coroutines.core)
                        }
                    }
                    val commonTest by getting
                    val androidMain by getting
                    val desktopMain by getting
                }
            }

            android {
                namespace = "$namespace.shared"
                compileSdk = 35
                defaultConfig {
                    minSdk = 24
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        """.trimIndent() + "\n"

        val androidAppBuildGradleKts = """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
            }

            android {
                namespace = "$namespace.android"
                compileSdk = 35

                defaultConfig {
                    applicationId = "$androidApplicationId"
                    minSdk = 24
                    targetSdk = 35
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            kotlin {
                jvmToolchain(17)
            }

            dependencies {
                implementation(project(":shared"))
            }
        """.trimIndent() + "\n"

        val desktopAppBuildGradleKts = """
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
                mainClass.set("$desktopMainClass")
            }

            tasks.register<Jar>("desktopJar") {
                group = "build"
                description = "Assembles runnable Desktop JAR including runtime dependencies."
                archiveClassifier.set("desktop")
                manifest {
                    attributes["Main-Class"] = "$desktopMainClass"
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
        """.trimIndent() + "\n"

        val versionCatalogToml = """
            [versions]
            kotlin = "${project.kotlinVersion}"
            agp = "8.7.3"
            coroutines = "1.8.1"

            [libraries]
            kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }

            [plugins]
            kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
            kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
            kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
            android-application = { id = "com.android.application", version.ref = "agp" }
            android-library = { id = "com.android.library", version.ref = "agp" }
        """.trimIndent() + "\n"

        return KmpGradleScripts(
            settingsGradleKts,
            rootBuildGradleKts,
            gradleProperties,
            sharedBuildGradleKts,
            androidAppBuildGradleKts,
            desktopAppBuildGradleKts,
            versionCatalogToml
        )
    }

    private fun escapeForKtsString(raw: String): String {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
