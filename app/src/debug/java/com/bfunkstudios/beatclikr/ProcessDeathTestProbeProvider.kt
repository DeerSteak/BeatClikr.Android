package com.bfunkstudios.beatclikr

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.bfunkstudios.beatclikr.services.PlaybackCoordinator
import com.bfunkstudios.beatclikr.services.PlaybackTransportState

class ProcessDeathTestProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        "configure" -> Bundle().apply {
            providerContext().getSharedPreferences(
                ProcessDeathTestEngine.PREFS,
                android.content.Context.MODE_PRIVATE
            ).edit().putString(
                ProcessDeathTestEngine.MODE,
                arg ?: ProcessDeathTestEngine.NORMAL
            ).commit()
            putString("mode", arg)
        }
        "snapshot" -> snapshot()
        else -> error("Unknown process-death probe method: $method")
    }

    private fun snapshot(): Bundle {
        val application = providerContext().applicationContext as BeatClikrApplication
        val coordinator = application.audioPlayerService as PlaybackCoordinator
        val state = coordinator.transportState.value
        val context = (state as? PlaybackTransportState.SessionState)?.context
        val engine = ProcessDeathTestEngine.current
        return Bundle().apply {
            putString("state", state::class.simpleName)
            putLong("session", context?.sessionId?.value ?: 0)
            putString("origin", context?.startOrigin?.name)
            putLong("transitions", coordinator.lifecycleCheckpoint.value.latestTransitionSequence)
            putInt("starts", engine?.startCount?.get() ?: 0)
            putInt("stops", engine?.stopCount?.get() ?: 0)
            putBoolean("focusHeld", engine?.focusHeld ?: false)
        }
    }

    private fun providerContext() = checkNotNull(context)
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}
