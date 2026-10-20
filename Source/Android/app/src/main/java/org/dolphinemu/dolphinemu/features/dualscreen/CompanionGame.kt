// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen

import android.content.Context
import android.view.View
import org.dolphinemu.dolphinemu.features.dualscreen.luigismansion.LuigisMansionCompanion

/** A game that has something to show on the companion screen. */
interface CompanionGame {
    /** Game IDs (e.g. "GLME01") this companion supports. */
    val gameIds: Set<String>

    /** The memory this game's screen needs, in the order [CompanionView.update] expects. */
    fun buildWatches(): WatchList

    /** Creates the screen. [context] belongs to the companion display. */
    fun createView(context: Context): CompanionView
}

/** The view shown on the companion display. Called on the main thread. */
abstract class CompanionView(context: Context) : View(context) {
    abstract fun update(snapshot: MemorySnapshot)
}

object CompanionGames {
    private val all: List<CompanionGame> = listOf(LuigisMansionCompanion)

    fun forGameId(gameId: String): CompanionGame? = all.firstOrNull { gameId in it.gameIds }
}
