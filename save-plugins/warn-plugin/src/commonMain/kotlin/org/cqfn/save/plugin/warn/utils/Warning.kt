/**
 * Classes and methods to work with warnings
 */

package org.cqfn.save.plugin.warn.utils

import org.cqfn.save.core.logging.describe
import org.cqfn.save.core.logging.logWarn
import org.cqfn.save.core.plugin.ResourceFormatException

import okio.Path

/**
 * Class for warnings which should be discovered and compared wit analyzer output
 *
 * @property message warning text
 * @property line line on which this warning occurred
 * @property column column in which this warning occurred
 * @property fileName file name
 */
data class Warning(
    val message: String,
    val line: Int?,
    val column: Int?,
    val fileName: String,
)

/**
 * Extract warning from [this] string using provided parameters
 *
 * @param warningRegex regular expression for warning
 * @param columnGroupIdx index of capture group for column number
 * @param messageGroupIdx index of capture group for waring text
 * @param fileName file name
 * @param line line number of warning
 * @return a [Warning] or null if [this] string doesn't match [warningRegex]
 * @throws ResourceFormatException when parsing a file
 */
internal fun String.extractWarning(warningRegex: Regex,
                                   fileName: String,
                                   line: Int?,
                                   columnGroupIdx: Int?,
                                   messageGroupIdx: Int,
): Warning? {
    val groups = warningRegex.find(this)?.groups ?: return null

    val column = getRegexGroupSafe(columnGroupIdx, groups, this, "column number")?.toIntOrNull()
    val message = getRegexGroupSafe(messageGroupIdx, groups, this, "warning message")!!
    return Warning(
        message,
        line,
        column,
        fileName,
    )
}

/**
 * Extract warning from [this] string using provided parameters
 *
 * @param warningRegex regular expression for warning
 * @param columnGroupIdx index of capture group for column number
 * @param messageGroupIdx index of capture group for waring text
 * @param fileNameGroupIdx index of capture group for file name
 * @param line line number of warning
 * @return a [Warning] or null if [this] string doesn't match [warningRegex]
 * @throws ResourceFormatException when parsing a file
 */
@Suppress(
    "TooGenericExceptionCaught",
    "SwallowedException")
internal fun String.extractWarning(warningRegex: Regex,
                                   fileNameGroupIdx: Int,
                                   line: Int?,
                                   columnGroupIdx: Int?,
                                   messageGroupIdx: Int,
): Warning? {
    val groups = warningRegex.find(this)?.groups ?: return null
    val fileName = getRegexGroupSafe(fileNameGroupIdx, groups, this, "file name")!!

    return extractWarning(warningRegex, fileName, line, columnGroupIdx, messageGroupIdx)
}

/**
 * @param warningRegex regular expression for warning
 * @param lineGroupIdx index of capture group for line number
 * @param placeholder placeholder for line
 * @param lineNum number of line
 * @param file path to test file
 * @param linesFile lines of file
 * @return a [Warning] or null if [this] string doesn't match [warningRegex]
 * @throws ResourceFormatException when parsing a file
 */
@Suppress(
    "TooGenericExceptionCaught",
    "SwallowedException",
    "NestedBlockDepth",
    "LongParameterList",
    "ReturnCount",
    "TOO_MANY_PARAMETERS",
)
internal fun String.getLineNumber(warningRegex: Regex,
                                  lineGroupIdx: Int?,
                                  placeholder: String,
                                  lineNum: Int?,
                                  file: Path?,
                                  linesFile: List<String>?,
): Int? {
    val groups = warningRegex.find(this)?.groups ?: return null

    return lineGroupIdx?.let {
        val lineValue = groups[lineGroupIdx]!!.value
        if (lineValue.isEmpty() && lineNum != null && linesFile != null) {
            return plusLine(file, warningRegex, linesFile, lineNum)
        } else {
            lineValue.toIntOrNull() ?: run {
                if (lineValue[0] != placeholder[0]) {
                    throw ResourceFormatException("The group <$lineValue> is neither a number nor a placeholder.")
                }
                try {
                    val line = lineValue.substringAfterLast(placeholder)
                    lineNum!! + 1 + if (line.isNotEmpty()) line.toInt() else 0
                } catch (e: Exception) {
                    throw ResourceFormatException("Could not extract line number from line [$this], cause: ${e.describe()}")
                }
            }
        }
    }
}

@Suppress(
    "WRONG_NEWLINES",
    "TooGenericExceptionCaught",
    "SwallowedException",
)
private fun getRegexGroupSafe(idx: Int?,
                              groups: MatchGroupCollection,
                              line: String,
                              exceptionMessage: String,
): String? {
    return idx?.let {
        try {
            return groups[idx]!!.value
        } catch (e: Exception) {
            throw ResourceFormatException("Could not extract $exceptionMessage from line [$line], cause: ${e.message}")
        }
    }
}

private fun plusLine(
    file: Path?,
    warningRegex: Regex,
    linesFile: List<String>,
    lineNum: Int
): Int {
    val fileSize = linesFile.size
    val newLine = lineNum + 1 + linesFile.drop(lineNum).takeWhile { warningRegex.containsMatchIn(it) }.count()
    if (newLine > fileSize) {
        logWarn("Some warnings are at the end of the file: <$file>. They will be assigned the following line: $newLine")
        return fileSize
    }
    return newLine
}
