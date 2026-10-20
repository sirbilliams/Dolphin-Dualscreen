// SPDX-License-Identifier: GPL-2.0-or-later
// Ported from Bill's Pluto for Android (sirbilliams/Pluto-Android, app/src/main/java/io/homelab/pluto/ui/Theme.kt),
// so Dolphin Dualscreen's themes look exactly like Pluto's. Keep in step with Pluto when its themes change.

package org.dolphinemu.dolphinemu.features.pluto

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.dolphinemu.dolphinemu.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The suite's eight themes, with the desktop Pluto's colours and skies (themes.py). */
data class Glow(val x: Float, val y: Float, val r: Float, val color: Color, val alpha: Float)

data class Sky(
    val stops: List<Color>, val glows: List<Glow>, val stars: Float, val starAlpha: Float, val meteors: Boolean,
    val drift: Pair<Float, Float>, val tints: List<Color>, val meteor: Color = Color.White, val meteorHead: Color = Color.White,
    val curtains: Color? = null, val curtains2: Color? = null, val sun: Pair<Color, Color>? = null, val grid: Color? = null,
    val shafts: Color? = null, val bubbles: Boolean = false, val horizon: Boolean = false,
    val swooshes: List<Pair<Color, Float>> = emptyList(), val ocean: Boolean = false, val shaftStrength: Float = 1f,
)

data class Palette(
    val bg: Color, val bar: Color, val panel: Color, val card: Color, val text: Color, val dim: Color, val head: Color,
    val atext: Color, val accent: Color, val accent2: Color, val ok: Color, val warn: Color, val danger: Color,
    val star: Color, val line: Color, val light: Boolean,
    /** A theme's own glass (Abyss's is more solid, so text stays easy to read over the light of the sea), or -1. */
    val glass: Float = -1f,
) {
    /** The desktop's "ink": what tints soft buttons, chips and key caps (white on dark themes, deep blue on Daylight). */
    val ink: Color get() = if (light) Color(0xFF1B1E3A) else Color.White
    /** How solid glass is, and how bright its sheen (style.css's glass_a and sheen_a). */
    val glassA: Float get() = if (glass >= 0f) glass else if (light) .78f else .62f
    val sheenA: Float get() = if (light) .55f else .10f
}

data class PlutoTheme(val key: String, val name: String, val blurb: String, val c: Palette, val sky: Sky)

