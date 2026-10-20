// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager

/**
 * Shows a [CompanionView] on a secondary display (the AYN Thor's bottom screen).
 *
 * The window is never focusable, so controller buttons and keys keep going to the game on the
 * main screen. Touch still works on the companion display.
 */
class CompanionPresentation(
    outerContext: Context,
    display: Display,
    private val createView: (Context) -> CompanionView
) : Presentation(outerContext, display) {
    var view: CompanionView? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        val v = createView(context)
        view = v
        setContentView(v)
    }
}
