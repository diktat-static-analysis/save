/**
 * Utilities to work with SAVE config in CLI mode
 */

package org.cqfn.save.cli

import org.cqfn.save.cli.logging.logErrorAndExit
import org.cqfn.save.core.config.SaveProperties
import org.cqfn.save.core.logging.logDebug
import org.cqfn.save.core.logging.logInfo

import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromMap
import kotlinx.serialization.serializer

/**
 * @return this config in case we have valid configuration
 */
fun SaveProperties.validate(): SaveProperties {
    try {
        FileSystem.SYSTEM.metadata(this.testConfig!!.toPath())
    } catch (e: FileNotFoundException) {
        logErrorAndExit(ExitCodes.INVALID_CONFIGURATION, "Not able to find file '${this.testConfig}'." +
                " Please provide a valid path to the test config via command-line or using the file with properties.")
    }

    return this
}

/**
 * @param args CLI args
 * @return an instance of [SaveProperties]
 */
@Suppress("TOO_LONG_FUNCTION")
fun createConfigFromArgs(args: Array<String>): SaveProperties {
    // getting configuration from command-line arguments
    val configFromCli = SaveProperties(args)
    // reading configuration from the properties file
    val configFromPropertiesFile = readPropertiesFile(configFromCli.propertiesFile)
    // merging two configurations into signle [SaveProperties] class with a priority to command line arguments
    return configFromCli.mergeConfigWithPriorityToThis(configFromPropertiesFile).validate()
}

/**
 * @param propertiesFileName path to the save.properties file
 * @return an instance of [SaveProperties] deserialized from this file
 */
@OptIn(ExperimentalSerializationApi::class)
fun readPropertiesFile(propertiesFileName: String?): SaveProperties {
    propertiesFileName ?: return SaveProperties()

    logDebug("Reading properties file $propertiesFileName")

    val properties: Map<String, String> = try {
        FileSystem.SYSTEM.read(propertiesFileName.toPath()) {
            generateSequence { readUtf8Line() }.toList()
        }
            .associate { line ->
                line.split("=", limit = 2).let {
                    if (it.size != 2) {
                        logErrorAndExit(ExitCodes.GENERAL_ERROR,
                            "Incorrect format of property in $propertiesFileName" +
                                    " Should be <key = value>, but was <$line>")
                    }
                    it[0].trim() to it[1].trim()
                }
            }
    } catch (e: IOException) {
        logErrorAndExit(
            ExitCodes.GENERAL_ERROR,
            "Failed to read properties file $propertiesFileName: ${e.message}"
        )
    }

    logDebug("Read properties from the properties file: $properties")
    val deserializedPropertiesFile: SaveProperties = Properties.decodeFromMap(properties)
    logInfo("Read properties from the properties file: $deserializedPropertiesFile")

    return deserializedPropertiesFile
}
