package org.cqfn.save.core

import org.cqfn.save.core.config.ReportType
import org.cqfn.save.core.config.SaveProperties
import org.cqfn.save.core.config.defaultConfig

import okio.FileSystem

import kotlin.test.Test

class PerformAnalysisTest {
    private val fs: FileSystem = FileSystem.SYSTEM

    @Test
    fun `detect plugins`() {
        val saveProperties = SaveProperties(
            reportType = ReportType.PLAIN,
            testFiles = listOf("../examples/discovery-test"),
        )
        // In this test we need to merge with emulated empty save.properties file in aim to use default values,
        // since initially all fields are null
        Save(saveProperties.mergeConfigWithPriorityToThis(SaveProperties()), fs).performAnalysis()
    }

    @Test
    fun `should execute single test`() {
        val saveProperties = SaveProperties(
            reportType = ReportType.PLAIN,
            testFiles = listOf("../examples/discovery-test", "../discovery-test/highlevel/suite1/MyTest.java")
        )
        Save(saveProperties.mergeConfigWithPriorityToThis(defaultConfig()), fs).performAnalysis()
        // fixme: check that only a single test has been executed
    }
}
