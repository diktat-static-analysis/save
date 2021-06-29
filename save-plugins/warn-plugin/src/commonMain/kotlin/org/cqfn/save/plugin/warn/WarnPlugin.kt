package org.cqfn.save.plugin.warn

import org.cqfn.save.core.config.TestConfig
import org.cqfn.save.core.files.createFile
import org.cqfn.save.core.files.readLines
import org.cqfn.save.core.plugin.GeneralConfig
import org.cqfn.save.core.plugin.Plugin
import org.cqfn.save.core.result.DebugInfo
import org.cqfn.save.core.result.Fail
import org.cqfn.save.core.result.Pass
import org.cqfn.save.core.result.TestResult
import org.cqfn.save.core.result.TestStatus
import org.cqfn.save.core.utils.ProcessExecutionException
import org.cqfn.save.plugin.warn.utils.Warning
import org.cqfn.save.plugin.warn.utils.extractWarning

import okio.FileSystem
import okio.Path

private typealias WarningMap = MutableMap<LineColumn?, List<Warning>>
internal typealias LineColumn = Pair<Int, Int>

/**
 * A plugin that runs an executable and verifies that it produces required warning messages.
 * @property testConfig
 */
class WarnPlugin(
    testConfig: TestConfig,
    testFiles: List<String> = emptyList(),
    useInternalRedirections: Boolean = true) : Plugin(
    testConfig,
    testFiles,
    useInternalRedirections) {
    private val fs = FileSystem.SYSTEM
    private val expectedAndNotReceived = "Some warnings were expected but not received"
    private val unexpected = "Some warnings were unexpected"

    override fun handleFiles(files: Sequence<List<Path>>): Sequence<TestResult> {
        testConfig.validateAndSetDefaults()

        val warnPluginConfig = testConfig.pluginConfigs.filterIsInstance<WarnPluginConfig>().single()
        val generalConfig = testConfig.pluginConfigs.filterIsInstance<GeneralConfig>().singleOrNull()

        return files.chunked(warnPluginConfig.batchSize ?: 1).map { chunk ->
            handleTestFile(chunk.map { it.single() }, warnPluginConfig, generalConfig)
        }.flatten()
    }

    override fun rawDiscoverTestFiles(resourceDirectories: Sequence<Path>): Sequence<List<Path>> {
        val warnPluginConfig = testConfig.pluginConfigs.filterIsInstance<WarnPluginConfig>().single()
        val regex = warnPluginConfig.resourceNamePattern
        // returned sequence is a sequence of groups of size 1
        return resourceDirectories.flatMap { directory ->
            fs.list(directory)
                .filter { regex.matches(it.name) }
                .map { listOf(it) }
        }
    }

    override fun cleanupTempDir() {
        val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / WarnPlugin::class.simpleName!!)
        if (fs.exists(tmpDir)) {
            fs.deleteRecursively(tmpDir)
        }
    }

    @Suppress(
        "TOO_LONG_FUNCTION",
        "SAY_NO_TO_VAR",
        "LongMethod")
    private fun handleTestFile(
        paths: List<Path>,
        warnPluginConfig: WarnPluginConfig,
        generalConfig: GeneralConfig?): List<TestResult> {
        val expectedWarnings: WarningMap = mutableMapOf()
        paths.forEach { path ->
            expectedWarnings.putAll(
                fs.readLines(path)
                    .mapNotNull {
                        with(warnPluginConfig) {
                            it.extractWarning(
                                warningsInputPattern!!,
                                path.name,
                                lineCaptureGroup,
                                columnCaptureGroup,
                                messageCaptureGroup!!,
                            )
                        }
                    }
                    .groupBy {
                        if (it.line != null && it.column != null) {
                            it.line to it.column
                        } else {
                            null
                        }
                    }
                    .mapValues { it.value.sortedBy { it.message } }
            )
        }

        val fileNames = paths.joinToString {
            if (generalConfig!!.ignoreSaveComments == true) createTestFile(it, warnPluginConfig.warningsInputPattern!!) else it.toString()
        }
        val execCmd = "${generalConfig!!.execCmd} ${warnPluginConfig.execFlags} $fileNames"
        val executionResult = try {
            pb.exec(execCmd, null)
        } catch (ex: ProcessExecutionException) {
            return listOf(TestResult(
                paths,
                Fail("${ex::class.simpleName}: ${ex.message}", ex::class.simpleName ?: "Unknown exception"),
                DebugInfo(null, ex.message, null)
            ))
        }
        val stdout = executionResult.stdout
        val stderr = executionResult.stderr

        val actualWarningsMap = executionResult.stdout.mapNotNull {
            with(warnPluginConfig) {
                it.extractWarning(warningsOutputPattern!!, fileNameCaptureGroupOut!!, lineCaptureGroupOut, columnCaptureGroupOut, messageCaptureGroupOut!!)
            }
        }
            .groupBy { if (it.line != null && it.column != null) it.line to it.column else null }
            .mapValues { (_, warning) -> warning.sortedBy { it.message } }

        return paths.map { path ->
            TestResult(
                listOf(path),
                checkResults(
                    expectedWarnings.filter { it.value.any { warning -> warning.fileName == path.name } },
                    actualWarningsMap.filter { it.value.any { warning -> warning.fileName == path.name } },
                    warnPluginConfig
                ),
                DebugInfo(
                    stdout.filter { it.contains(path.name) }.joinToString("\n"),
                    stderr.filter { it.contains(path.name) }.joinToString("\n"),
                    null)
            )
        }
    }

    /**
     * @param path
     * @param warningsInputPattern
     * @return name of the temporary file
     */
    internal fun createTestFile(path: Path, warningsInputPattern: Regex): String {
        val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / WarnPlugin::class.simpleName!!)
        val relativePath = createPathRelativeToTheParentConfig(path)
        val tmpPath: Path = tmpDir / relativePath
        createTempDir(tmpPath)

        val fileName = tmpPath / path.name
        fs.write(fs.createFile(fileName)) {
            fs.readLines(path).forEach {
                if (!warningsInputPattern.matches(it)) {
                    write(
                        (it + "\n").encodeToByteArray()
                    )
                }
            }
        }
        return fileName.toString()
    }

    /**
     * Compares actual and expected warnings and returns TestResult
     *
     * @param expectedWarningsMap expected warnings, grouped by LineCol
     * @param actualWarningsMap actual warnings, grouped by LineCol
     * @param warnPluginConfig configuration of warn plugin
     * @return [TestResult]
     */
    @Suppress("TYPE_ALIAS")
    internal fun checkResults(expectedWarningsMap: Map<LineColumn?, List<Warning>>,
                              actualWarningsMap: Map<LineColumn?, List<Warning>>,
                              warnPluginConfig: WarnPluginConfig): TestStatus {
        val missingWarnings = expectedWarningsMap.valuesNotIn(actualWarningsMap)
        val unexpectedWarnings = actualWarningsMap.valuesNotIn(expectedWarningsMap)

        return when (missingWarnings.isEmpty() to unexpectedWarnings.isEmpty()) {
            false to true -> createFail(expectedAndNotReceived, missingWarnings)
            false to false -> Fail(
                "$expectedAndNotReceived: $missingWarnings, and ${unexpected.lowercase()}: $unexpectedWarnings",
                "$expectedAndNotReceived (${missingWarnings.size}), and ${unexpected.lowercase()} (${unexpectedWarnings.size})"
            )
            true to false -> if (warnPluginConfig.exactWarningsMatch == false) {
                Pass("$unexpected: $unexpectedWarnings")
            } else {
                createFail(unexpected, unexpectedWarnings)
            }
            true to true -> Pass(null)
            else -> Fail("", "")
        }
    }

    private fun createFail(baseText: String, warnings: List<Warning>) = Fail("$baseText: $warnings", "$baseText (${warnings.size})")
}

/**
 * Collect all values that are present in [this] map, but absent in [other]
 */
private fun <K, V> Map<K, List<V>>.valuesNotIn(other: Map<K, List<V>>): List<V> = flatMap { (key, value) ->
    other[key]?.let { otherValue ->
        value.filter { it !in otherValue }
    }
        ?: value
}