private fun c(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

private fun pal(bg: String, bar: String, panel: String, card: String, text: String, dim: String, head: String, atext: String,
                accent: String, accent2: String, ok: String, warn: String, danger: String, star: String, line: String, light: Boolean = false) =
    Palette(c(bg), c(bar), c(panel), c(card), c(text), c(dim), c(head), c(atext), c(accent), c(accent2), c(ok), c(warn), c(danger), c(star), c(line), light)

object Themes {
    val all: List<PlutoTheme> = listOf(
        PlutoTheme("spaced-out", "Spaced Out", "The original: deep space, violet and cyan, drifting stars.",
            pal("#05060f", "#050614", "#101434", "#121638", "#e9ebff", "#b8bde6", "#ffffff", "#cfc4ff", "#7c5cff", "#4aa8ff", "#6bf0c0", "#ffc56b", "#e0466b", "#ffd36b", "#a0aaff"),
            Sky(listOf(c("#05060f"), c("#090b1e"), c("#0e0a25")), listOf(Glow(.84f, .08f, .55f, c("#784dff"), .22f), Glow(.06f, .98f, .5f, c("#1aa6f2"), .14f), Glow(.45f, .55f, .35f, c("#d94dbf"), .05f)),
                1f, 1f, true, -3f to .8f, listOf(c("#ffffff"), c("#ffffff"), c("#c7d6ff"), c("#ffebd1"), c("#ccf2ff")))),
        PlutoTheme("daylight", "Daylight", "Light mode: a pale morning sky in lavender and peach.",
            pal("#eef1fb", "#f7f8ff", "#ffffff", "#ffffff", "#2a2d45", "#565b80", "#15172b", "#5b45d6", "#6a4cf5", "#2f8fe8", "#13a37a", "#b8750c", "#d6335a", "#c98a12", "#3c4380", light = true),
            Sky(listOf(c("#e9eeff"), c("#f5f1ff"), c("#fff4ec")), listOf(Glow(.86f, .06f, .55f, c("#8c73ff"), .2f), Glow(.05f, .95f, .55f, c("#4da6ff"), .16f), Glow(.55f, .6f, .4f, c("#ffb380"), .14f)),
                .5f, .85f, true, -2.5f to .5f, listOf(c("#6b57eb"), c("#eb7a61"), c("#4785f5"), c("#9e61e6")), c("#735cf2"), c("#5c3ddb"))),
        PlutoTheme("midnight", "Midnight", "Dark mode: quiet graphite, soft blue, slow-drifting stars.",
            pal("#0d0e11", "#0f1013", "#1a1c21", "#1c1e24", "#e3e5ea", "#a7adbb", "#ffffff", "#b4c2ff", "#5b7cfa", "#6aa6ff", "#5fd3a0", "#e9b25c", "#e5484d", "#e9b25c", "#9aa3b5"),
            Sky(listOf(c("#0d0e11"), c("#111317"), c("#15171c")), listOf(Glow(.85f, .05f, .6f, c("#7385d9"), .06f)),
                .6f, .75f, true, -1.6f to .3f, listOf(c("#ffffff"), c("#d9e6ff")), c("#9eb3f2"))),
        PlutoTheme("aurora", "Aurora", "Northern lights: green and teal curtains over a polar night.",
            pal("#020d10", "#021317", "#06242a", "#072a30", "#e2fbf3", "#a9dccd", "#ffffff", "#9df2d2", "#1fc796", "#3aa9e0", "#8af5b5", "#ffcf6b", "#ff5d7a", "#ffcf6b", "#8fe3d0"),
            Sky(listOf(c("#021014"), c("#031a1f"), c("#0a1230")), listOf(Glow(.2f, 0f, .7f, c("#26f299"), .2f), Glow(.8f, .1f, .55f, c("#1abfd9"), .15f), Glow(.6f, 1f, .5f, c("#8c59ff"), .09f)),
                1f, 1f, true, -2f to .4f, listOf(c("#ffffff"), c("#ccffeb"), c("#d9f2ff")), c("#73ffbf"), curtains = c("#33ffa6"), curtains2 = c("#2ad4c8"), bubbles = true)),
        PlutoTheme("supernova", "Supernova", "A star blowing apart: ember reds, molten orange, gold.",
            pal("#120404", "#140504", "#2a0d08", "#2e0f0a", "#ffece2", "#e8b9a6", "#ffffff", "#ffc9a8", "#ff6a2b", "#ffb23d", "#8af5b5", "#ffd36b", "#ff4d6a", "#ffd36b", "#ffb08a"),
            Sky(listOf(c("#120404"), c("#200806"), c("#2e0c07")), listOf(Glow(.78f, .18f, .6f, c("#ff5a1f"), .28f), Glow(.78f, .18f, .22f, c("#ffc23d"), .3f), Glow(.1f, .95f, .5f, c("#b3122e"), .18f)),
                .8f, .95f, true, -2.6f to 1.2f, listOf(c("#ffffff"), c("#ffe0b8"), c("#ffc28a"), c("#ff9e7a")), c("#ff8c40"), c("#ffebb3"))),
        PlutoTheme("synthwave", "Synthwave", "A striped neon sun sinking into a glowing pink grid.",
            pal("#0d0221", "#0d0221", "#1e0a3c", "#22104a", "#f8e9ff", "#d9b3e8", "#ffffff", "#ffb3e6", "#ff3cac", "#7a5cff", "#6bf0c0", "#ffd36b", "#ff4d6a", "#ffd36b", "#ff8fd8"),
            Sky(listOf(c("#0d0221"), c("#1a0540"), c("#2a0845")), listOf(Glow(.5f, .62f, .45f, c("#ff3cac"), .22f), Glow(.1f, .1f, .5f, c("#2b86c5"), .14f)),
                .8f, .9f, true, -4f to 0f, listOf(c("#ffffff"), c("#ffd9f2"), c("#d9e6ff")), c("#ff59cc"), c("#bfffff"), sun = c("#ffd36b") to c("#ff3cac"), grid = c("#ff3cac"))),
        PlutoTheme("abyss", "Abyss", "Deep sea: light from far above, rising bubbles, kelp and jellyfish, or plankton stars.",
            pal("#00121a", "#00141d", "#052530", "#062a36", "#dff8ff", "#9fcfd8", "#ffffff", "#9ff0fa", "#18b6c9", "#3a7bff", "#8af5b5", "#ffd36b", "#ff5d7a", "#ffd36b", "#7fe3ef").copy(glass = .74f),
            // under the sea (the desktop's "ocean"): the surface's light, the sea floor, kelp, jellyfish, fish, bubbles
            Sky(listOf(c("#04465a"), c("#011c2a"), c("#00070b")), listOf(Glow(.5f, -.18f, .85f, c("#5fe3f0"), .24f), Glow(.16f, .02f, .4f, c("#a6f2ff"), .1f), Glow(.9f, 1f, .5f, c("#2a5cff"), .08f)),
                .32f, .42f, false, 1.4f to 7.5f, listOf(c("#d8f3f5"), c("#a8dce2"), c("#eefcff")), shafts = c("#c4f7ff"), bubbles = true, ocean = true, shaftStrength = 1.55f)),
        PlutoTheme("aero", "Space Frutiger Aero", "Glass and gloss over a blue planet: aqua and lime light, bubbles drifting by.",
            pal("#041633", "#05204a", "#0b3566", "#0c3a70", "#eef9ff", "#a8d3ec", "#ffffff", "#9ff0ff", "#16aee6", "#76d84c", "#8af5b5", "#ffd36b", "#e8473b", "#ffe27a", "#9fe3ff"),
            Sky(listOf(c("#030f2c"), c("#06306a"), c("#0a5a92")), listOf(Glow(.82f, .1f, .55f, c("#5fd8ff"), .2f), Glow(.12f, .3f, .45f, c("#2f7bff"), .16f), Glow(.5f, 1.05f, .6f, c("#3fe0f0"), .22f)),
                .6f, .85f, true, -2.2f to .4f, listOf(c("#ffffff"), c("#d6f6ff"), c("#bdf1ff"), c("#e6ffd6")), c("#7ff0ff"), c("#ffffff"),
                horizon = true, swooshes = listOf(c("#7ff0ff") to 1f, c("#9be85a") to .75f, c("#ffffff") to .45f), bubbles = true)),
    )

    /** The desktop's typeface (Inter, as GNOME's Adwaita Sans is), in the weights Pluto uses: 200 for the clocks and the
     *  brand, 300 for big titles, 600–800 for buttons, kickers and names. Game titles in other scripts fall back to
     *  Android's own fonts. */
    val inter = FontFamily(
        Font(R.font.inter_extralight, FontWeight.ExtraLight), Font(R.font.inter_light, FontWeight.Light),
        Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold), Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    )

    /** Material's text styles, all in Inter. */
    val type: Typography = Typography().let { t ->
        fun f(x: TextStyle) = x.copy(fontFamily = inter)
        Typography(displayLarge = f(t.displayLarge), displayMedium = f(t.displayMedium), displaySmall = f(t.displaySmall),
            headlineLarge = f(t.headlineLarge), headlineMedium = f(t.headlineMedium), headlineSmall = f(t.headlineSmall),
            titleLarge = f(t.titleLarge), titleMedium = f(t.titleMedium), titleSmall = f(t.titleSmall),
            bodyLarge = f(t.bodyLarge), bodyMedium = f(t.bodyMedium), bodySmall = f(t.bodySmall),
            labelLarge = f(t.labelLarge), labelMedium = f(t.labelMedium), labelSmall = f(t.labelSmall))
    }

    /** Abyss's other sky, as on the desktop: among its stars (plankton rising, glowing streaks) instead of under the sea. */
    private val abyssStars = Sky(listOf(c("#00202c"), c("#00141d"), c("#000a10")), listOf(Glow(.5f, -.1f, .65f, c("#4fd6e6"), .18f), Glow(.9f, 1f, .5f, c("#2a5cff"), .1f)),
        .8f, .8f, true, .6f to -6f, listOf(c("#7fffe0"), c("#5fe0ef"), c("#b3fff2")), meteor = c("#4dffd9"), meteorHead = c("#ccfff2"), shafts = c("#a6f2ff"))

    /** A theme by its key (Abyss with the sky chosen in Themes: "abyss_scene" ocean, the default, or stars). */
    fun get(key: String): PlutoTheme {
        val t = all.firstOrNull { it.key == key } ?: all[0]
        // (Pluto lets Abyss's sky be chosen in its Themes screen; Dolphin Dualscreen uses the default, under the sea)
        val stars = false
        return if (stars) t.copy(sky = abyssStars) else t
    }

    fun scheme(t: PlutoTheme): ColorScheme {
        val p = t.c
        return if (p.light) lightColorScheme(primary = p.accent, secondary = p.accent2, background = p.bg, surface = p.panel,
            onBackground = p.text, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.dim, error = p.danger, outline = p.line.copy(alpha = .4f))
        else darkColorScheme(primary = p.accent, secondary = p.accent2, background = p.bg, surface = p.panel,
            onBackground = p.text, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.dim, error = p.danger, outline = p.line.copy(alpha = .3f),
            onPrimary = Color.White)
    }
}
