// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.pluto

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.utils.ThemeHelper

/**
 * Dolphin Dualscreen's Pluto look: Bill's Pluto front end's eight themes on Dolphin's own screens.
 *
 * The colours and fonts come from the generated styles (res/values/pluto_themes.xml); this object adds what a style
 * can't: Pluto's animated sky behind a screen, and Pluto's frosted glass on game cards and the in-game menu. Every
 * function does nothing when one of Dolphin's own themes is chosen, so Dolphin's screens need only one call each.
 */
object PlutoSkin {
    /** Dolphin's theme setting values for the Pluto themes (res/values/arrays.xml, themeValues). */
    const val SPACED_OUT = 10
    const val DAYLIGHT = 11
    const val MIDNIGHT = 12
    const val AURORA = 13
    const val SUPERNOVA = 14
    const val SYNTHWAVE = 15
    const val ABYSS = 16
    const val AERO = 17

    private const val FIRST = SPACED_OUT
    private val keys = listOf("spaced-out", "daylight", "midnight", "aurora", "supernova", "synthwave", "abyss", "aero")
    private val styles = intArrayOf(
        R.style.Theme_Dolphin_Pluto_SpacedOut, R.style.Theme_Dolphin_Pluto_Daylight,
        R.style.Theme_Dolphin_Pluto_Midnight, R.style.Theme_Dolphin_Pluto_Aurora,
        R.style.Theme_Dolphin_Pluto_Supernova, R.style.Theme_Dolphin_Pluto_Synthwave,
        R.style.Theme_Dolphin_Pluto_Abyss, R.style.Theme_Dolphin_Pluto_Aero
    )

    private const val SKY_TAG = "pluto_sky"

    fun isPluto(themeId: Int) = themeId in FIRST until FIRST + keys.size

    /** The style for a Pluto theme setting value, or null for Dolphin's own themes. */
    fun styleFor(themeId: Int): Int? = if (isPluto(themeId)) styles[themeId - FIRST] else null

    fun themeFor(themeId: Int): PlutoTheme? = if (isPluto(themeId)) Themes.get(keys[themeId - FIRST]) else null

    /** True for Daylight, Pluto's one light theme; the rest are dark whatever the system's mode. */
    fun isLight(themeId: Int) = themeFor(themeId)?.c?.light == true

    /** The theme chosen in Dolphin's settings (as ThemeHelper reads it), or null for one of Dolphin's own. */
    fun current(context: Context): PlutoTheme? = themeFor(
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getInt(ThemeHelper.CURRENT_THEME, ThemeHelper.DEFAULT_THEME)
    )

    fun isActive(context: Context) = current(context) != null

    /**
     * Puts the theme's sky behind [activity]'s content (once), and clears the solid backgrounds of [clear] and of the
     * content's root so the sky shows through.
     */
    @JvmStatic
    fun applySky(activity: Activity, vararg clear: View?) {
        val theme = current(activity) ?: return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<View>(SKY_TAG) == null) {
            val sky = ComposeView(activity).apply {
                tag = SKY_TAG
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { SkyView(theme, moving = true, modifier = Modifier.fillMaxSize()) }
            }
            content.addView(
                sky, 0,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.tag != SKY_TAG) child.setBackgroundColor(Color.TRANSPARENT)
        }
        for (v in clear) v?.setBackgroundColor(Color.TRANSPARENT)
    }

    /** Clears a view's solid background under a Pluto theme (for screens built after [applySky], like pages). */
    @JvmStatic
    fun clearBackground(view: View?) {
        if (view != null && isActive(view.context)) view.setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Pluto's glass: the theme's card colour at its glass opacity with a thin light edge; when focused, selected or
     * pressed, a brighter fill and the accent edge (the controller's place on the screen).
     */
    fun glass(context: Context, cornerDp: Float, fillAlphaBoost: Float = 0f): StateListDrawable {
        val dp = context.resources.displayMetrics.density
        // (under Dolphin's own themes there's no glass: use their nearest colours)
        val pluto = isActive(context)
        val fill = attrColor(context, if (pluto) R.attr.plutoGlass else R.attr.colorSurfaceContainerHigh)
        val edge = attrColor(context, if (pluto) R.attr.plutoGlassEdge else R.attr.colorOutlineVariant)
        val focus = attrColor(context, if (pluto) R.attr.plutoGlassFocus else R.attr.colorPrimary)

        fun shape(fillColor: Int, strokeColor: Int, strokeDp: Float) = GradientDrawable().apply {
            cornerRadius = cornerDp * dp
            setColor(fillColor)
            setStroke((strokeDp * dp).toInt().coerceAtLeast(1), strokeColor)
        }

        val boosted = withAlpha(fill, (Color.alpha(fill) / 255f + fillAlphaBoost).coerceIn(0f, 1f))
        val lit = blend(boosted, focus, .22f)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(lit, focus, 2f))
            addState(intArrayOf(android.R.attr.state_pressed), shape(lit, focus, 2f))
            addState(intArrayOf(android.R.attr.state_selected), shape(lit, focus, 2f))
            addState(intArrayOf(), shape(boosted, edge, 1f))
        }
    }

    /** A game card on the game list: a glass tile, rounded art, Inter titles in the theme's colours. */
    @JvmStatic
    fun styleGameCard(root: View, art: View?, title: TextView?, caption: TextView?) {
        val context = root.context
        if (!isActive(context)) return
        val dp = context.resources.displayMetrics.density
        root.background = glass(context, 16f)
        root.foreground = resolveDrawable(context, android.R.attr.selectableItemBackground)
        (art as? androidx.cardview.widget.CardView)?.apply {
            radius = 12 * dp
            cardElevation = 0f
            setCardBackgroundColor(withAlpha(attrColor(context, R.attr.colorSurfaceContainerHigh), .9f))
        }
        val inter = inter(context)
        title?.apply {
            typeface = Typeface.create(inter, Typeface.BOLD)
            setTextColor(attrColor(context, R.attr.plutoHead))
        }
        caption?.apply {
            typeface = inter
            setTextColor(attrColor(context, R.attr.plutoDim))
        }
    }

    /** The in-game menu: a solid-enough glass panel over the game, with the theme's light edge. */
    @JvmStatic
    fun styleIngameMenu(view: View): Boolean {
        val context = view.context
        if (!isActive(context)) return false
        val bg = attrColor(context, R.attr.colorSurfaceContainer)
        val edge = attrColor(context, R.attr.plutoGlassEdge)
        val dp = context.resources.displayMetrics.density
        view.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(bg, .94f), withAlpha(attrColor(context, android.R.attr.colorBackground), .96f))
        ).apply { setStroke((1 * dp).toInt().coerceAtLeast(1), edge) }
        return true
    }

    /** Pluto's typeface. */
    fun inter(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT

    fun attrColor(context: Context, attr: Int): Int =
        MaterialColors.getColor(context, attr, Color.MAGENTA)

    private fun resolveDrawable(context: Context, attr: Int) = TypedValue().let { tv ->
        if (context.theme.resolveAttribute(attr, tv, true) && tv.resourceId != 0)
            androidx.appcompat.content.res.AppCompatResources.getDrawable(context, tv.resourceId)
        else null
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(a: Int, b: Int, f: Float): Int = Color.argb(
        Color.alpha(a),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
    )

    /** For tinting controls that read a ColorStateList. */
    fun tint(context: Context, attr: Int): ColorStateList = ColorStateList.valueOf(attrColor(context, attr))
}
