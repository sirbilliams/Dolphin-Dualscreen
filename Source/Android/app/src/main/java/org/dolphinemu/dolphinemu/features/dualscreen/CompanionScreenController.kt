// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import org.dolphinemu.dolphinemu.NativeLibrary

/**
 * Drives the companion (bottom) screen while a game is emulated.
 *
 * Every [POLL_MS] it checks which game is running. If a [CompanionGame] supports it, the
 * controller tells the core which memory to watch, opens a [CompanionPresentation] on the
 * secondary display, and feeds it the latest memory snapshot. For other games the bottom screen
 * is left alone.
 */
class CompanionScreenController(private val activity: Activity) : DisplayManager.DisplayListener {
    private val handler = Handler(Looper.getMainLooper())
    private val displayManager = activity.getSystemService(DisplayManager::class.java)

    private var running = false
    private var gameId: String? = null
    private var game: CompanionGame? = null
    private var presentation: CompanionPresentation? = null
    private var lastSerial = 0
    private var loggedDisplays = false
    private var noDisplayLogged = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                poll()
            } catch (e: Exception) {
                CompanionLog.e("Companion screen update failed", e)
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        displayManager.registerDisplayListener(this, handler)
        handler.post(tick)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        displayManager.unregisterDisplayListener(this)
        dismiss()
        gameId = null
        game = null
        try {
            DualScreenNative.setWatches(null)
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    private fun poll() {
        val id = currentGameId()
        if (id != gameId) {
            gameId = id
            game = id?.let { CompanionGames.forGameId(it) }
            CompanionLog.i("Game: ${id ?: "none"}; companion: ${game?.javaClass?.simpleName ?: "none"}")
            DualScreenNative.setWatches(game?.buildWatches()?.toIntArray())
            lastSerial = 0
            dismiss()
        }

        val g = game ?: return
        val p = presentation ?: show(g) ?: return
        val snapshot = MemorySnapshot(DualScreenNative.getSnapshot())
        if (snapshot.serial != lastSerial) {
            lastSerial = snapshot.serial
            p.view?.update(snapshot)
        }
    }

    private fun currentGameId(): String? = try {
        if (NativeLibrary.IsRunning()) NativeLibrary.GetCurrentGameID().ifEmpty { null } else null
    } catch (_: IllegalStateException) {
        null
    }

    private fun show(g: CompanionGame): CompanionPresentation? {
        val display = findCompanionDisplay() ?: return null
        val p = CompanionPresentation(activity, display) { g.createView(it) }
        return try {
            p.show()
            presentation = p
            CompanionLog.i("Showing companion screen on display ${display.displayId} (${display.name})")
            p
        } catch (e: WindowManager.InvalidDisplayException) {
            CompanionLog.e("Couldn't show on display ${display.displayId}", e)
            null
        }
    }

    private fun dismiss() {
        presentation?.let {
            try {
                it.dismiss()
            } catch (_: Exception) {
            }
        }
        presentation = null
    }

    private fun findCompanionDisplay(): Display? {
        val own = ownDisplayId()
        val all = displayManager.displays
        if (!loggedDisplays) {
            loggedDisplays = true
            CompanionLog.i("Game is on display $own. Displays:")
            for (d in all) {
                val metrics = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
                CompanionLog.i(
                    "  #${d.displayId} '${d.name}' ${metrics.widthPixels}x${metrics.heightPixels} " +
                            "flags=0x${Integer.toHexString(d.flags)} state=${d.state}"
                )
            }
        }
        val presentationDisplays =
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val choice = presentationDisplays.firstOrNull { it.displayId != own }
            ?: all.firstOrNull { it.displayId != own }
        if (choice == null && !noDisplayLogged) {
            noDisplayLogged = true
            CompanionLog.i("No second display found")
        }
        return choice
    }

    private fun ownDisplayId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.displayId
        }

    override fun onDisplayAdded(displayId: Int) {
        CompanionLog.i("Display added: $displayId")
        loggedDisplays = false
        noDisplayLogged = false
    }

    override fun onDisplayRemoved(displayId: Int) {
        CompanionLog.i("Display removed: $displayId")
        if (presentation?.display?.displayId == displayId) dismiss()
    }

    override fun onDisplayChanged(displayId: Int) {}

    companion object {
        private const val POLL_MS = 100L
    }
}
