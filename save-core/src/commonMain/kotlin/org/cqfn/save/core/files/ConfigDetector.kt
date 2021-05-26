package org.cqfn.save.core.files

import org.cqfn.save.core.config.TestConfig
import org.cqfn.save.core.config.isSaveTomlConfig
import org.cqfn.save.core.logging.logDebug
import org.cqfn.save.core.logging.logError

import okio.FileSystem
import okio.Path

/**
 * A class that is capable of discovering config files hierarchy.
 */
class ConfigDetector {
    /**
     * Try to create SAVE config file from [file].
     *
     * @param testConfig - testing configuration (save.toml) from which SAVE config file should be built
     * @return [TestConfig] or null if no suitable config file has been found.
     * @throws IllegalArgumentException - in case of invalid testConfig file
     */
    fun configFromFile(testConfig: Path): TestConfig {
        // testConfig is validated in the beginning and cannot be null
        return discoverConfigWithParents(testConfig)
            ?.also { config ->
                // Go down through the file tree and
                // discover all descendant configs of [config]
                val descendantConfigLocations = config
                    .directory
                    .findAllFilesMatching { it.isSaveTomlConfig() }
                    .flatten()

                createTestConfigs(descendantConfigLocations, mutableListOf(config))
            }
            ?: run {
                logError("Config file was not found in $testConfig")
                throw IllegalArgumentException("Provided option '--test-config' $testConfig doesn't correspond" +
                        " to a valid save.toml file")
            }
    }

    private fun createTestConfigs(descendantConfigLocations: List<Path>, configs: MutableList<TestConfig>) =
            descendantConfigLocations
                .drop(1)  // because [config] will be discovered too
                .mapIndexed { index, path ->
                    val parentConfig = configs.find {
                        it.location ==
                                descendantConfigLocations.take(index + 1)
                                    .reversed()
                                    .find { it.parent in path.parents() }!!
                    }!!
                    // Actually, we don't interested in return value, just need to create proper config
                    TestConfig(path, parentConfig)
                }

    /**
     * Depends to type of entry point, start to create the hierarchy of TestConfig's, starting from the [file] till the top in the file tree
     *
     * @param file entry point from which SAVE should create hierarchy of [TestConfig]'s.
     * @return [TestConfig] or null if no suitable config file has been found.
     */
    private fun discoverConfigWithParents(file: Path): TestConfig? = when {
        // if provided file is a directory, try to find save.toml inside it
        FileSystem.SYSTEM.metadata(file).isDirectory -> getTestConfigFromDirectory(file)
        // if provided file is save.toml, create config from it
        file.isSaveTomlConfig() -> getTestConfigFromTomlFile(file)
        // if provided file is an individual test file, we search a config file in this and parent directories
        // and start processing for this single test (in case it is really a valid test file)
        else -> getTestConfigFromSingleTestFile(file)
    }

    private fun getTestConfigFromSingleTestFile(file: Path) = file
        .parents()
        .mapNotNull { it.findChildByOrNull { it.isSaveTomlConfig() } }
        .firstOrNull()
        ?.let { discoverConfigWithParents(it) }
        .also { logDebug("Processing test config for a single test file: $file") }

    private fun getTestConfigFromDirectory(file: Path) = file
        .findChildByOrNull { it.isSaveTomlConfig() }
        ?.let { discoverConfigWithParents(it) }
        .also { logDebug("Processing test config from directory: $file") }

    private fun getTestConfigFromTomlFile(file: Path): TestConfig {
        // Create instances for all parent configs recursively
        val parentConfig = file.parents()
            .drop(1)  // because immediate parent already contains [this] config
            .mapNotNull { parentDir ->
                parentDir.findChildByOrNull {
                    it.isSaveTomlConfig()
                }
            }
            .firstOrNull()
            ?.let { getTestConfigFromTomlFile(it) }
        // Now process current config
        logDebug("Processing test config from the toml file: $file")
        return TestConfig(
            file,
            parentConfig
        )
    }
}
