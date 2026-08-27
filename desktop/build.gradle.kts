import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(project(":shared-ui"))
            implementation(project(":library"))
            implementation(libs.vlcj)
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation("de.femtopedia.dex2jar:dex-translator:2.4.28")
            implementation("de.femtopedia.dex2jar:dex-tools:2.4.28")
            implementation(libs.newpipeextractor)
            implementation(libs.nicehttp)
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        jvmArgs += listOf(
            "-Djna.nosys=true",
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe
            )
            packageName = "CloudStream"
            packageVersion = "1.0.0"
            description = "CloudStream - Modern cross-platform streaming application"
            copyright = "© 2026 CloudStream Community. All rights reserved."
            vendor = "CloudStream"

            modules(
                "java.instrument",
                "java.sql",
                "java.naming",
                "jdk.unsupported"
            )

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon.png"))
                debMaintainer = "cloudstream@users.noreply.github.com"
                menuGroup = "AudioVideo;Video;"
                appCategory = "Video"
            }

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon.png"))
                menu = true
                shortcut = true
                upgradeUuid = "a6125027-2dc7-4a0b-9ef1-4b13861db6dc"
                dirChooser = true
                perUserInstall = true
            }

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/icon.png"))
                bundleID = "com.lagradost.cloudstream3"
                appStore = false
                dockName = "CloudStream"
            }
        }
    }
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(javaTarget)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>> {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
