// SPDX-License-Identifier: GPL-2.0-or-later
// Ported from Bill's Pluto for Android (sirbilliams/Pluto-Android, app/src/main/java/io/homelab/pluto/ui/Sky.kt),
// so Dolphin Dualscreen's themes look exactly like Pluto's. Keep in step with Pluto when its themes change.

package org.dolphinemu.dolphinemu.features.pluto

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Seconds of movement since the screen appeared, ticking every frame; still while [moving] is off (motion off, or a
 *  game running), and carrying on from where it stopped when it's back on (nothing jumps). */
@Composable
fun rememberClock(moving: Boolean): () -> Float {
    var t by remember { mutableFloatStateOf(0f) }
    val go = moving
    LaunchedEffect(go) {
        if (!go) return@LaunchedEffect
        val from = t
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = from + (it - start) / 1e9f }
    }
    return { t }
}

private data class Star(val x: Float, val y: Float, val r: Float, val tw: Float, val tint: Int, val depth: Float)

/** The theme's sky, as on the desktop: gradient, glows, drifting stars and shooting stars, plus each theme's own
 *  (aurora curtains, the synthwave sun and grid, light shafts and bubbles under the sea, Aero's glass horizon). */
@Composable
fun SkyView(theme: PlutoTheme, moving: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberClock(moving)
    val stars = remember(theme.key) {
        val r = Random(7)
        List((220 * theme.sky.stars).toInt()) {
            Star(r.nextFloat(), r.nextFloat(), .5f + r.nextFloat() * r.nextFloat() * 1.8f, r.nextFloat() * 6.28f,
                r.nextInt(theme.sky.tints.size), .3f + r.nextFloat() * .7f)
        }
    }
    Canvas(modifier) {
        val t = clock()
        val sky = theme.sky
        drawRect(Brush.verticalGradient(0f to sky.stops[0], .55f to sky.stops[1], 1f to sky.stops[2]))
        for (g in sky.glows) {
            val c = Offset(g.x * size.width, g.y * size.height)
            drawCircle(Brush.radialGradient(listOf(g.color.copy(alpha = g.alpha), Color.Transparent), c, g.r * max(size.width, size.height)), g.r * max(size.width, size.height), c)
        }
        sky.curtains?.let { curtains(this, it, sky.curtains2 ?: it, t) }
        if (sky.shafts != null) shafts(this, sky.shafts, sky.shaftStrength, t)
        if (sky.ocean) Ocean.back(this, t + 6f)                    // (the sea floor and the surface's light; t+6: the desktop's still moment)
        if (sky.horizon) horizon(this, sky, t)
        sky.sun?.let { sun(this, it, t) }
        sky.grid?.let { grid(this, it, t) }
        for (s in stars) {
            val dx = sky.drift.first * t * 3f * s.depth
            val dy = sky.drift.second * t * 3f * s.depth
            val x = ((s.x * size.width + dx) % size.width + size.width) % size.width
            val y = ((s.y * size.height + dy) % size.height + size.height) % size.height
            val tw = .55f + .45f * sin(t * 1.7f + s.tw)
            drawCircle(sky.tints[s.tint].copy(alpha = sky.starAlpha * tw * s.depth), s.r * density * .9f, Offset(x, y))
        }
        if (sky.meteors) meteor(this, sky, t)
        if (sky.ocean) Ocean.front(this, t + 6f)                   // (kelp, jellyfish, fish and the bubbles)
        else if (sky.bubbles) bubbles(this, t, false)
    }
}

private fun curtains(d: DrawScope, a: Color, b: Color, t: Float) = with(d) {
    for (k in 0 until 3) {
        val p = Path()
        val base = size.height * (.18f + k * .07f)
        p.moveTo(0f, base)
        var x = 0f
        while (x <= size.width) {
            p.lineTo(x, base + sin(x / size.width * 6f + t * .25f + k) * size.height * .05f)
            x += size.width / 60
        }
        drawPath(p, Brush.verticalGradient(listOf((if (k % 2 == 0) a else b).copy(alpha = .22f), Color.Transparent), base - 40f, base + size.height * .35f),
            style = Stroke(size.height * .22f, cap = StrokeCap.Round))
    }
}

