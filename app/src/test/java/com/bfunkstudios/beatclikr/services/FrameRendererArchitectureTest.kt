package com.bfunkstudios.beatclikr.services

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Test

class FrameRendererArchitectureTest {
    @Test
    fun renderImplementationContainsNoBlockingOrExternalWork() {
        val source = locateRendererSource().readText()
        val violations = FORBIDDEN_TOKENS.filter(source::contains)

        assertFalse(
            "Renderer contains forbidden real-time work: ${violations.joinToString()}",
            violations.isNotEmpty()
        )
    }

    private fun locateRendererSource(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val relative = Path.of(
            "src/main/java/com/bfunkstudios/beatclikr/services/FramePcmRenderer.kt"
        )
        return generateSequence(workingDirectory) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Path::isRegularFile)
            ?: error("Cannot locate FramePcmRenderer.kt from $workingDirectory")
    }

    private companion object {
        val FORBIDDEN_TOKENS = listOf(
            "synchronized",
            ".lock(",
            "Thread.sleep",
            ".wait(",
            "java.io",
            "android.util.Log",
            "Timber.",
            "android.database",
            "androidx.room",
            "Handler(",
            ".post("
        )
    }
}
