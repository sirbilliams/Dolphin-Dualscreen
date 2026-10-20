// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.pluto.PlutoSkin
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting
import org.dolphinemu.dolphinemu.features.settings.model.Settings
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity
import org.dolphinemu.dolphinemu.ui.main.MainActivity
import org.dolphinemu.dolphinemu.ui.main.ThemeProvider
import org.dolphinemu.dolphinemu.utils.AfterDirectoryInitializationRunner
import org.dolphinemu.dolphinemu.utils.ThemeHelper

/**
 * The bottom-screen preview (⋮ menu → Bottom screen preview): each supported game's companion screen with sample
 * values, on the second display when there is one (and in a frame here either way). Checks the second display works
 * without starting a game.
 *
 * Also used by the build's screenshot run, through two optional extras:
 *  - "theme" (int): switch Dolphin's theme first, as if chosen in Settings → Interface → Theme;
 *  - "open" (string): then open "settings" (Settings → Interface) or "main" (the game list).
 */
class CompanionPreviewActivity : AppCompatActivity(), ThemeProvider {
    override var themeId: Int = 0

    private var presentation: CompanionPresentation? = null
    private var opened = false

    override fun setTheme(themeId: Int) {
        super.setTheme(themeId)
        this.themeId = themeId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        opened = savedInstanceState?.getBoolean(KEY_OPENED) ?: false

        val game = CompanionGames.supported.first()
        setContentView(buildPage(game))
        PlutoSkin.applySky(this)

        AfterDirectoryInitializationRunner().runWithLifecycle(this) {
            val wanted = intent.getIntExtra(EXTRA_THEME, -1)
            if (wanted >= 0 && wanted != IntSetting.MAIN_INTERFACE_THEME.int) {
                CompanionLog.i("Preview: switching the theme to $wanted")
                Settings().use { s ->
                    s.loadSettings()
                    IntSetting.MAIN_INTERFACE_THEME.setInt(s, wanted)
                    s.saveSettings()
                }
                ThemeHelper.saveTheme(this, wanted) // (recreates this screen in the new theme)
                return@runWithLifecycle
            }
            if (!opened) {
                opened = true
                when (intent.getStringExtra(EXTRA_OPEN)) {
                    "settings" -> SettingsActivity.launch(this, MenuTag.CONFIG_INTERFACE)
                    "main" -> startActivity(Intent(this, MainActivity::class.java))
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OPENED, opened)
    }

    override fun onStart() {
        super.onStart()
        showOnSecondDisplay(CompanionGames.supported.first())
    }

    override fun onStop() {
        super.onStop()
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
    }

    private fun buildPage(game: CompanionGame): ViewGroup {
        val dp = resources.displayMetrics.density
        val inter = PlutoSkin.inter(this)
        val head = PlutoSkin.attrColor(this, R.attr.colorOnSurface)
        val dim = PlutoSkin.attrColor(this, R.attr.colorOnSurfaceVariant)

        fun label(text: String, size: Float, color: Int, style: Int, spacing: Float = 0f) = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color)
            typeface = android.graphics.Typeface.create(inter, style)
            letterSpacing = spacing
        }

        // On a wide screen (the Thor's top screen) the words sit on the left and the companion on the right, as
        // tall as the screen allows; on a tall one they stack and scroll.
        val metrics = resources.displayMetrics
        val wide = metrics.widthPixels > metrics.heightPixels

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (wide) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }
        column.addView(label("DOLPHIN DUALSCREEN", 12f, dim, android.graphics.Typeface.BOLD, .22f))
        column.addView(label(getString(R.string.companion_preview), 28f, head, android.graphics.Typeface.NORMAL).apply {
            setPadding(0, (2 * dp).toInt(), 0, (6 * dp).toInt())
        })
        column.addView(label(statusText(game), 14f, dim, android.graphics.Typeface.NORMAL).apply {
            setLineSpacing(0f, 1.25f)
        })
        val close = MaterialButton(this).apply {
            text = getString(R.string.companion_preview_close)
            typeface = android.graphics.Typeface.create(inter, android.graphics.Typeface.BOLD)
            setOnClickListener { finish() }
        }

        // The same screen in a frame, the Thor's lower screen's shape (1080 x 1240).
        val frame = AspectFrame(this, 1080f / 1240f, fitHeight = wide).apply {
            background = PlutoSkin.glass(this@CompanionPreviewActivity, 22f, .2f).current
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            clipToOutline = true
            addView(game.createDemoView(this@CompanionPreviewActivity),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val content: ViewGroup
        if (wide) {
            column.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (20 * dp).toInt()
            })
            content = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    setMargins(0, (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
                })
            }
        } else {
            column.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (16 * dp).toInt()
            })
            column.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (16 * dp).toInt()
                gravity = Gravity.END
            })
            content = ScrollView(this).apply {
                isFillViewport = true
                addView(column)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        return FrameLayout(this).apply { addView(content) }
    }

    /** A frame kept at [ratio] (width / height): sized from its height when [fitHeight], else from its width. */
    private class AspectFrame(context: Context, private val ratio: Float, private val fitHeight: Boolean) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (fitHeight) {
                val h = MeasureSpec.getSize(heightMeasureSpec)
                val w = (h * ratio).toInt()
                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            } else {
                val w = MeasureSpec.getSize(widthMeasureSpec)
                val h = (w / ratio).toInt()
                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            }
        }
    }

    private fun secondDisplay(): Display? {
        val dm = getSystemService(DisplayManager::class.java)
        val own = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId ?: Display.DEFAULT_DISPLAY
        else @Suppress("DEPRECATION") windowManager.defaultDisplay.displayId
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.displayId != own }
            ?: dm.displays.firstOrNull { it.displayId != own }
    }

    private fun statusText(game: CompanionGame): String {
        val d = secondDisplay()
        return if (d != null)
            "${game.title} with sample values, on your second screen (display ${d.displayId}, “${d.name}”) and below."
        else
            "No second screen found, so ${game.title}'s companion is shown below only. On the Thor, check the dual-screen mode (AYN button)."
    }

    private fun showOnSecondDisplay(game: CompanionGame) {
        val d = secondDisplay() ?: return
        val p = CompanionPresentation(this, d) { game.createDemoView(it) }
        try {
            p.show()
            presentation = p
            CompanionLog.i("Preview on display ${d.displayId} (${d.name})")
        } catch (e: WindowManager.InvalidDisplayException) {
            CompanionLog.e("Preview couldn't use display ${d.displayId}", e)
        }
    }

    companion object {
        const val EXTRA_THEME = "theme"
        const val EXTRA_OPEN = "open"
        private const val KEY_OPENED = "opened"

        fun launch(context: Context) = context.startActivity(Intent(context, CompanionPreviewActivity::class.java))
    }
}
