plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // workaround https://github.com/gradle/gradle/issues/15383
    implementation(files(project.libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.diktat.gradle.plugin)
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.plugin.serialization)
    implementation("io.github.gradle-nexus:publish-plugin:2.0.0")
    implementation("org.ajoberstar.reckon:reckon-gradle:0.19.1")
    implementation("com.squareup:kotlinpoet:2.0.0")
    implementation("com.google.code.gson:gson:2.12.1")
}
