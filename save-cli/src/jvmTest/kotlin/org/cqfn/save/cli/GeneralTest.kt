package org.cqfn.save.cli

import org.cqfn.save.core.config.OutputStreamType
import org.cqfn.save.core.files.StdStreamsSink
import org.cqfn.save.core.files.readFile
import org.cqfn.save.core.result.Pass
import org.cqfn.save.core.utils.CurrentOs
import org.cqfn.save.core.utils.ProcessBuilder
import org.cqfn.save.core.utils.getCurrentOs
import org.cqfn.save.core.utils.isCurrentOsWindows
import org.cqfn.save.plugins.fix.FixPlugin
import org.cqfn.save.reporter.Report
import org.cqfn.save.reporter.json.JsonReporter

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString

@Suppress(
    "TOO_LONG_FUNCTION",
    "INLINE_CLASS_CAN_BE_USED",
    "MISSING_KDOC_TOP_LEVEL",
    "MISSING_KDOC_CLASS_ELEMENTS"
)
@OptIn(ExperimentalSerializationApi::class)
class GeneralTest {
    private val fs = FileSystem.SYSTEM

    // The `out` property for reporter is basically the stub, just need to create an instance in aim to use json formatter
    private val json = JsonReporter(StdStreamsSink(OutputStreamType.STDOUT).buffer()) {
        FixPlugin.FixTestFiles.register(this)
    }.json

    @Test
    fun `examples test`() {
        val binDir = "../save-cli/build/bin/" + when (getCurrentOs()) {
            CurrentOs.LINUX -> "linuxX64"
            CurrentOs.MACOS -> "macosX64"
            CurrentOs.WINDOWS -> "mingwX64"
            else -> return
        } + "/debugExecutable"

        assertTrue(fs.exists(binDir.toPath()))

        val saveExecutableFiles = fs.list(binDir.toPath()).filter { fs.metadata(it).isRegularFile }
        // Binary should be created at this moment
        assertTrue(saveExecutableFiles.isNotEmpty())

        val examplesDir = "../examples/kotlin-diktat/"

        val actualSaveBinary = saveExecutableFiles.last()
        val saveBinName = if (isCurrentOsWindows()) "save.exe" else "save"
        val destination = examplesDir.toPath() / saveBinName
        // Copy latest version of save into examples
        fs.copy(actualSaveBinary, destination)
        assertTrue(fs.exists(destination))

        // Check for existence of diktat and ktlint
        assertTrue(fs.exists((examplesDir.toPath() / "diktat.jar")))
        assertTrue(fs.exists((examplesDir.toPath() / "ktlint")))

        // Make sure, that we will check report, which will be obtained after current execution; remove old report if exist
        val reportFile = examplesDir.toPath() / "save.out.json".toPath()
        if (fs.exists(reportFile)) {
            fs.delete(reportFile)
        }

        val runCmd = if (isCurrentOsWindows()) "" else "sudo chmod +x $saveBinName && ./"
        val saveFlags = " . --result-output FILE --report-type JSON"
        // Execute the script from examples
        val execCmd = "$runCmd$saveBinName $saveFlags"
        val pb = ProcessBuilder(true, fs).exec(execCmd, examplesDir, null, 300_000L)
        println("SAVE execution output:\n${pb.stdout.joinToString("\n")}")
        if (pb.stderr.isNotEmpty()) {
            println("Warning and errors during SAVE execution:\n${pb.stderr.joinToString("\n")}")
        }

        // We need some time, before the report will be completely filled
        Thread.sleep(30_000)

        // Report should be created after successful completion
        assertTrue(fs.exists(reportFile))

        val reports: List<Report> = json.decodeFromString(fs.readFile(reportFile))
        // Almost all result statuses should be Pass, except the few cases
        reports.forEach { report ->
            report.pluginExecutions.forEach { pluginExecution ->
                pluginExecution.testResults.find { result ->
                    println(result.status)
                    // FixMe: if we will have other failing tests - we will make the logic less hardcoded
                    result.resources.test.name != "GarbageTest.kt" &&
                            result.resources.test.name != "ThisShouldAlwaysFailTest.kt" &&
                            !result.resources.test.toString().contains("warn${Path.DIRECTORY_SEPARATOR}chapter2")
                }?.let {
                    assertTrue(it.status is Pass)
                }
            }
        }
        fs.delete(destination)
        fs.delete(reportFile)
    }
}
