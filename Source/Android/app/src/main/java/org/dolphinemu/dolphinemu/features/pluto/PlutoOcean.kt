// SPDX-License-Identifier: GPL-2.0-or-later
// Ported from Bill's Pluto for Android (sirbilliams/Pluto-Android, app/src/main/java/io/homelab/pluto/ui/Ocean.kt),
// so Dolphin Dualscreen's themes look exactly like Pluto's. Keep in step with Pluto when its themes change.

package org.dolphinemu.dolphinemu.features.pluto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Abyss under the sea, as on the desktop (its ocean.py): the rippling light of the surface far above, the sea floor
 * with its rock, sea fan and sponges, kelp swaying, two moon jellies pulsing slowly upwards, bubbles rising in streams
 * from the floor (growing as they rise) with now and then a burst, and a school of small fish passing by. Everything
 * is a function of time, so with motion off it's a still picture. The pieces are drawn once (on a worker, off the
 * screen's thread) and kept as pictures; each frame only places them. The desktop's sizes are in its points: here
 * they're dp.
 */
object Ocean {
    private const val TAU = (2 * PI).toFloat()
    private const val JELLY_FRAMES = 12
    private const val TILE = 760f
    private const val JELLY_W = 76f
    private const val JELLY_H = 150f
    private const val FISH_W = 22f
    private const val FISH_H = 10f
    private const val KELP_W = 44f
    private const val KELP_H = 54f
    private const val CAUSTIC_W = 384
    private const val CAUSTIC_H = 192
    private const val BURST_EVERY = 13f
    private const val SCHOOL_EVERY = 34f
    private const val LOOSE = 26
    /** Kelp clumps at the sides: (x, height, phase). */
    private val KELP = listOf(Triple(.030f, 1.30f, 0f), Triple(.058f, .95f, 1.3f), Triple(.092f, 1.55f, 2.1f), Triple(.125f, 1.05f, 3.3f),
        Triple(.865f, 1.20f, .7f), Triple(.900f, 1.60f, 2.8f), Triple(.935f, 1.00f, 4.0f), Triple(.970f, 1.35f, 5.1f))
    /** Bubble streams from the floor: (x, how many). */
    private val VENTS = listOf(.075f to 16, .27f to 10, .66f to 12, .92f to 17)

    private class Pieces(val d: Float, val bubble: Bitmap, val jelly: List<Bitmap>, val fish: Bitmap, val kelp: List<List<Bitmap>>,
                         val surface: Bitmap, val caustic: List<Bitmap>)

