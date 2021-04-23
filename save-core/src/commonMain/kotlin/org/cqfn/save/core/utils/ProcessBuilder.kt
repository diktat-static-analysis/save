/**
 * Utilities to run a process and get its result.
 */

package org.cqfn.save.core.utils

import org.cqfn.save.core.files.createFile
import org.cqfn.save.core.files.readLines
import org.cqfn.save.core.logging.logDebug
import org.cqfn.save.core.logging.logWarn

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

import kotlinx.datetime.Clock

/**
 * A class that is capable of executing processes, specific to different OS and returning their output.
 */
@Suppress("EMPTY_PRIMARY_CONSTRUCTOR")  // expected class should have explicit default constructor
expect class ProcessBuilderInternal() {
    /**
     * Modify execution command for popen,
     * stdout and stderr will be redirected to tmp files
     *
     * @param command raw command
     * @param collectStdout whether to collect stdout for future usage
     * @return command with redirection of stderr to tmp file
     */
    fun prepareCmd(command: String, collectStdout: Boolean, stdoutFile: Path, stderrFile: Path): String

    /**
     * Execute [cmd] and wait for its completion.
     *
     * @param cmd executable command with arguments
     * @return exit status
     */
    fun exec(cmd: String): Int
}

/**
 * Class contains common logic for all platforms
 */
@Suppress("INLINE_CLASS_CAN_BE_USED")
class ProcessBuilder {
    /**
     * Instance, containing platform-dependent realization of command execution
     */
    private val processBuilderInternal = ProcessBuilderInternal()

    /**
     * Execute [command] and wait for its completion.
     *
     * @param command executable command with arguments
     * @param redirectTo a file where process output should be redirected. If null, output will be returned as [ExecutionResult.stdout].
     * @param collectStdout whether to collect stdout for future usage, [redirectTo] will be ignored
     * @return [ExecutionResult] built from process output
     */
    fun exec(
        command: List<String>,
        redirectTo: Path?,
        collectStdout: Boolean = true): ExecutionResult {
        val fs = FileSystem.SYSTEM

        //Temporary directory for stderr and stdout (posix `system()` can't separate streams, so we do it ourselves)
        val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
                ("ProcessBuilder_" + Clock.System.now().toEpochMilliseconds()).toPath())

        //Path to stdout file
        val stdoutFile = tmpDir / "stdout.txt"

        //Path to stderr file
        val stderrFile = tmpDir / "stderr.txt"
        fs.createDirectories(tmpDir)
        fs.createFile(stdoutFile)
        fs.createFile(stderrFile)
        logDebug("Created temp directory $tmpDir for stderr and stdout of ProcessBuilder")
        val userCmd = command.joinToString(" ")
        if (userCmd.contains(">")) {
            logWarn("Found user provided redirections in `$userCmd`. " +
                    "SAVE use own redirections for internal purpose and will redirect all to the $tmpDir")
        }
        val cmd = processBuilderInternal.prepareCmd(userCmd, collectStdout, stdoutFile, stderrFile)
        val status = processBuilderInternal.exec(cmd)
        println("Tmp dir exists${fs.exists(tmpDir)}")
        println("Stdout file exists: ${fs.exists(stdoutFile)}")
        println("Stderr file exists: ${fs.exists(stderrFile)}")
        val stdout = fs.readLines(stdoutFile)
        val stderr = fs.readLines(stderrFile)
        fs.deleteRecursively(tmpDir)
        logDebug("Removed temp directory $tmpDir")
        if (stderr.isNotEmpty()) {
            logWarn(stderr.joinToString("\n"))
        }
        redirectTo?.let {
            fs.write(redirectTo) {
                write(stdout.joinToString("\n").encodeToByteArray())
            }
        } ?: logDebug("Execution output:\n$stdout")
        return ExecutionResult(status, stdout, stderr)
    }
}

/**
 * @property code exit code
 * @property stdout content of stdout
 * @property stderr content of stderr
 */
data class ExecutionResult(
    val code: Int,
    val stdout: List<String>,
    val stderr: List<String>,
)
