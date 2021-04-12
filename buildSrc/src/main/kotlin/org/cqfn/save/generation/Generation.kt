/**
 * This file contains code for codegen: generating a list of options for config files and README.
 */

package org.cqfn.save.generation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.KModifier

import java.io.File
import java.io.BufferedReader

/**
 * The comment that will be added to the generated sources file.
 */
private val autoGenerationComment =
    """
    | This document was auto generated, please don't modify it.
    """.trimMargin()

class Option {
    lateinit var argType: String
    lateinit var kotlinType: String
    lateinit var fullName: String
    lateinit var shortName: String
    lateinit var description: String
    lateinit var option: Map<String, String>
}

fun main() {
    println("======================")
    val configFile = "buildSrc/src/main/kotlin/config-options.json"
    val gson = Gson()
    val bufferedReader: BufferedReader = File(configFile).bufferedReader()
    val jsonString = bufferedReader.use { it.readText() }
    val jsonObject = gson.fromJson<Map<String, Option>>(jsonString, object : TypeToken<Map<String, Option>>(){}.type)

    generateConfig(jsonObject)
    generateSaveConfig(jsonObject)
    generateReadme(jsonObject)

    println("\n======================")
}

fun generateConfig(jsonObject: Map<String, Option>) {
    jsonObject.forEach {
        //val currOption = it.value
        //println("${it.key} ${currOption.type}")
    }
    /*
    val t = FunSpec.builder()
        .addCode("""
            |private fun <U> Map<String, String>.getAndParseOrElse(
    |key: String,
    |parse: String.() -> U,
    |default: () -> U) =
        |get(key)?.let(parse) ?: default()
            """)

    val kotlinFile = FileSpec.builder("org.cqfn.save.cli", "Config")
        //.addType(fileBody)
        .indent("    ")
        .addComment(autoGenerationComment)
        .addFunction(FunSpec.builder("main")
            .addStatement("val a = %M()", createA)
            .addStatement("val b = %M()", createB)
            .addStatement("println(a.%M)")
            .addStatement("println(b.%M)")
            .build())
        .build()
    */

    //kotlinFile.writeTo(System.out)
}

fun generateSaveConfig(jsonObject: Map<String, Option>) {
    println("-----------generateSaveConfig------------")
    val builder = FileSpec.builder("org.cqfn.save.core.config", "SaveConfig")
    builder.addImport("okio", "ExperimentalFileSystem")

    var properties = ""
    jsonObject.forEach { properties += ("@property ${it.key} ${it.value.description}\n")}
    val kdoc = """
               |Configuration properties of save application, retrieved either from properties file
               |or from CLI args.
               |$properties
               """.trimMargin()

    val classBuilder = TypeSpec.classBuilder("SaveConfig").addModifiers(KModifier.DATA)
    classBuilder.addKdoc(kdoc)
    val experimentalFileSystem = AnnotationSpec.builder(ClassName("kotlin", "OptIn")).addMember("ExperimentalFileSystem::class")

    classBuilder.addAnnotation(experimentalFileSystem.build())

    val ctor = FunSpec.constructorBuilder()

    for ((name, value) in jsonObject) {
        ctor.addParameter(name, selectType(value))
        val property = PropertySpec.builder(name, selectType(value)).initializer(name).build()
        classBuilder.addProperty(property)
    }
    classBuilder.primaryConstructor(ctor.build())

    builder.addType(classBuilder.build())
    builder.build().writeTo(System.out)
    println("-------------------------------------")
}

fun selectType(value: Option): TypeName =
    when(value.kotlinType) {
        "Boolean" -> ClassName("kotlin", "Boolean")
        "Int" -> ClassName("kotlin", "Int")
        "String" -> ClassName("kotlin", "String")
        "Path" -> ClassName("okio", "Path")
        "Path?" -> ClassName("okio", "Path").copy(nullable = true)
        "ReportType" -> ClassName("org.cqfn.save.core.config","ReportType")
        "LanguageType" -> ClassName("org.cqfn.save.core.config","LanguageType")
        "ResultOutputType" -> ClassName("org.cqfn.save.core.config","ResultOutputType")
        else -> ClassName("kotlin", "Unit")
    }

fun generateReadme(jsonObject: Map<String, Option>) {

}
