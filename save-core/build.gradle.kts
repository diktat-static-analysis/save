import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
    maven(url = "https://kotlin.bintray.com/kotlinx/")
}

kotlin {
    jvm()
    val os = getCurrentOperatingSystem()
    val saveTarget = when {
        os.isMacOsX -> macosX64("save")
        os.isLinux -> linuxX64("save")
        os.isWindows -> mingwX64("save")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    saveTarget.apply {
        binaries {
            executable {
                entryPoint = "org.cqfn.save.cli.main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio-multiplatform:3.0.0-alpha.1")
            }
        }
        val commonTest by getting

        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-engine:5.0.0")
            }
        }
    }
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}
