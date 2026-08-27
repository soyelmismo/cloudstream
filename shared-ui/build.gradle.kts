import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = true
}


val javaTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())

kotlin {
    android {
        namespace = "com.lagradost.cloudstream3.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        
        compilerOptions {
            jvmTarget.set(javaTarget)
        }
    }

    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "com.lagradost.cloudstream3.InternalAPI",
            "com.lagradost.cloudstream3.Prerelease",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            api(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(project(":library"))
            implementation(libs.nicehttp)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.sqlite)
        }

        androidMain.dependencies {
            api(libs.bundles.media3)
            api(libs.bundles.nextlib)
            api(libs.juniversalchardet)
            implementation(libs.video)
            implementation(libs.anime.db)
            implementation(libs.torrentserver)
            implementation(libs.core.ktx)
            implementation(libs.appcompat)
            implementation(libs.annotation)
            implementation(libs.jackson.module.kotlin)
            implementation(libs.material)
            implementation(libs.fragment.ktx)
            implementation(libs.tvprovider)
            implementation(libs.bundles.coil)
            implementation(libs.work.runtime.ktx)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
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
