// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen

import android.util.Log
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes companion-screen events to <user dir>/Dualscreen/log.txt (on the device:
 * Android/data/io.github.sirbilliams.dolphindualscreen/files/Dualscreen/log.txt) so problems on
 * the Thor can be diagnosed without adb.
 */
object CompanionLog {
    private const val TAG = "DolphinDualscreen"
    private const val MAX_BYTES = 256 * 1024
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var file: File? = null
    private var started = false

    /** Folder for companion-screen files, or null before Dolphin's directories are ready. */
    fun folder(): File? = try {
        File(DirectoryInitialization.getUserDirectory(), "Dualscreen").also { it.mkdirs() }
    } catch (_: IllegalStateException) {
        null
    }

    @Synchronized
    fun i(message: String) {
        Log.i(TAG, message)
        write("I", message)
    }

    @Synchronized
    fun e(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
        write("E", if (error == null) message else "$message: ${Log.getStackTraceString(error)}")
    }

    private fun write(level: String, message: String) {
        try {
            if (file == null) {
                val dir = folder() ?: return
                file = File(dir, "log.txt")
            }
            val f = file!!
            if (!started || f.length() > MAX_BYTES) {
                // Start fresh each app launch, and never let the file grow unbounded.
                f.writeText("")
                started = true
            }
            f.appendText("${timeFormat.format(Date())} $level $message\n")
        } catch (_: Exception) {
            // Logging must never break the companion screen.
        }
    }
}