/** Light from far above in the deep sea, as on the desktop (starfield.py's SHAFTS): (x, width, strength, speed,
 *  phase, tilt in degrees). */
private val SHAFTS = listOf(floatArrayOf(.18f, .07f, .9f, .040f, .4f, 12f), floatArrayOf(.36f, .10f, 1f, .031f, 2f, 7f),
    floatArrayOf(.62f, .06f, .8f, .047f, 3.7f, 2f), floatArrayOf(.80f, .09f, .9f, .036f, 5.1f, -3f))

/** The sea's light shafts: soft columns hanging from the top, brightest there and fading downwards and to their
 *  sides (an elliptical glow, no hard edges); they sway, breathe and drift very slowly. */
private fun shafts(d: DrawScope, c: Color, strength: Float, t: Float) = with(d) {
    val w = size.width; val h = size.height
    val tau = 2 * PI.toFloat()
    for (sh in SHAFTS) {
        val (fx, fw, k, sp, ph) = sh
        val tilt = sh[5]
        val x = w * (fx + .012f * sin(t * sp * tau + ph))
        val breathe = .6f + .4f * (.5f + .5f * sin(t * sp * .9f * tau + ph * 1.9f))
        val a = (.13f * k * breathe * strength).coerceIn(0f, 1f)
        val hw = w * fw / 2
        val length = h * 1.15f
        withTransform({
            translate(x, -h * .04f)
            rotate(tilt + 1.5f * sin(t * sp * .7f * tau + ph), Offset.Zero)
            scale(1f, length / hw, Offset.Zero)
        }) {
            drawRect(Brush.radialGradient(0f to c.copy(alpha = a), .35f to c.copy(alpha = a * .62f), .7f to c.copy(alpha = a * .22f), 1f to c.copy(alpha = 0f),
                center = Offset.Zero, radius = hw), Offset(-hw, 0f), Size(2 * hw, hw))
        }
    }
}

/** Space Frutiger Aero's planet low in the sky (its top at 84% of the height, a bright rim and a thin green-blue
 *  atmosphere above it) and the ribbons of light sweeping across, as the desktop paints them. */
private fun horizon(d: DrawScope, sky: Sky, t: Float) = with(d) {
    val w = size.width; val h = size.height
    val r = max(w, h) * 1.35f
    val c = Offset(w * .5f, h * .84f + r)
    val R = r * 1.08f
    val e = r / R
    drawRect(Brush.radialGradient(0f to Color(0xFF010B22), (e - .06f) to Color(0xFF03224C), (e - .018f) to Color(0xFF0A4F8C),
        (e - .004f) to Color(0xFF38C8F0), e to Color(0xFFB8FBFF), (e + .006f) to Color(0xFF5FE3FF).copy(alpha = .75f),
        (e + .022f) to Color(0xFF7FDC5A).copy(alpha = .30f), (e + .05f) to Color(0xFF3FA8FF).copy(alpha = .10f), 1f to Color.Transparent,
        center = c, radius = R))
    // (start y, end y, bend) for each ribbon; its colour and strength come from the theme
    val shapes = listOf(Triple(.62f, .30f, -.18f), Triple(.70f, .44f, -.10f), Triple(.55f, .18f, -.22f))
    val k = minOf(w, h) / 810f                                   // the desktop's widths were for an 810-pixel-tall window
    sky.swooshes.forEachIndexed { i, (col, str) ->
        val (y0, y1, bend0) = shapes.getOrElse(i) { shapes.last() }
        val bend = bend0 + .015f * sin(t * .15f + i)              // (a slow breath, when things move)
        val p = Path().apply {
            moveTo(-w * .05f, h * y0)
            cubicTo(w * .30f, h * (y0 + bend), w * .62f, h * (y1 - bend * .4f), w * 1.05f, h * y1)
        }
        for ((width, a) in listOf(130f to .025f, 80f to .035f, 46f to .05f, 22f to .08f, 9f to .16f, 3f to .40f, 1.2f to .7f)) {
            val al = a * str
            drawPath(p, Brush.horizontalGradient(0f to col.copy(alpha = 0f), .35f to col.copy(alpha = al), .7f to col.copy(alpha = al * .8f), 1f to col.copy(alpha = 0f)),
                style = Stroke(maxOf(1f, width * k), cap = StrokeCap.Round))
        }
    }
}

