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
    jvm {}
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
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

// java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
tasks.withType<JavaExec> {
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Djna.nosys=true",
        "-Dfile.encoding=UTF-8"
    )
}

compose.desktop {
    application {
        mainClass = "MainKt"

        jvmArgs += listOf(
            "-Djna.nosys=true",
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            val isCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
            val os = org.gradle.internal.os.OperatingSystem.current()
            val availableFormats = mutableListOf<TargetFormat>()

            if (os.isLinux) {
                val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator).map { File(it) }
                val hasDpkg = isCi || pathDirs.any { File(it, "dpkg-deb").exists() || File(it, "dpkg").exists() }
                val hasRpm = isCi || pathDirs.any { File(it, "rpmbuild").exists() }

                if (hasDpkg) availableFormats.add(TargetFormat.Deb)
                if (hasRpm) availableFormats.add(TargetFormat.Rpm)
            } else if (os.isWindows) {
                availableFormats.add(TargetFormat.Msi)
                availableFormats.add(TargetFormat.Exe)
            } else if (os.isMacOsX) {
                availableFormats.add(TargetFormat.Dmg)
            }

            targetFormats(*(availableFormats.toTypedArray()))
            packageName = "CloudStream"
            packageVersion = "1.0.0"

            val iconsRoot = project.file("src/desktop-icons")
            windows {
                if (iconsRoot.resolve("icon-windows.ico").exists()) {
                    iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                }
            }
            linux {
                if (iconsRoot.resolve("icon-linux.png").exists()) {
                    iconFile.set(iconsRoot.resolve("icon-linux.png"))
                }
            }
        }
    }
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        jvmTarget.set(javaTarget)
    }
}