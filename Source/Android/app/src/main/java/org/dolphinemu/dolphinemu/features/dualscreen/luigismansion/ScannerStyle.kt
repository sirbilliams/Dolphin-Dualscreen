// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The spectral scanner: an original ghost-hunting handheld drawn around the Luigi's Mansion companion screen.
 * A chunky plum-and-gunmetal body with screws, a speaker grille and a status lamp; a teal phosphor screen with
 * scanlines, a slow sweep and a little flicker; segmented gauges and a rolling treasure counter below.
 */
object ScannerStyle {
    // Body
    val bodyTop = Color.rgb(58, 46, 78)
    val bodyBottom = Color.rgb(30, 24, 42)
    val bodyEdge = Color.argb(70, 220, 205, 255)
    val bezel = Color.rgb(10, 8, 14)
    val screw = Color.rgb(120, 110, 140)
    val engrave = Color.rgb(170, 158, 196)

    // Phosphor screen
    val screenBg = Color.rgb(3, 22, 19)
    val phosphor = Color.rgb(125, 255, 216)
    val phosphorDim = Color.rgb(46, 140, 116)
    val phosphorFaint = Color.rgb(20, 72, 60)

    // Gauges
    val amber = Color.rgb(255, 196, 92)
    val alarm = Color.rgb(255, 92, 110)
    val lampGreen = Color.rgb(120, 255, 170)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Turns a colour picture into the screen's teal phosphor (by its brightness). */
    val phosphorFilter: ColorMatrixColorFilter = run {
        val l = floatArrayOf(.299f, .587f, .114f)
        val tint = floatArrayOf(.49f, 1f, .85f)
        ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            l[0] * tint[0], l[1] * tint[0], l[2] * tint[0], 0f, 0f,
            l[0] * tint[1], l[1] * tint[1], l[2] * tint[1], 0f, 0f,
            l[0] * tint[2], l[1] * tint[2], l[2] * tint[2], 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }

