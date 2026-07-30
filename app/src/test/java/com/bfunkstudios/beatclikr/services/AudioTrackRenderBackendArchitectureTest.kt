package com.bfunkstudios.beatclikr.services

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackRenderBackendArchitectureTest {
    @Test
    fun timestampHolderIsAllocatedOnceOutsideTimestampCalls() {
        val source = locateSource().readText()

        assertTrue(source.contains("private val platformTimestamp = AudioTimestamp()"))
        assertEquals(1, Regex("""AudioTimestamp\(\)""").findAll(source).count())
    }

    private fun locateSource(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val relative = Path.of(
            "src/main/java/com/bfunkstudios/beatclikr/services/AudioTrackRenderBackend.kt"
        )
        return generateSequence(workingDirectory) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Path::isRegularFile)
            ?: error("Cannot locate AudioTrackRenderBackend.kt from $workingDirectory")
    }
}