private fun sun(d: DrawScope, cols: Pair<Color, Color>, t: Float) = with(d) {
    val r = size.minDimension * .2f
    val c = Offset(size.width * .5f, size.height * .62f)
    drawCircle(Brush.verticalGradient(listOf(cols.first, cols.second), c.y - r, c.y + r), r, c)
    // its stripes, in the sky's lowest colour (the desktop's), clipped to the sun
    clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }) {
        for (i in 0 until 6) {
            val y = c.y + r * (.1f + i * .16f)
            drawRect(Color(0xFF2A0845), Offset(c.x - r, y), Size(2 * r, (2f + i * 1.4f) * density * .6f))
        }
    }
}

/** Synthwave's floor, as the desktop draws it: from 72% down, the sky's lowest colour, faint pink lines running
 *  away to the horizon (the cross lines slide towards you when things move). */
private fun grid(d: DrawScope, col: Color, t: Float) = with(d) {
    val top = size.height * .72f
    drawRect(Color(0xFF2A0845).copy(alpha = .85f), Offset(0f, top), Size(size.width, size.height - top))
    val shift = (t * .25f) % 1f
    for (i in 1..11) {
        val f = (i - 1 + shift) / 11f + 1f / 11f
        val y = top + (size.height - top) * f * f
        if (y < size.height) drawLine(col.copy(alpha = .35f), Offset(0f, y), Offset(size.width, y), 1.2f * density * .8f)
    }
    for (i in -14..14) {
        val x0 = size.width / 2 + i * size.width * .012f
        val x1 = size.width / 2 + i * size.width * .12f
        drawLine(col.copy(alpha = .3f), Offset(x0, top), Offset(x1, size.height), 1.2f * density * .8f)
    }
}

private fun meteor(d: DrawScope, sky: Sky, t: Float) = with(d) {
    val period = 9f
    val n = floor(t / period).toInt()
    val f = (t % period) / 1.2f
    if (f > 1f) return@with
    val r = Random(n * 31 + 5)
    val x0 = size.width * (.3f + r.nextFloat() * .7f)
    val y0 = size.height * r.nextFloat() * .4f
    val len = size.width * .25f
    val hx = x0 - len * f
    val hy = y0 + len * .45f * f
    drawLine(Brush.linearGradient(listOf(Color.Transparent, sky.meteor.copy(alpha = .8f * (1 - f))), Offset(hx + 120f, hy - 54f), Offset(hx, hy)),
        Offset(hx + 120f, hy - 54f), Offset(hx, hy), 2f * density, StrokeCap.Round)
    drawCircle(sky.meteorHead.copy(alpha = 1 - f), 2.2f * density, Offset(hx, hy))
}

private fun bubbles(d: DrawScope, t: Float, sea: Boolean) = with(d) {
    if (!sea) { glassBubbles(this, t); return@with }
    val r = Random(11)
    repeat(22) {
        val x = r.nextFloat() * size.width
        val speed = 12f + r.nextFloat() * 28f
        val rad = (4f + r.nextFloat() * 8f) * density
        val y = size.height + rad - ((t * speed * density + r.nextFloat() * size.height * 2) % (size.height + rad * 2))
        val wob = sin(t * 1.3f + it) * 8f * density
        val c = Offset(x + wob, y)
        drawCircle(Color.White.copy(alpha = .10f), rad, c)
        drawCircle(Color.White.copy(alpha = .35f), rad, c, style = Stroke(1.2f * density))
        drawCircle(Color.White.copy(alpha = .55f), rad * .25f, Offset(c.x - rad * .35f, c.y - rad * .35f))
    }
}

/** The rainbow of a soap film, round a bubble's rim (the desktop's IRIDESCENT). */
private val IRIDESCENT = listOf(Color(1f, .45f, .85f), Color(.35f, .92f, 1f), Color(.62f, 1f, .45f), Color(1f, .88f, .42f),
    Color(.66f, .52f, 1f), Color(1f, .45f, .85f))