    @Volatile private var pieces: Pieces? = null
    @Volatile private var bed: Triple<Int, Int, Bitmap>? = null
    private val making = AtomicBoolean(false)
    private val makingBed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "pluto-ocean").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())

    /** Bumped when pictures are ready: the sky reads it, so it redraws then (even when nothing moves). */
    var gen by mutableIntStateOf(0)
        private set

    private fun ready(d: Float): Pieces? {
        val p = pieces
        if (p != null && p.d == d) return p
        if (making.compareAndSet(false, true)) worker.execute {
            try { pieces = make(d) } catch (e: Throwable) { android.util.Log.w("PlutoSky", "the sea's pictures weren't made", e) }
            finally { making.set(false); main.post { gen++ } }
        }
        return null
    }

    private fun seabed(w: Int, h: Int, d: Float): Bitmap? {
        val b = bed
        if (b != null && b.first == w && b.second == h) return b.third
        if (w > 0 && h > 0 && makingBed.compareAndSet(false, true)) worker.execute {
            try { bed = Triple(w, h, paintSeabed(w, h, d)) } catch (e: Throwable) { android.util.Log.w("PlutoSky", "the sea floor wasn't made", e) }
            finally { makingBed.set(false); main.post { gen++ } }
        }
        return b?.third                                     // (the old size's, until the new one is ready)
    }

    // ------------------------------------------------------------------------------- drawing, frame by frame

    /** Behind the stars: the sea floor, then the surface's light (two nets of light drifting across each other) and
     *  the bright underside of the surface along the top. */
    fun back(s: DrawScope, t: Float) = with(s) {
        @Suppress("UNUSED_VARIABLE") val g = gen
        val w = size.width; val h = size.height; val d = density
        val p = ready(d)
        val bedPic = seabed(w.toInt(), h.toInt(), d)
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            bedPic?.let { bm -> val top = h * BED_TOP; c.drawBitmap(bm, null, RectF(0f, top, w, top + bm.height * (w / bm.width)), null) }
            if (p == null) return@drawIntoCanvas
            val tile = TILE * d
            val ch = h * .58f
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for ((n, speed, alpha) in listOf(Triple(0, 7f, .55f), Triple(1, -5f, .45f))) {
                val off = ((t * speed * d) % tile + tile) % tile
                val bob = 6f * d * sin(t * (.21f + .09f * n) + n)
                paint.alpha = (255 * alpha * (.8f + .2f * sin(t * .45f + n * 2))).toInt().coerceIn(0, 255)
                var x = off - tile
                while (x < w) { c.drawBitmap(p.caustic[n], null, RectF(x, -10 * d + bob, x + tile, -10 * d + bob + ch), paint); x += tile }
            }
            val off = ((t * 11 * d) % tile + tile) % tile
            var x = off - tile
            val y = -6 * d + 2 * d * sin(t * .8f)
            while (x < w) { c.drawBitmap(p.surface, null, RectF(x, y, x + tile, y + 22 * d), null); x += tile }
        }
    }

    /** In front of the stars: kelp, jellyfish, fish and bubbles. */
    fun front(s: DrawScope, t: Float, fish: Boolean = true) = with(s) {
        @Suppress("UNUSED_VARIABLE") val g = gen
        val w = size.width; val h = size.height; val d = density
        val p = ready(d) ?: return@with
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            // kelp, rooted on the sea floor, each strand four pieces that sway one on the other
            KELP.forEachIndexed { i, (fx, size, ph) ->
                c.save()
                c.translate(w * fx, h * .965f)
                c.scale(size * .9f, size * .9f)
                for (k in 0 until 4) {
                    val sway = (2.4f + 2.6f * k) * sin(t * .55f + ph + k * .55f) + 1.2f * sin(t * .23f + ph * 1.7f)
                    c.rotate(sway)
                    c.drawBitmap(p.kelp[i][k], null, RectF(-KELP_W / 2 * d, (-KELP_H - 2) * d, KELP_W / 2 * d, 2 * d), paint)
                    c.translate(0f, -KELP_H * .94f * d)
                }
                c.restore()
            }
            // jellyfish drift up and come round again
            for ((fx, speed, size, period, ph, alpha) in listOf(J(.22f, 10f, 1.35f, 3.4f, 0f, .82f), J(.76f, 6.5f, .85f, 4.1f, .45f, .58f))) {
                val jh = JELLY_H * size * d; val jw = JELLY_W * size * d
                val span = h + jh * 2.2f
                val pp = ((t / period + ph) % 1f + 1f) % 1f
                val y = h + jh - (((t * speed * d + ph * span * 1.7f) % span + span) % span) - 5 * d * sin(TAU * pp)
                val x = w * fx + 18 * d * sin(t * .11f + ph * 9)
                val frame = (pp * JELLY_FRAMES).toInt() % JELLY_FRAMES
                c.save()
                c.translate(x, y)
                c.rotate(4 * sin(t * .17f + ph * 5))
                paint.alpha = (255 * alpha).toInt()
                c.drawBitmap(p.jelly[frame], null, RectF(-jw / 2, -jh * .18f, jw / 2, jh * .82f), paint)
                c.restore()
            }
            paint.alpha = 255
            if (fish) fishes(c, p, w, h, t, d, paint)
            bubbles(c, p, w, h, t, d, paint)
        }
    }

    private data class J(val fx: Float, val speed: Float, val size: Float, val period: Float, val ph: Float, val alpha: Float)

    /** A steady pseudo-random 0-1 from numbers (the same every frame). */
    private fun hash(vararg xs: Float): Float {
        var s = 0f
        xs.forEachIndexed { i, x -> s += x * (12.9898f + 7.233f * i) }
        val v = sin(s.toDouble()) * 43758.5453
        return (v - floor(v)).toFloat()
    }

    private fun bubbles(c: Canvas, p: Pieces, w: Float, h: Float, t: Float, d: Float, paint: Paint) {
        val k = 34f / 32f
        fun put(x: Float, y: Float, r: Float) { val rr = r * k * d; c.drawBitmap(p.bubble, null, RectF(x - rr, y - rr, x + rr, y + rr), paint) }
        val rise = h + 60 * d
        VENTS.forEachIndexed { v, (vx, n) ->                // streams of bubbles from the sea floor
            for (i in 0 until n) {
                val spd = (42 + 26 * hash(v.toFloat(), i.toFloat())) * d
                val y = h * .95f - ((t * spd + i * rise / n + hash(i.toFloat(), v.toFloat(), 3f) * 40 * d) % rise)
                val u = max(0f, 1 - y / h)
                val r = (1.6f + 5.2f * hash(v.toFloat(), i.toFloat(), 7f).pow(1.6f)) * (1 + .5f * u)     // they grow as they rise
                val x = w * vx + sin(t * (1.6f + hash(i.toFloat(), v.toFloat())) + i * 1.7f) * (1.5f + 10 * u) * d + (hash(v.toFloat(), i.toFloat(), 9f) - .5f) * 10 * d
                put(x, y, r)
            }
        }
        for (i in 0 until LOOSE) {                          // loose ones, anywhere
            val spd = (20 + 18 * hash(i.toFloat(), 11f)) * d
            val y = h + 20 * d - ((t * spd + hash(i.toFloat(), 13f) * rise) % rise)
            val r = 1.2f + 3.4f * hash(i.toFloat(), 17f).pow(1.4f)
            val x = w * hash(i.toFloat(), 19f) + sin(t * (.9f + hash(i.toFloat(), 23f)) + i) * 7 * d
            put(x, y, r)
        }
        val b = floor(t / BURST_EVERY)                      // now and then, a burst of them from the floor
        for (kb in listOf(b - 1, b)) {
            val start = kb * BURST_EVERY + 4 * hash(kb, 31f)
            val x0 = w * (.18f + .64f * hash(kb, 32f))
            for (i in 0 until 22) {
                val age = t - start - i * .06f
                if (age < 0) continue
                val spd = (80 + 60 * hash(kb, i.toFloat(), 33f)) * d
                val y = h * .95f - age * spd
                if (y < -20 * d) continue
                val u = max(0f, 1 - y / h)
                val r = (2f + 6.5f * hash(kb, i.toFloat(), 34f).pow(1.8f)) * (1 + .4f * u)
                val x = x0 + (hash(kb, i.toFloat(), 35f) - .5f) * 50 * d * min(1f, age * .8f) + sin(t * 2.2f + i) * 4 * d * u
                put(x, y, r)
            }
        }
    }

    /** Now and then a school of small fish crosses, in a loose shoal that ripples as it swims. */
    private fun fishes(c: Canvas, p: Pieces, w: Float, h: Float, t: Float, d: Float, paint: Paint) {
        val k = floor(t / SCHOOL_EVERY)
        val start = k * SCHOOL_EVERY + 8 + 6 * hash(k, 1f)
        val speed = (46 + 14 * hash(k, 2f)) * d
        val span = w + 360 * d
        val pos = (t - start) * speed
        if (pos < 0 || pos > span) return
        val right = hash(k, 3f) > .5f
        val y0 = h * (.22f + .36f * hash(k, 4f))
        val n = 9 + (7 * hash(k, 5f)).toInt()
        for (i in 0 until n) {
            val dx = -(hash(k, i.toFloat(), 6f) * 150 * d)
            val dy = (hash(k, i.toFloat(), 7f) - .5f) * 70 * d
            var x = -180 * d + pos + dx
            x = if (right) x else w - x
            val y = y0 + dy + 6 * d * sin(t * 1.3f + i * .7f + x / d * .01f)
            val size = .75f + .5f * hash(k, i.toFloat(), 8f)
            c.save()
            c.translate(x, y)
            if (!right) c.scale(-1f, 1f)
            c.rotate(4 * sin(t * 2.4f + i))
            paint.alpha = (255 * (.55f + .25f * hash(k, i.toFloat(), 9f))).toInt()
            val fw = FISH_W * size * d / 2; val fh = FISH_H * size * d / 2
            c.drawBitmap(p.fish, null, RectF(-fw, -fh, fw, fh), paint)
            c.restore()
        }
        paint.alpha = 255
    }

    // ------------------------------------------------------------------------------- the pieces, drawn once

    private fun rgba(r: Float, g: Float, b: Float, a: Float) =
        android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), (r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255))

    private fun canvas(wdp: Float, hdp: Float, d: Float): Pair<Bitmap, Canvas> {
        val bm = Bitmap.createBitmap(max(1, (wdp * d).toInt() + 1), max(1, (hdp * d).toInt() + 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.scale(d, d)
        return bm to c
    }

    private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private fun stroke(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private fun make(d: Float): Pieces {
        val bubble = canvas(34f, 34f, d).let { (bm, c) -> drawBubble(c, 17f, 17f, 16f); bm }
        val jelly = List(JELLY_FRAMES) { i -> canvas(JELLY_W, JELLY_H, d).let { (bm, c) -> drawJelly(c, i / JELLY_FRAMES.toFloat()); bm } }
        val fish = canvas(FISH_W, FISH_H, d).let { (bm, c) -> drawFish(c); bm }
        val kelp = List(KELP.size) { i -> List(4) { k -> canvas(KELP_W, KELP_H + 4, d).let { (bm, c) -> drawKelpPiece(c, i, k); bm } } }
        val surface = canvas(TILE, 22f, d).let { (bm, c) -> drawSurface(c, TILE, 22f); bm }
        val caustic = List(2) { n -> causticBitmap(CAUSTIC_W, CAUSTIC_H, 3f + n * 4.7f) }
        return Pieces(d, bubble, jelly, fish, kelp, surface, caustic)
    }

    /** A bubble under water: clear in the middle, a bright silvery rim, a highlight up top. */
    private fun drawBubble(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r, fill().apply {
            shader = RadialGradient(cx, cy, r, intArrayOf(rgba(.6f, .95f, 1f, .03f), rgba(.6f, .95f, 1f, .09f), rgba(.82f, 1f, 1f, .42f), rgba(.82f, 1f, 1f, 0f)),
                floatArrayOf(0f, .72f, .92f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawCircle(cx, cy, r * .92f, stroke(max(.5f, r * .08f)).apply { color = rgba(.86f, 1f, 1f, .72f) })
        c.save()
        c.translate(cx - r * .36f, cy - r * .40f)
        c.rotate(-40f)
        c.scale(r * .34f, r * .18f)
        c.drawCircle(0f, 0f, 1f, fill().apply { shader = RadialGradient(0f, 0f, 1f, rgba(1f, 1f, 1f, .95f), rgba(1f, 1f, 1f, 0f), Shader.TileMode.CLAMP) })
        c.restore()
        val o = RectF(cx - r * .7f, cy - r * .7f, cx + r * .7f, cy + r * .7f)
        c.drawArc(o, Math.toDegrees(.25).toFloat(), Math.toDegrees(1.0).toFloat(), false, stroke(max(.4f, r * .07f)).apply { color = rgba(.8f, 1f, 1f, .35f) })
    }

    /** The light of the surface on the water: the bright, shifting net that sunlight makes through waves (Dave
     *  Hoskins' tileable water caustic, worked out once per pixel), pale cyan, brightest at the top and fading below. */
    private fun causticBitmap(w: Int, h: Int, t0: Float): Bitmap {
        val px = IntArray(w * h)
        val inten = .005
        val ts = DoubleArray(5) { n -> t0 * (1.0 - 3.5 / (n + 1)) }
        val tau = 2 * PI
        for (y in 0 until h) {
            val py = y.toDouble() / h * tau - 250.0
            val fade = max(0.0, 1.0 - y.toDouble() / h).pow(1.35)
            for (x in 0 until w) {
                val pxx = x.toDouble() / w * tau - 250.0
                var ix = pxx; var iy = py; var cc = 1.0
                for (tt in ts) {
                    val nx = pxx + cos(tt - ix) + sin(tt + iy)
                    val ny = py + sin(tt - iy) + cos(tt + ix)
                    ix = nx; iy = ny
                    val a = sin(ix + tt) / inten; val b = cos(iy + tt) / inten
                    if (a != 0.0 && b != 0.0) cc += 1.0 / sqrt((pxx / a).pow(2) + (py / b).pow(2))
                }
                cc = 1.17 - (cc / 5).pow(1.4)
                val v = (min(1.0, abs(cc).pow(8) * 1.25) * fade).let { if (it.isNaN()) 0.0 else it }
                px[y * w + x] = android.graphics.Color.argb((255 * v).toInt().coerceIn(0, 255), 199, 247, 255)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** The underside of the surface: a bright, wavy band along the top that repeats every [w]. */
    private fun drawSurface(c: Canvas, w: Float, h: Float) {
        fun edge(x: Float) = h * .55f + h * .22f * sin(TAU * 2 * x / w) + h * .10f * sin(TAU * 7 * x / w + 1)
        val p = Path().apply {
            moveTo(-12f, 0f)
            var x = -12f
            while (x <= w + 12) { lineTo(x, edge(x)); x += 4 }
            lineTo(w + 12, 0f); close()
        }
        c.drawPath(p, fill().apply { shader = LinearGradient(0f, 0f, 0f, h, rgba(.82f, .98f, 1f, .5f), rgba(.82f, .98f, 1f, .04f), Shader.TileMode.CLAMP) })
        val e = Path().apply { moveTo(-12f, edge(-12f)); var x = -12f; while (x <= w + 12) { lineTo(x, edge(x)); x += 4 } }
        c.drawPath(e, stroke(1f).apply { color = rgba(.9f, 1f, 1f, .45f) })
    }

    /** A moon jelly at point [p] (0-1) of its pulse: a glowing bell that squeezes and relaxes, four rings inside,
     *  frilly arms and fine tentacles trailing below. */
    private fun drawJelly(c: Canvas, p: Float) {
        val sq = .5f - .5f * cos(TAU * p)                   // 0 relaxed, 1 squeezed
        val cx = JELLY_W / 2; val top = 14f
        val bw = 27 * (1 - .17f * sq); val bh = 23 * (1 + .12f * sq)
        val base = top + bh
        c.drawPaint(fill().apply { shader = RadialGradient(cx, top + bh * .6f, 44f, rgba(.45f, 1f, .92f, .2f), rgba(.45f, 1f, .92f, 0f), Shader.TileMode.CLAMP) })
        for (j in 0 until 9) {                              // fine tentacles from the rim
            val x0 = cx - bw * .9f + j * (bw * 1.8f / 8)
            val length = (78 + 16 * sin(j * 1.7f)) * (1 - .12f * sq)
            val path = Path().apply { moveTo(x0, base); var s = 0f; while (s < length) { lineTo(x0 + sin(s * .075f - TAU * p + j) * (1.5f + s * .06f), base + s); s += 3 } }
            c.drawPath(path, stroke(.7f).apply { shader = LinearGradient(0f, base, 0f, base + length, rgba(.65f, 1f, .95f, .5f), rgba(.65f, 1f, .95f, 0f), Shader.TileMode.CLAMP) })
        }
        for (j in listOf(-1, 1)) {                          // the frilly arms in the middle
            val x0 = cx + j * 4
            val path = Path().apply { moveTo(x0, base - 2); var s = 0f; while (s < 58) { lineTo(x0 + j * 1.5f + sin(s * .16f - TAU * p * 1.3f + j) * (1.6f + s * .035f), base - 2 + s); s += 2 } }
            c.drawPath(path, stroke(2.4f).apply { shader = LinearGradient(0f, base, 0f, base + 58, rgba(.85f, 1f, .95f, .42f), rgba(.85f, 1f, .95f, 0f), Shader.TileMode.CLAMP) })
        }
        val bell = Path().apply {                           // the bell, with a scalloped rim
            moveTo(cx - bw, base)
            cubicTo(cx - bw * 1.03f, top + bh * .22f, cx - bw * .55f, top, cx, top)
            cubicTo(cx + bw * .55f, top, cx + bw * 1.03f, top + bh * .22f, cx + bw, base)
            val n = 8
            for (k in 0 until n) {
                val x1 = cx + bw - (k + 1) * 2 * bw / n
                val xm = (cx + bw - k * 2 * bw / n + x1) / 2
                cubicTo(xm + bw / n * .5f, base + 2.2f, xm - bw / n * .5f, base + 2.2f, x1, base)
            }
            close()
        }
        c.drawPath(bell, fill().apply {
            shader = RadialGradient(cx, top + bh * .4f, bw * 1.15f, intArrayOf(rgba(.8f, 1f, .96f, .46f), rgba(.38f, .92f, 1f, .16f), rgba(.55f, 1f, 1f, .32f)),
                floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawPath(bell, stroke(1f).apply { color = rgba(.82f, 1f, .97f, .72f) })
        val ring = stroke(1.1f).apply { color = rgba(.92f, 1f, .86f, .45f) }
        for (k in 0 until 4) {                              // the four rings
            val a = TAU * k / 4 + .4f
            val ox = cx + cos(a) * 6.5f; val oy = top + bh * .55f + sin(a) * 2.6f
            c.drawOval(RectF(ox - 3.6f, oy - 2.3f, ox + 3.6f, oy + 2.3f), ring)
        }
        val shine = RectF(cx - bw * .15f - bh * .7f, top + bh * .85f - bh * .7f, cx - bw * .15f + bh * .7f, top + bh * .85f + bh * .7f)
        c.drawArc(shine, 180f * 1.18f, 180f * .24f, false, stroke(1.6f).apply { color = rgba(1f, 1f, 1f, .35f) })
    }

    /** A small fish facing right, in silhouette with a pale belly and a glint of an eye. */
    private fun drawFish(c: Canvas) {
        val p = Path().apply {
            moveTo(21f, 5f); cubicTo(18f, 1.2f, 10f, .6f, 6f, 4f); lineTo(.5f, .8f); lineTo(2.2f, 5f); lineTo(.5f, 9.2f); lineTo(6f, 6f)
            cubicTo(10f, 9.4f, 18f, 8.8f, 21f, 5f); close()
        }
        c.drawPath(p, fill().apply {
            shader = LinearGradient(0f, 1f, 0f, 9f, intArrayOf(rgba(.10f, .36f, .44f, 1f), rgba(.20f, .55f, .62f, 1f), rgba(.55f, .85f, .88f, 1f)),
                floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawCircle(17.3f, 4.2f, .8f, fill().apply { color = rgba(.85f, 1f, 1f, .9f) })
    }

    /** A kelp blade along -y from (0, 0): wide in the middle, ruffled along both edges. */
    private fun ribbon(length: Float, width: Float, ruffle: Float, phase: Float): Path {
        val n = 18
        val left = ArrayList<Pair<Float, Float>>(); val right = ArrayList<Pair<Float, Float>>()
        for (i in 0..n) {
            val u = i.toFloat() / n
            val wd = width * sin(PI.toFloat() * min(1f, u * 1.08f + .04f)).pow(.7f) * .5f
            val rf = ruffle * sin(u * 19 + phase)
            left.add(-wd - rf to -length * u); right.add(wd + rf * .6f to -length * u)
        }
        return Path().apply {
            moveTo(left[0].first, left[0].second)
            for (q in left.drop(1)) lineTo(q.first, q.second)
            for (q in right.reversed()) lineTo(q.first, q.second)
            close()
        }
    }

    /** One piece of a giant kelp strand, its bottom at the bottom: the stalk, a blade growing from a small float at
     *  its base, and at the tip (k=3) the strand's last long blade. */
    private fun drawKelpPiece(c: Canvas, seed: Int, k: Int) {
        val rnd = Random(seed * 10 + k)
        fun uni(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
        val cx = KELP_W / 2
        val base = KELP_H + 2
        val dark = rgba(.02f, .17f, .19f, .95f); val mid = rgba(.05f, .29f, .29f, .92f); val light = rgba(.30f, .72f, .66f, .42f)
        val wid = 2.6f - k * .35f
        c.drawPath(Path().apply { moveTo(cx - wid / 2, base); lineTo(cx - wid / 2 * .85f, -2f); lineTo(cx + wid / 2 * .85f, -2f); lineTo(cx + wid / 2, base); close() },
            fill().apply { color = dark })
        val side = if ((seed + k) % 2 == 1) 1 else -1
        val blades = mutableListOf(listOf(base - 6 - uni(0f, 6f), side.toFloat(), uni(38f, 48f) * (1 - .06f * k), uni(.28f, .48f)))
        if (k == 3) blades.add(listOf(4f, 0f, 44f, .08f))
        for ((y, sd, ln, tilt) in blades) {
            c.save()
            c.translate(cx, y)
            c.rotate(Math.toDegrees((sd * tilt).toDouble()).toFloat())
            val blade = ribbon(ln, if (sd != 0f) 9.5f else 8f, .9f, (seed + k).toFloat())
            c.drawPath(blade, fill().apply { shader = LinearGradient(-5f, 0f, 5f, 0f, intArrayOf(dark, mid, dark), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) })
            c.drawPath(blade, stroke(.6f).apply { color = light })
            c.drawLine(0f, -2f, 0f, -ln * .9f, stroke(.8f).apply { color = rgba(.35f, .78f, .70f, .22f) })      // its midrib
            if (sd != 0f) {                                 // the float it grows from
                c.drawCircle(0f, 0f, 2.1f, fill().apply { color = rgba(.08f, .36f, .34f, .97f) })
                c.drawCircle(0f, 0f, 2.1f, stroke(.5f).apply { color = light })
            }
            c.restore()
        }
    }

    /** Where the sea floor's picture starts, down the screen. */
    private const val BED_TOP = .66f

    /** The sea floor along the bottom: dark rock with a faint light on its edge, a sea fan and tube sponges. Drawn for
     *  one screen's size; only its bottom third is kept. */
    private fun paintSeabed(w: Int, h: Int, d: Float): Bitmap {
        val top = h * BED_TOP
        val bm = Bitmap.createBitmap(w, max(1, (h - top).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.translate(0f, -top)
        val rnd = Random(5)
        fun uni(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
        val a = uni(0f, TAU); val b = uni(0f, TAU); val cc = uni(0f, TAU)
        val base = h * .95f
        fun ridge(x: Float): Float {
            val u = x / max(1, w)
            var y = base - h * .022f * (sin(u * TAU * 1.3f + a) + .6f * sin(u * TAU * 3.1f + b) + .25f * sin(u * TAU * 7.3f + cc))
            for ((mx, mh, mw) in listOf(Triple(.10f, .075f, .09f), Triple(.88f, .06f, .08f), Triple(.55f, .02f, .12f))) y -= h * mh * exp(-((u - mx) / mw).pow(2))
            return y
        }
        val step = 8 * d
        val rock = Path().apply { moveTo(0f, h.toFloat()); var x = 0f; while (x <= w + step) { lineTo(x, ridge(x)); x += step }; lineTo(w.toFloat(), h.toFloat()); close() }
        c.drawPath(rock, fill().apply { shader = LinearGradient(0f, base - h * .12f, 0f, h.toFloat(), rgba(.012f, .085f, .11f, 1f), rgba(0f, .028f, .045f, 1f), Shader.TileMode.CLAMP) })
        val rim = Path().apply { moveTo(0f, ridge(0f)); var x = 0f; while (x <= w + step) { lineTo(x, ridge(x)); x += step } }
        c.drawPath(rim, stroke(1.2f * d).apply { color = rgba(.30f, .80f, .85f, .16f) })
        val fan = stroke(1f).apply { color = rgba(.06f, .24f, .28f, .9f) }
        fun branch(x: Float, y: Float, ang: Float, ln: Float, depth: Int) {      // a sea fan: a flat, lacy spread of branches
            val x1 = x + cos(ang) * ln; val y1 = y + sin(ang) * ln
            fan.strokeWidth = max(.6f, depth * .55f) * d
            c.drawLine(x, y, x1, y1, fan)
            if (depth > 1) for (da in listOf(-.26f, .24f)) branch(x1, y1, ang + da + uni(-.08f, .08f), ln * uni(.74f, .84f), depth - 1)
        }
        val seaFan = w * .885f
        for (a0 in listOf(-.55f, -.18f, .2f, .55f)) branch(seaFan, ridge(seaFan) + 2 * d, -PI.toFloat() / 2 + a0, h * .032f, 6)
        for ((fx, n) in listOf(.115f to 4, .62f to 3)) {    // tube sponges, in clumps
            val x0 = w * fx
            for (i in 0 until n) {
                val x = x0 + (i - n / 2f) * 11 * d + uni(-2f, 2f) * d
                val tall = h * uni(.035f, .075f)
                val tw = uni(7f, 11f) * d
                val lean = uni(-.18f, .18f)
                c.save()
                c.translate(x, ridge(x) + 3 * d)
                c.rotate(Math.toDegrees(lean.toDouble()).toFloat())
                c.drawPath(Path().apply { moveTo(-tw / 2, 0f); lineTo(-tw / 2 * .86f, -tall); lineTo(tw / 2 * .86f, -tall); lineTo(tw / 2, 0f); close() },
                    fill().apply { shader = LinearGradient(-tw / 2, 0f, tw / 2, 0f, intArrayOf(rgba(.03f, .15f, .18f, 1f), rgba(.07f, .27f, .30f, 1f), rgba(.02f, .11f, .14f, 1f)), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP) })
                val mouth = RectF(-tw / 2 * .86f, -tall - 2.2f * d, tw / 2 * .86f, -tall + 2.2f * d)
                c.drawOval(mouth, fill().apply { color = rgba(0f, .05f, .07f, 1f) })
                c.drawOval(mouth, stroke(.8f * d).apply { color = rgba(.35f, .80f, .82f, .30f) })
                c.restore()
            }
        }
        val pebble = fill().apply { color = rgba(.03f, .13f, .16f, 1f) }
        repeat(14) {                                        // pebbles
            val x = uni(0f, w.toFloat()); val r = uni(2f, 6f) * d
            val y = ridge(x) + r * .4f
            c.drawArc(RectF(x - r * 1.5f, y - r, x + r * 1.5f, y + r), 180f, 180f, true, pebble)
        }
        return bm
    }
}
