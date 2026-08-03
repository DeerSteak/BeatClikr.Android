package com.bfunkstudios.beatclikr

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupPolicyInstrumentedTest {
    @Test
    fun backupRulesAllowOnlyDatabaseAndPreferences() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val legacy = includes(resources.getXml(R.xml.backup_rules))
        val modern = includes(resources.getXml(R.xml.data_extraction_rules))
        val expected = setOf(
            "database:beatclikr.db",
            "database:beatclikr.db-wal",
            "database:beatclikr.db-shm",
            "sharedpref:beatclikr_preferences.xml"
        )

        assertEquals(expected, legacy)
        assertEquals(expected, modern)
    }

    private fun includes(parser: XmlPullParser): Set<String> {
        val result = mutableSetOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "include") {
                val domain = parser.getAttributeValue(null, "domain")
                val path = parser.getAttributeValue(null, "path")
                result += "$domain:$path"
            }
            parser.next()
        }
        return result
    }
}