/** A coordinate bouncing between [lo] and [hi] (a ball moving at a steady speed, turning at the edges). */
private fun bounce(v: Float, lo: Float, hi: Float): Float {
    val span = (hi - lo).coerceAtLeast(1f)
    val m = ((v - lo) % (2 * span) + 2 * span) % (2 * span)
    return lo + if (m <= span) m else 2 * span - m
}

/**
 * Glass bubbles drifting about and bouncing off the edges, as on the desktop (the Vista screensaver, slower): nearly
 * clear in the middle, a rainbow film round the rim that slowly turns, a bright window-shaped highlight upper left,
 * a tiny glint and a soft glow lower right. As many and as big as the desktop's for the screen's size.
 */
private fun glassBubbles(d: DrawScope, t: Float) = with(d) {
    val rnd = Random(11)
    val wdp = size.width / density; val hdp = size.height / density
    val n = (wdp * hdp / 95000f * 2.2f).toInt().coerceIn(5, 15)
    val k = (minOf(wdp, hdp) / 800f * 1.5f).coerceIn(.45f, 1.2f)          // the desktop's sizes, for this screen
    repeat(n) { i ->
        val rad = (24f + rnd.nextFloat() * 46f) * (if (i % 4 == 0) 1.6f else 1f) * k * density
        val speed = (14f + rnd.nextFloat() * 16f) * k * density
        val ang = rnd.nextFloat() * 2 * PI.toFloat()
        val x0 = rnd.nextFloat() * size.width; val y0 = rnd.nextFloat() * size.height
        val rot0 = rnd.nextFloat() * 360f
        val spin = (8f + rnd.nextFloat() * 12f) * (if (rnd.nextBoolean()) 1 else -1)
        val c = Offset(bounce(x0 + cos(ang) * speed * t, rad, size.width - rad), bounce(y0 + (sin(ang) * speed - 4f * density) * t, rad, size.height - rad))
        // the film: a faint body, brightening to a white rim
        drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = .025f), .70f to Color(.6f, .9f, 1f, .05f), .92f to Color(.75f, .95f, 1f, .18f),
            .985f to Color.White.copy(alpha = .55f), 1f to Color.Transparent, center = c, radius = rad), rad, c)
        // the rainbow round the rim, turning slowly
        rotate(rot0 + spin * t, c) {
            val film = Brush.sweepGradient(IRIDESCENT.map { it.copy(alpha = .55f) }, c)
            drawCircle(film, rad * .86f, c, alpha = .32f, style = Stroke(rad * .22f))
            drawCircle(film, rad * .93f, c, alpha = .55f, style = Stroke(rad * .09f))
        }
        // the window-shaped highlight upper left, a glint below it, a soft glow lower right
        withTransform({ translate(c.x - rad * .36f, c.y - rad * .42f); rotate(-38f, Offset.Zero); scale(1f, .5f, Offset.Zero) }) {
            drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = .85f), .45f to Color.White.copy(alpha = .42f), 1f to Color.Transparent,
                center = Offset.Zero, radius = rad * .4f), rad * .4f, Offset.Zero)
        }
        val g = Offset(c.x - rad * .55f, c.y - rad * .12f); val gr = maxOf(1.2f * density, rad * .05f) * 2
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .8f), Color.Transparent), g, gr), gr, g)
        withTransform({ translate(c.x + rad * .42f, c.y + rad * .48f); scale(1f, .6f, Offset.Zero) }) {
            drawCircle(Brush.radialGradient(listOf(Color(.8f, 1f, 1f, .28f), Color.Transparent), Offset.Zero, rad * .3f), rad * .3f, Offset.Zero)
        }
    }
}

/** A gentle wave from 0 to 1 and back, for glows. */
fun pulse(t: Float, speed: Float = 1f) = (.5f + .5f * sin(t * speed * 2 * PI.toFloat()))
fun ease(f: Float) = (1 - cos(f.coerceIn(0f, 1f) * PI.toFloat())) / 2
fun near(a: Float, b: Float, eps: Float = .001f) = abs(a - b) < eps
