// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Luigi's Mansion companion screen's own look (it follows the game, not the app's theme): a stormy night over the
 * mansion. Midnight violet sky, slow fog, dust in the air, lightning now and then, frosted violet glass panels and a
 * spectral green-blue glow for Luigi's place on the map.
 */
object MansionStyle {
    val skyTop = Color.rgb(14, 9, 30)
    val skyMid = Color.rgb(20, 12, 40)
    val skyBottom = Color.rgb(6, 4, 12)
    val fogViolet = Color.rgb(120, 92, 230)
    val fogTeal = Color.rgb(48, 190, 170)

    val glass = Color.argb(178, 30, 21, 56)
    val glassEdge = Color.argb(50, 196, 180, 255)
    val text = Color.rgb(239, 234, 255)
    val dim = Color.rgb(170, 160, 210)
    val faint = Color.rgb(110, 100, 150)

    val spectral = Color.rgb(125, 249, 214)
    val spectralDeep = Color.rgb(43, 217, 169)
    val heart = Color.rgb(255, 107, 138)
    val ghost = Color.rgb(216, 204, 255)
    val gold = Color.rgb(255, 209, 102)
    val lightning = Color.rgb(220, 214, 255)

    private class Mote(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

    private val motes = Random(23).let { r ->
        List(46) { Mote(r.nextFloat(), r.nextFloat(), .6f + r.nextFloat() * 1.6f, .006f + r.nextFloat() * .018f, r.nextFloat() * 6.28f) }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** How bright a lightning flash is at [t] seconds: two quick flickers every 17 seconds, otherwise 0. */
    fun lightning(t: Float): Float {
        val p = t % 17f
        return when {
            p < .08f -> 1f - p / .08f * .3f
            p < .16f -> .15f
            p < .24f -> .75f
            p < .9f -> .75f * (1f - (p - .24f) / .66f)
            else -> 0f
        }
    }

    /** The night sky, fog and dust behind everything. */
    fun drawBackdrop(c: Canvas, w: Float, h: Float, t: Float, density: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(skyTop, skyMid, skyBottom), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)

        // Fog: three soft clouds drifting slowly across.
        fog(c, w * (.25f + .12f * sin(t * .05f)), h * .22f, max(w, h) * .55f, fogViolet, .16f)
        fog(c, w * (.8f + .1f * sin(t * .04f + 2f)), h * .65f, max(w, h) * .5f, fogTeal, .09f)
        fog(c, w * (.45f + .15f * sin(t * .03f + 4f)), h * 1.02f, max(w, h) * .6f, fogViolet, .12f)
        paint.shader = null

        // Dust in the air, rising and swaying.
        for (m in motes) {
            val y = ((m.y - t * m.speed) % 1f + 1f) % 1f
            val x = m.x + .01f * sin(t * .7f + m.phase)
            val a = (.25f + .25f * sin(t * 1.3f + m.phase)) * (.4f + .6f * y)
            paint.color = Color.argb((a * 255).toInt().coerceIn(0, 255), 220, 214, 255)
            c.drawCircle(x * w, y * h, m.r * density * .8f, paint)
        }

        // Lightning washes the whole screen briefly.
        val flash = lightning(t)
        if (flash > 0f) {
            paint.color = Color.argb((flash * 46).toInt(), Color.red(lightning), Color.green(lightning), Color.blue(lightning))
            c.drawRect(0f, 0f, w, h, paint)
        }
    }

    private fun fog(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        paint.shader = RadialGradient(x, y, r, intArrayOf(withAlpha(color, a), withAlpha(color, a * .4f), Color.TRANSPARENT),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, paint)
    }

    /** A frosted violet panel with a thin light edge (and a brighter edge when [lit]). */
    fun drawGlass(c: Canvas, r: RectF, corner: Float, density: Float, lit: Boolean = false) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = glass
        c.drawRoundRect(r, corner, corner, paint)
        // a faint sheen across the top, as Pluto's glass has
        paint.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * .45f,
            Color.argb(22, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, corner, corner, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (if (lit) 1.6f else 1f) * density
        paint.color = if (lit) withAlpha(spectral, .55f) else glassEdge
        c.drawRoundRect(r, corner, corner, paint)
        paint.style = Paint.Style.FILL
    }

    /** Luigi's place on the map: a spectral orb with a ring breathing out from it. */
    fun drawMarker(c: Canvas, x: Float, y: Float, size: Float, t: Float, density: Float) {
        val phase = (t % 1.6f) / 1.6f
        paint.shader = RadialGradient(x, y, size * 3.2f, intArrayOf(withAlpha(spectral, .45f), withAlpha(spectralDeep, .12f), Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, size * 3.2f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * density
        paint.color = withAlpha(spectral, (1f - phase) * .85f)
        c.drawCircle(x, y, size * (1.1f + phase * 1.9f), paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(x, y, size * .95f, paint)
        paint.color = spectral
        c.drawCircle(x, y, size * (.68f + .06f * sin(phase * 2 * PI.toFloat())), paint)
    }

    // --- Icons (simple original drawings) -----------------------------------------------------------------------

    fun drawHeart(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val p = Path()
        p.moveTo(cx, cy + s * .42f)
        p.cubicTo(cx - s * .95f, cy - s * .15f, cx - s * .5f, cy - s * .78f, cx, cy - s * .3f)
        p.cubicTo(cx + s * .5f, cy - s * .78f, cx + s * .95f, cy - s * .15f, cx, cy + s * .42f)
        paint.color = color
        c.drawPath(p, paint)
    }

    /** A plain little ghost: round head, wavy hem, two eyes. */
    fun drawGhost(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, eyes: Int) {
        val w = s * .62f
        val top = cy - s * .5f
        val hem = cy + s * .42f
        val p = Path()
        p.moveTo(cx - w, hem)
        p.lineTo(cx - w, cy - s * .05f)
        p.arcTo(RectF(cx - w, top, cx + w, top + w * 2), 180f, 180f)
        p.lineTo(cx + w, hem)
        val waves = 3
        val step = 2 * w / waves
        for (i in 0 until waves) {
            val x0 = cx + w - i * step
            p.quadTo(x0 - step / 4, hem - s * .14f, x0 - step / 2, hem)
            p.quadTo(x0 - step * 3 / 4, hem + s * .12f, x0 - step, hem)
        }
        p.close()
        paint.color = color
        c.drawPath(p, paint)
        paint.color = eyes
        c.drawOval(RectF(cx - w * .5f, cy - s * .2f, cx - w * .18f, cy + s * .05f), paint)
        c.drawOval(RectF(cx + w * .18f, cy - s * .2f, cx + w * .5f, cy + s * .05f), paint)
    }

    /** A stack of two coins. */
    fun drawCoins(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, edge: Int) {
        for ((dx, dy) in listOf(-.18f to .16f, .14f to -.12f)) {
            val x = cx + dx * s
            val y = cy + dy * s
            paint.color = edge
            c.drawOval(RectF(x - s * .38f, y - s * .26f + s * .08f, x + s * .38f, y + s * .26f + s * .08f), paint)
            paint.color = color
            c.drawOval(RectF(x - s * .38f, y - s * .26f, x + s * .38f, y + s * .26f), paint)
        }
    }

    /** An old door key: a ring bow and a toothed bit. */
    fun drawKey(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, density: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * .14f
        c.drawCircle(cx - s * .28f, cy, s * .2f, paint)
        paint.style = Paint.Style.FILL
        c.drawRect(cx - s * .1f, cy - s * .06f, cx + s * .48f, cy + s * .06f, paint)
        c.drawRect(cx + s * .28f, cy, cx + s * .36f, cy + s * .22f, paint)
        c.drawRect(cx + s * .4f, cy, cx + s * .48f, cy + s * .16f, paint)
    }

    /** The waiting screen's scene: a moon, and a dark old house with a few lit windows on a hill. */
    fun drawMansion(c: Canvas, w: Float, h: Float, t: Float) {
        val base = h * .74f
        val moonR = min(w, h) * .09f
        val mx = w * .72f
        val my = h * .2f
        paint.shader = RadialGradient(mx, my, moonR * 4f, intArrayOf(Color.argb(70, 230, 225, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(mx, my, moonR * 4f, paint)
        paint.shader = null
        paint.color = Color.rgb(232, 228, 250)
        c.drawCircle(mx, my, moonR, paint)
        paint.color = skyMid
        c.drawCircle(mx + moonR * .45f, my - moonR * .2f, moonR * .9f, paint)

        // the hill
        val hill = Path()
        hill.moveTo(0f, base + h * .05f)
        hill.quadTo(w * .5f, base - h * .06f, w, base + h * .04f)
        hill.lineTo(w, h)
        hill.lineTo(0f, h)
        hill.close()
        paint.color = Color.rgb(9, 6, 18)
        c.drawPath(hill, paint)

        // the house: a wide body, a tall middle tower, two side gables
        val hw = min(w * .34f, h * .3f)
        val cx = w * .45f
        val top = base - hw * .9f
        val house = Path()
        house.moveTo(cx - hw, base)
        house.lineTo(cx - hw, top + hw * .25f)
        house.lineTo(cx - hw * .7f, top)                     // left gable
        house.lineTo(cx - hw * .4f, top + hw * .25f)
        house.lineTo(cx - hw * .22f, top + hw * .25f)
        house.lineTo(cx - hw * .22f, top - hw * .3f)
        house.lineTo(cx, top - hw * .62f)                    // tower roof
        house.lineTo(cx + hw * .22f, top - hw * .3f)
        house.lineTo(cx + hw * .22f, top + hw * .25f)
        house.lineTo(cx + hw * .4f, top + hw * .25f)
        house.lineTo(cx + hw * .7f, top)                     // right gable
        house.lineTo(cx + hw, top + hw * .25f)
        house.lineTo(cx + hw, base)
        house.close()
        paint.color = Color.rgb(5, 3, 10)
        c.drawPath(house, paint)

        // lit windows, flickering a little like candles
        val windows = listOf(-.72f to .45f, -.5f to .45f, .5f to .45f, .72f to .45f, -.08f to .02f, -.72f to .72f, .62f to .72f)
        for ((i, wpos) in windows.withIndex()) {
            val (fx, fy) = wpos
            val lit = .65f + .35f * sin(t * (2.1f + i * .37f) + i)
            val ww = hw * .1f
            val x = cx + fx * hw
            val y = top + fy * hw
            paint.color = Color.argb((lit * 255).toInt().coerceIn(0, 255), 255, 205, 120)
            c.drawRect(x - ww / 2, y, x + ww / 2, y + ww * 1.4f, paint)
        }
    }

    fun withAlpha(color: Int, a: Float) =
        Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
