package org.dolphinemu.dolphinemu.utils

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.elevation.ElevationOverlayProvider
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.pluto.PlutoSkin
import org.dolphinemu.dolphinemu.ui.main.ThemeProvider

object ThemeHelper {

    const val CURRENT_THEME = "current_theme"
    const val CURRENT_THEME_MODE = "current_theme_mode"
    const val USE_BLACK_BACKGROUNDS = "use_black_backgrounds"

    const val DEFAULT = 0
    const val MONET = 1
    const val MATERIAL_DEFAULT = 2
    const val GREEN = 3
    const val PINK = 4

    // Dolphin Dualscreen: Pluto's eight themes (see PlutoSkin), with Spaced Out as the default.
    const val DEFAULT_THEME = PlutoSkin.SPACED_OUT

    @JvmStatic
    fun setTheme(activity: AppCompatActivity) {
        // We have to use shared preferences in addition to Dolphin's settings to guarantee that the
        // requested theme id is ready before the onCreate method of any given Activity.
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
        setThemeMode(activity)
        val theme = preferences.getInt(CURRENT_THEME, DEFAULT_THEME)
        when (theme) {
            DEFAULT -> activity.setTheme(R.style.Theme_Dolphin_Main)
            MONET -> activity.setTheme(R.style.Theme_Dolphin_Main_MaterialYou)
            MATERIAL_DEFAULT -> activity.setTheme(R.style.Theme_Dolphin_Main_Material)
            GREEN -> activity.setTheme(R.style.Theme_Dolphin_Main_Green)
            PINK -> activity.setTheme(R.style.Theme_Dolphin_Main_Pink)
            else -> activity.setTheme(PlutoSkin.styleFor(theme) ?: R.style.Theme_Dolphin_Main)
        }
        // (Pluto's themes bring their own backgrounds: the black-backgrounds option is for Dolphin's themes)
        if (!PlutoSkin.isPluto(theme) && preferences.getBoolean(USE_BLACK_BACKGROUNDS, false)) {
            activity.setTheme(R.style.ThemeOverlay_Dolphin_Dark)
        }

    }

    private fun setThemeMode(activity: AppCompatActivity) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
        val theme = preferences.getInt(CURRENT_THEME, DEFAULT_THEME)
        // Pluto's themes are dark (Daylight is light) whatever the light/dark setting says.
        val themeMode = when {
            !PlutoSkin.isPluto(theme) ->
                preferences.getInt(CURRENT_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            PlutoSkin.isLight(theme) -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        activity.delegate.localNightMode = themeMode
        val windowController = WindowCompat.getInsetsController(
            activity.window, activity.window.decorView
        )
        val systemReportedThemeMode =
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> when (systemReportedThemeMode) {
                Configuration.UI_MODE_NIGHT_NO -> setLightModeSystemBars(windowController)
                Configuration.UI_MODE_NIGHT_YES -> setDarkModeSystemBars(windowController)
            }

            AppCompatDelegate.MODE_NIGHT_NO -> setLightModeSystemBars(windowController)
            AppCompatDelegate.MODE_NIGHT_YES -> setDarkModeSystemBars(windowController)
        }
    }