    /** The handheld's body filling [r], with screws in the corners and a soft edge light. */
    fun drawBody(c: Canvas, r: RectF, density: Float) {
        val corner = min(r.width(), r.height()) * .07f
        paint.style = Paint.Style.FILL
        // a drop shadow on the night behind it
        paint.color = Color.argb(120, 0, 0, 0)
        c.drawRoundRect(RectF(r.left + 3 * density, r.top + 6 * density, r.right + 3 * density, r.bottom + 6 * density), corner, corner, paint)
        paint.shader = LinearGradient(0f, r.top, 0f, r.bottom, bodyTop, bodyBottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, corner, corner, paint)
        // a sheen along the top edge, as moulded plastic catches light
        paint.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * .12f, Color.argb(40, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, corner, corner, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = bodyEdge
        c.drawRoundRect(r, corner, corner, paint)
        paint.style = Paint.Style.FILL

        val inset = corner * .55f
        for ((x, y) in listOf(r.left + inset to r.top + inset, r.right - inset to r.top + inset,
            r.left + inset to r.bottom - inset, r.right - inset to r.bottom - inset)) {
            drawScrew(c, x, y, max(3.5f * density, corner * .16f))
        }
    }

    private fun drawScrew(c: Canvas, x: Float, y: Float, rad: Float) {
        paint.color = Color.argb(140, 0, 0, 0)
        c.drawCircle(x + rad * .15f, y + rad * .2f, rad, paint)
        paint.shader = RadialGradient(x - rad * .3f, y - rad * .3f, rad * 1.4f, Color.rgb(170, 160, 190), screw, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rad, paint)
        paint.shader = null
        paint.color = Color.rgb(60, 52, 76)
        paint.strokeWidth = rad * .28f
        paint.style = Paint.Style.STROKE
        c.drawLine(x - rad * .6f, y - rad * .25f, x + rad * .6f, y + rad * .25f, paint)
        paint.style = Paint.Style.FILL
    }

    /** The top strip: a speaker grille on the left and a status lamp that blinks while scanning. */
    fun drawTopStrip(c: Canvas, r: RectF, t: Float, scanning: Boolean, density: Float) {
        // grille: short horizontal slots
        paint.color = Color.argb(160, 8, 6, 12)
        val slotH = max(2f * density, r.height() * .1f)
        val slotW = r.width() * .16f
        for (i in 0 until 4) {
            val y = r.top + r.height() * .2f + i * slotH * 1.9f
            c.drawRoundRect(RectF(r.left, y, r.left + slotW, y + slotH), slotH / 2, slotH / 2, paint)
        }
        // lamp
        val on = !scanning || (t % 1.2f) < .8f
        val lx = r.right - r.height() * .4f
        val ly = r.centerY()
        val lr = max(3.5f * density, r.height() * .16f)
        val col = if (scanning) lampGreen else amber
        if (on) {
            paint.shader = RadialGradient(lx, ly, lr * 3.5f, withAlpha(col, .55f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(lx, ly, lr * 3.5f, paint)
            paint.shader = null
        }
        paint.color = if (on) col else withAlpha(col, .25f)
        c.drawCircle(lx, ly, lr, paint)
    }

    /** The recessed screen: a black bezel and the dark phosphor glass. Returns the glass's rectangle. */
    fun drawScreenWell(c: Canvas, r: RectF, density: Float): RectF {
        val corner = min(r.width(), r.height()) * .05f
        paint.color = bezel
        c.drawRoundRect(r, corner, corner, paint)
        // inner shadow on the bezel's top edge
        paint.shader = LinearGradient(0f, r.top, 0f, r.top + 10 * density, Color.argb(160, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, corner, corner, paint)
        paint.shader = null
        val pad = max(6f * density, min(r.width(), r.height()) * .03f)
        val glass = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        paint.shader = RadialGradient(glass.centerX(), glass.centerY(), max(glass.width(), glass.height()) * .7f,
            Color.rgb(6, 34, 29), screenBg, Shader.TileMode.CLAMP)
        c.drawRoundRect(glass, corner * .6f, corner * .6f, paint)
        paint.shader = null
        return glass
    }

    /**
     * What's drawn over the screen's content: scanlines, a slow sweep band, the curved glass's vignette and
     * reflection, and a flicker; during lightning, the picture breaks up for a moment.
     */
    fun drawScreenGlass(c: Canvas, glass: RectF, t: Float, density: Float, lightning: Float) {
        val corner = min(glass.width(), glass.height()) * .03f
        // scanlines
        paint.color = Color.argb(46, 0, 0, 0)
        val step = max(2f, 2.4f * density)
        var y = glass.top
        while (y < glass.bottom) {
            c.drawRect(glass.left, y, glass.right, y + step * .45f, paint)
            y += step
        }
        // sweep: a soft bright band travelling down every few seconds
        val period = 4.5f
        val f = (t % period) / period
        val sy = glass.top + glass.height() * (f * 1.3f - .15f)
        val band = glass.height() * .12f
        paint.shader = LinearGradient(0f, sy - band, 0f, sy, Color.TRANSPARENT, withAlpha(phosphor, .10f), Shader.TileMode.CLAMP)
        c.drawRect(glass.left, max(glass.top, sy - band), glass.right, min(glass.bottom, sy), paint)
        paint.shader = null
        // vignette
        paint.shader = RadialGradient(glass.centerX(), glass.centerY(), max(glass.width(), glass.height()) * .72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 0, 8, 6)), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(glass, corner, corner, paint)
        // reflection across the upper left
        paint.shader = LinearGradient(glass.left, glass.top, glass.left + glass.width() * .5f, glass.top + glass.height() * .5f,
            Color.argb(26, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRoundRect(glass, corner, corner, paint)
        paint.shader = null
        // flicker
        val flicker = .03f + .03f * sin(t * 47f) * sin(t * 13f)
        paint.color = Color.argb((max(0f, flicker) * 255).toInt(), 0, 0, 0)
        c.drawRoundRect(glass, corner, corner, paint)
        // interference during lightning: a few torn bright lines
        if (lightning > .05f) {
            val r = Random((t * 20).toInt())
            paint.color = withAlpha(phosphor, .35f * lightning)
            repeat(6) {
                val ly = glass.top + r.nextFloat() * glass.height()
                c.drawRect(glass.left, ly, glass.right, ly + (1 + r.nextFloat() * 3) * density, paint)
            }
        }
    }

    /** Static for the "no signal" screen. */
    fun drawStatic(c: Canvas, glass: RectF, t: Float, density: Float) {
        val r = Random((t * 24).toInt())
        val dot = max(2f, 2.2f * density)
        var y = glass.top
        while (y < glass.bottom) {
            var x = glass.left
            while (x < glass.right) {
                val v = r.nextFloat()
                if (v > .55f) {
                    paint.color = withAlpha(phosphor, (v - .55f) * .5f)
                    c.drawRect(x, y, x + dot, y + dot, paint)
                }
                x += dot * 2
            }
            y += dot * 2
        }
    }

    /** Luigi's place on the scanner: a pulsing blip inside corner brackets. */
    fun drawBlip(c: Canvas, x: Float, y: Float, size: Float, t: Float, density: Float) {
        val phase = (t % 1.3f) / 1.3f
        paint.shader = RadialGradient(x, y, size * 3f, withAlpha(phosphor, .5f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, size * 3f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = withAlpha(phosphor, (1f - phase) * .9f)
        c.drawCircle(x, y, size * (1f + phase * 2.4f), paint)
        // brackets
        val b = size * 2.1f
        val arm = size * .8f
        paint.color = phosphor
        paint.strokeWidth = 2f * density
        for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
            val cx = x + sx * b
            val cy = y + sy * b
            c.drawLine(cx, cy, cx - sx * arm, cy, paint)
            c.drawLine(cx, cy, cx, cy - sy * arm, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = phosphor
        c.drawCircle(x, y, size * .7f, paint)
    }

    /** A row of [count] segments, [lit] of them lit in [color] (the rest faint). */
    fun drawSegments(c: Canvas, r: RectF, count: Int, lit: Int, color: Int, density: Float) {
        val gap = max(1.5f * density, r.width() * .012f)
        val w = (r.width() - gap * (count - 1)) / count
        for (i in 0 until count) {
            val x = r.left + i * (w + gap)
            val on = i < lit
            paint.color = if (on) color else withAlpha(color, .13f)
            c.drawRoundRect(RectF(x, r.top, x + w, r.bottom), 1.5f * density, 1.5f * density, paint)
            if (on) {
                paint.color = Color.argb(60, 255, 255, 255)
                c.drawRect(x, r.top, x + w, r.top + r.height() * .3f, paint)
            }
        }
    }

    /** A signal meter: five rising bars; during lightning some drop out. */
    fun drawSignal(c: Canvas, r: RectF, t: Float, lightning: Float, density: Float) {
        val bars = 5
        val gap = r.width() * .08f
        val w = (r.width() - gap * (bars - 1)) / bars
        val rnd = Random((t * 8).toInt())
        val strength = if (lightning > .05f) 1 + rnd.nextInt(3) else 4 + (if (sin(t * .7f) > .6f) 1 else 0)
        for (i in 0 until bars) {
            val h = r.height() * (.3f + .7f * (i + 1) / bars)
            val x = r.left + i * (w + gap)
            paint.color = if (i < strength) phosphor else withAlpha(phosphor, .15f)
            c.drawRect(x, r.bottom - h, x + w, r.bottom, paint)
        }
    }

    /** A small engraved label on the body. */
    fun engraveLabel(): Int = engrave

    fun withAlpha(color: Int, a: Float) =
        Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
