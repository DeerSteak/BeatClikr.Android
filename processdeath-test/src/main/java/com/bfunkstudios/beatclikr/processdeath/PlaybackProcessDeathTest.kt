package com.bfunkstudios.beatclikr.processdeath

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackProcessDeathTest {
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        shell("pm clear $TARGET_PACKAGE")
    }

    @After
    fun tearDown() {
        forceStop()
    }

    @Test
    fun everyTransportStateRestartsIdleWithoutAutomaticPlayback() {
        val states = listOf(
            "Preparing",
            "Starting",
            "Playing",
            "Stopping",
            "Interrupted",
            "Failed"
        )

        states.forEach { expectedState ->
            configure(expectedState)
            launch()
            tap(playPattern)
            if (expectedState == "Stopping") tap(pausePattern)
            awaitSnapshot("state", expectedState)
            val oldPid = pid()

            forceStop()
            launch()

            assertNotEquals(oldPid, pid())
            assertEquals("Idle", snapshot()["state"])
            assertEquals("0", snapshot()["starts"])
            assertEquals("0", snapshot()["transitions"])
            assertEquals("false", snapshot()["focusHeld"])
            find(playPattern)
            forceStop()
        }
    }

    @Test
    fun explicitPlayAfterRecreationCreatesOneFreshUserSession() {
        configure("Playing")
        launch()
        tap(playPattern)
        awaitSnapshot("state", "Playing")
        val oldPid = pid()

        forceStop()
        launch()

        assertNotEquals(oldPid, pid())
        assertEquals("Idle", snapshot()["state"])
        assertEquals("0", snapshot()["starts"])
        assertEquals("false", snapshot()["focusHeld"])
        tap(playPattern)
        awaitSnapshot("state", "Playing")
        val started = snapshot()
        assertEquals("1", started["starts"])
        assertEquals("USER", started["origin"])
        assertEquals("1", started["session"])
        assertTrue(started.getValue("transitions").toLong() > 0)
        find(pausePattern)
    }

    private fun configure(mode: String) {
        launch()
        shell("content call --uri content://$AUTHORITY --method configure --arg $mode")
        forceStop()
    }

    private fun launch() {
        shell("am start -W -n $TARGET_PACKAGE/com.bfunkstudios.beatclikr.MainActivity")
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), TIMEOUT)
    }

    private fun forceStop() {
        shell("am force-stop $TARGET_PACKAGE")
        device.wait(Until.gone(By.pkg(TARGET_PACKAGE).depth(0)), TIMEOUT)
    }

    private fun pid(): String = shell("pidof $TARGET_PACKAGE").trim().also {
        assertTrue("Expected target process PID", it.isNotEmpty())
    }

    private fun tap(pattern: Pattern) = find(pattern).click()

    private fun find(pattern: Pattern) = checkNotNull(
        device.wait(Until.findObject(By.text(pattern)), TIMEOUT)
    ) { "Could not find text matching $pattern" }

    private fun awaitSnapshot(key: String, expected: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            if (snapshot()[key] == expected) return
            Thread.sleep(50)
        }
        error("Expected $key=$expected, last snapshot=${snapshot()}")
    }

    private fun snapshot(): Map<String, String> {
        val output = shell("content call --uri content://$AUTHORITY --method snapshot")
        return ENTRY.findAll(output).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private companion object {
        const val TARGET_PACKAGE = "com.bfunkstudios.beatclikr.debug"
        const val AUTHORITY = "$TARGET_PACKAGE.process-death-probe"
        const val TIMEOUT = 8_000L
        val ENTRY = Regex(
            "(state|session|origin|transitions|starts|stops|focusHeld)=([^,}]+)"
        )
        val playPattern: Pattern = Pattern.compile("Play|Reproducir")
        val pausePattern: Pattern = Pattern.compile("Pause|Pausar")
    }
}