    private fun setLightModeSystemBars(windowController: WindowInsetsControllerCompat) {
        windowController.isAppearanceLightStatusBars = true

        // Fix for an API 26 specific bug where the navigation bar buttons would appear invisible
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            windowController.isAppearanceLightNavigationBars = true
        }
    }

    private fun setDarkModeSystemBars(windowController: WindowInsetsControllerCompat) {
        windowController.isAppearanceLightStatusBars = false
        windowController.isAppearanceLightNavigationBars = false
    }

    @JvmStatic
    fun saveTheme(activity: AppCompatActivity, themeValue: Int) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .putInt(CURRENT_THEME, themeValue).apply()
        activity.recreate()
    }

    @JvmStatic
    fun deleteThemeKey(activity: AppCompatActivity) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .remove(CURRENT_THEME).apply()
        activity.recreate()
    }

    @JvmStatic
    fun saveThemeMode(activity: AppCompatActivity, themeModeValue: Int) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .putInt(CURRENT_THEME_MODE, themeModeValue).apply()
        setThemeMode(activity)
    }

    @JvmStatic
    fun deleteThemeModeKey(activity: AppCompatActivity) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .remove(CURRENT_THEME_MODE).apply()
        setThemeMode(activity)
    }

    @JvmStatic
    fun saveBackgroundSetting(activity: AppCompatActivity, backgroundValue: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .putBoolean(USE_BLACK_BACKGROUNDS, backgroundValue).apply()
        activity.recreate()
    }

    @JvmStatic
    fun deleteBackgroundSetting(activity: AppCompatActivity) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .remove(USE_BLACK_BACKGROUNDS).apply()
        activity.recreate()
    }

    @JvmStatic
    fun resetThemePreferences(activity: AppCompatActivity, applyImmediately: Boolean = false) {
        PreferenceManager.getDefaultSharedPreferences(activity.applicationContext).edit()
            .remove(CURRENT_THEME).remove(CURRENT_THEME_MODE).remove(USE_BLACK_BACKGROUNDS).apply()
        activity.delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        activity.delegate.applyDayNight()
        if (applyImmediately) {
            activity.recreate()
        }
    }

    @JvmStatic
    fun setCorrectTheme(activity: AppCompatActivity) {
        val provider = activity as? ThemeProvider ?: return
        val currentTheme = provider.themeId
        setTheme(activity)
        if (currentTheme != provider.themeId) {
            activity.recreate()
        }
    }

    @JvmStatic
    fun setStatusBarColor(activity: AppCompatActivity, @ColorInt color: Int) {
        activity.window.statusBarColor = color
    }

    @JvmStatic
    fun enableScrollTint(
        activity: AppCompatActivity, toolbar: MaterialToolbar, appBarLayout: AppBarLayout
    ) {
        if (PlutoSkin.isActive(activity)) {
            // Over Pluto's sky the bar is clear, and frosted glass once the page scrolls under it.
            val glass = MaterialColors.getColor(appBarLayout, R.attr.plutoGlass)
            toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
                val scrolled = layout.totalScrollRange > 0 && -verticalOffset >= layout.totalScrollRange / 2
                toolbar.setBackgroundColor(if (scrolled) glass else android.graphics.Color.TRANSPARENT)
            }
            return
        }
        appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            if (layout.totalScrollRange > 0 && -verticalOffset >= layout.totalScrollRange / 2) {
                @ColorInt val color =
                    ElevationOverlayProvider(appBarLayout.context).compositeOverlay(
                        MaterialColors.getColor(appBarLayout, R.attr.colorSurface),
                        activity.resources.getDimensionPixelSize(R.dimen.elevated_app_bar).toFloat()
                    )
                toolbar.setBackgroundColor(color)
                setStatusBarColor(activity, color)
            } else {
                @ColorInt val statusBarColor = ContextCompat.getColor(
                    activity.applicationContext, android.R.color.transparent
                )
                @ColorInt val appBarColor = MaterialColors.getColor(toolbar, R.attr.colorSurface)
                toolbar.setBackgroundColor(appBarColor)
                setStatusBarColor(activity, statusBarColor)
            }
        }
    }

    @JvmStatic
    fun enableStatusBarScrollTint(activity: AppCompatActivity, appBarLayout: AppBarLayout) {
        // (under Pluto's themes the sky runs behind the status bar)
        if (PlutoSkin.isActive(activity)) return
        appBarLayout.addOnOffsetChangedListener { _: AppBarLayout, verticalOffset: Int ->
            if (-verticalOffset > 0) {
                @ColorInt val color = MaterialColors.getColor(appBarLayout, R.attr.colorSurface)
                setStatusBarColor(activity, color)
            } else {
                @ColorInt val statusBarColor = ContextCompat.getColor(
                    activity.applicationContext, android.R.color.transparent
                )
                setStatusBarColor(activity, statusBarColor)
            }
        }
    }
}
