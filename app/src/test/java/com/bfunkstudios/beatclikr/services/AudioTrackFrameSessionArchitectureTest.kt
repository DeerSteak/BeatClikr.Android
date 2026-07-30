package com.bfunkstudios.beatclikr.services

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackFrameSessionArchitectureTest {
    @Test
    fun frameSessionContainsNoClickQueueOrEventPositionEnqueue() {
        val source = locateSource().readText()

        listOf(
            "pendingClicks",
            "ArrayDeque",
            "enqueueWaveform",
            "playBeat(",
            "playRhythm("
        ).forEach { token ->
            assertFalse("Frame session contains legacy token: $token", source.contains(token))
        }
    }

    @Test
    fun snapshotsUseSessionPublishedWrittenFrame() {
        val source = locateSource().readText()

        assertTrue(source.contains("@Volatile\n    private var writtenFrame"))
        assertFalse(source.contains("nextFrame = owner.nextFrame"))
        assertTrue(source.contains("before = snapshotSequence"))
        assertTrue(source.contains("before != after || before and 1 != 0"))
    }

    @Test
    fun startUsesPublicationOriginAndReturnsSelectionResult() {
        val source = locateSource().readText()

        assertTrue(source.contains("fun start(rendererFactory: PcmFrameRendererFactory): Boolean"))
        assertTrue(source.contains("owner.publicationFirstOutputFrame"))
        assertTrue(source.contains("failureRing.record(failure)"))
        assertTrue(source.contains("const val FAILURE_CAPACITY"))
        assertFalse(source.contains("failures = failures + failure"))
        assertTrue(source.contains("renderedBlocks = 0"))
        assertTrue(source.contains("cancelled.set(true)"))
        assertTrue(source.contains("if (released) return true"))
    }

    @Test
    fun renderLoopCorrelatesFramesAndResyncsOnNewUnderruns() {
        val source = locateSource().readText()

        assertTrue(source.contains("private val timestamp = AudioFrameTimestamp()"))
        assertTrue(source.contains("correlatedWrittenFrame = owner.nextFrame"))
        assertTrue(source.contains("presentedFrame = timestamp.framePosition"))
        assertTrue(source.contains("presentationNanoTime = timestamp.monotonicTimeNanos"))
        assertTrue(source.contains("observedUnderruns > underrunCount"))
        assertTrue(source.contains("recoveryFrame = Math.addExact(owner.nextFrame, missingFrames)"))
        assertTrue(source.contains("owner.resync(recoveryFrame)"))
    }

    private fun locateSource(): Path {
        val relative = Path.of(
            "src/main/java/com/bfunkstudios/beatclikr/services/AudioTrackFrameSession.kt"
        )
        return generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Path::isRegularFile)
            ?: error("Cannot locate AudioTrackFrameSession.kt")
    }
}
