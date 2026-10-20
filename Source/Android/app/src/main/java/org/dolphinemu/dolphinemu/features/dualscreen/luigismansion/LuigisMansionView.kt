// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.features.dualscreen.CompanionLog
import org.dolphinemu.dolphinemu.features.dualscreen.CompanionView
import org.dolphinemu.dolphinemu.features.dualscreen.MemorySnapshot
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The Luigi's Mansion bottom screen, in the game's own look (MansionStyle): a stormy night, frosted violet glass.
 *
 * Portrait (as on the Thor): the room and floor on top, the floor map with Luigi's room glowing in the middle, and
 * health / Boos / treasure / keys below. On a wide display the status column moves to the right of the map.
 *
 * Floor images are read from <user dir>/Dualscreen/LuigisMansion/ (floor_1.png, f2.png, ...). Without them the map
 * is drawn as the rooms' names at their places.
 *
 * Tap the map to look at another floor; tap the top (or wait) to follow Luigi again.
 */
class LuigisMansionView(context: Context) : CompanionView(context) {
    private val table = LmRoomTable.load(context)
    private val density = resources.displayMetrics.density
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val startedAt = SystemClock.uptimeMillis()

    private var state: LmState? = null
    private var lastRoom: LmRoom? = null
    private var lastFloorKey: String? = null

    // Manual floor browsing.
    private var manualFloor: String? = null
    private var manualUntil = 0L

    private val bitmaps = HashMap<String, Bitmap?>()
    private val imageFolder: File? = CompanionLog.folder()?.let { File(it, "LuigisMansion") }

    // Hit areas, updated in onDraw.
    private val headerRect = RectF()
    private val mapRect = RectF()

    private val inter: Typeface = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val kickerPaint = text(Typeface.BOLD).apply { letterSpacing = .22f }
    private val titlePaint = text(Typeface.NORMAL)
    private val bodyPaint = text(Typeface.NORMAL)
    private val labelPaint = text(Typeface.BOLD).apply { letterSpacing = .14f }
    private val valuePaint = text(Typeface.BOLD)

    init {
        CompanionLog.i(
            "Luigi's Mansion screen: ${table.rooms.size} rooms; map images in ${imageFolder?.path} " +
                    table.maps.values.joinToString { "${it.image}=${File(imageFolder, it.image).isFile}" }
        )
    }

    private fun text(style: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(inter, style)
    }

    private fun dp(v: Float) = v * density

    private fun seconds() = (SystemClock.uptimeMillis() - startedAt) / 1000f

    override fun update(snapshot: MemorySnapshot) = show(LuigisMansionCompanion.parse(snapshot))

    /** Shows [s] (from the game, or sample values in the preview). */
    fun show(s: LmState) {
        state = s
        if (s.inGame && s.mapId != null) {
            val room = s.roomId?.let { table.room(s.mapId, it) }
            if (room != null) lastRoom = room
            val floor = room?.floorKey ?: table.areaFloor(s.mapId)
            if (floor != null) lastFloorKey = floor
            if (room == null && s.mapId != 2) lastRoom = null
        }
        invalidate()
    }

    // --- Touch ------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        when {
            headerRect.contains(event.x, event.y) -> manualFloor = null
            mapRect.contains(event.x, event.y) -> {
                val keys = table.maps.keys.toList()
                val current = shownFloorKey() ?: keys.first()
                manualFloor = keys[(keys.indexOf(current) + 1) % keys.size]
                manualUntil = SystemClock.uptimeMillis() + MANUAL_TIMEOUT_MS
            }
        }
        invalidate()
        return true
    }

    private fun shownFloorKey(): String? {
        if (manualFloor != null && SystemClock.uptimeMillis() > manualUntil) manualFloor = null
        return manualFloor ?: lastFloorKey
    }

    // --- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = seconds()
        MansionStyle.drawBackdrop(canvas, w, h, t, density)

        val s = state
        if (s == null || (s.mapId == null && lastRoom == null)) {
            drawWaiting(canvas, w, h, t)
        } else {
            val pad = dp(12f)
            val wide = w > h * 1.15f
            val headerH = min(dp(70f), h * .17f)
            headerRect.set(0f, 0f, w, headerH + pad)
            drawHeader(canvas, s, RectF(pad, pad, w - pad, pad + headerH))

            if (wide) {
                val statusW = w * .32f
                mapRect.set(pad, pad + headerH + pad * .6f, w - statusW - pad * 1.5f, h - pad)
                drawMap(canvas, mapRect, t)
                drawStatus(canvas, s, RectF(w - statusW - pad * .5f, mapRect.top, w - pad, h - pad), columns = 1)
            } else {
                val statusH = min(h * .34f, dp(236f))
                mapRect.set(pad, pad + headerH + pad * .6f, w - pad, h - statusH - pad * .6f)
                drawMap(canvas, mapRect, t)
                drawStatus(canvas, s, RectF(pad, h - statusH, w - pad, h - pad), columns = 2)
            }
        }

        // The night keeps moving: fog, dust, lightning and the marker's glow.
        postInvalidateDelayed(FRAME_MS)
    }

    private fun drawWaiting(canvas: Canvas, w: Float, h: Float, t: Float) {
        MansionStyle.drawMansion(canvas, w, h, t)
        kickerPaint.color = MansionStyle.dim
        kickerPaint.textSize = min(w, h) * .03f
        kickerPaint.textAlign = Paint.Align.CENTER
        titlePaint.color = MansionStyle.text
        titlePaint.textSize = min(w, h) * .075f
        titlePaint.textAlign = Paint.Align.CENTER
        val y = h * .84f
        canvas.drawText("LUIGI'S MANSION", w / 2, y - titlePaint.textSize * 1.25f, kickerPaint)
        canvas.drawText("Waiting for the mansion…", w / 2, y, titlePaint)
        kickerPaint.textAlign = Paint.Align.LEFT
        titlePaint.textAlign = Paint.Align.LEFT
    }

    private fun drawHeader(canvas: Canvas, s: LmState, r: RectF) {
        val room = lastRoom
        val area = s.mapId?.let { table.areaName(it) }
        val title = when {
            room != null && (s.mapId == 2 || s.mapId == room.mapId) -> room.name
            area != null -> area
            else -> "Luigi's Mansion"
        }
        val floorLabel = lastFloorKey?.let { table.maps[it]?.label }
        val kicker = listOfNotNull(area?.takeIf { it != title } ?: "Luigi's Mansion", floorLabel)
            .joinToString("  ·  ").uppercase()

        // The chip on the right: following Luigi, browsing a floor, or paused.
        val manual = manualFloor != null && shownFloorKey() != null
        val chip = when {
            manual -> "${table.maps[shownFloorKey()]?.label} · tap to follow"
            !s.inGame -> "Paused"
            else -> "Following Luigi"
        }
        labelPaint.textSize = min(dp(12f), r.height() * .2f)
        val chipW = labelPaint.measureText(chip) + dp(22f)
        val chipH = labelPaint.textSize * 2.3f
        val chipRect = RectF(r.right - chipW, r.top + dp(2f), r.right, r.top + dp(2f) + chipH)
        val following = !manual && s.inGame
        fill.shader = null
        fill.color = if (following) MansionStyle.withAlpha(MansionStyle.spectral, .14f) else MansionStyle.glass
        canvas.drawRoundRect(chipRect, chipH / 2, chipH / 2, fill)
        if (following) {
            fill.color = MansionStyle.spectral
            canvas.drawCircle(chipRect.left + dp(11f), chipRect.centerY(), dp(3f), fill)
        }
        labelPaint.color = if (following) MansionStyle.spectral else MansionStyle.dim
        canvas.drawText(chip, chipRect.left + if (following) dp(18f) else dp(11f), chipRect.centerY() + labelPaint.textSize * .36f, labelPaint)

        val textW = r.width() - chipW - dp(12f)
        kickerPaint.color = MansionStyle.dim
        kickerPaint.textSize = min(dp(11f), r.height() * .18f)
        canvas.drawText(TextUtils.ellipsize(kicker, kickerPaint, textW, TextUtils.TruncateAt.END).toString(),
            r.left, r.top + kickerPaint.textSize + dp(2f), kickerPaint)

        titlePaint.color = MansionStyle.text
        titlePaint.typeface = Typeface.create(inter, Typeface.NORMAL)
        titlePaint.textSize = min(dp(30f), r.height() * .48f)
        val t = TextUtils.ellipsize(title, titlePaint, r.width(), TextUtils.TruncateAt.END).toString()
        canvas.drawText(t, r.left, r.bottom - dp(4f), titlePaint)
    }

    private fun drawMap(canvas: Canvas, r: RectF, t: Float) {
        val corner = dp(18f)
        MansionStyle.drawGlass(canvas, r, corner, density)

        val key = shownFloorKey()
        val map = key?.let { table.maps[it] }
        if (map == null) {
            bodyPaint.color = MansionStyle.dim
            bodyPaint.textSize = dp(14f)
            bodyPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No map for this area", r.centerX(), r.centerY(), bodyPaint)
            bodyPaint.textAlign = Paint.Align.LEFT
            return
        }

        // Fit the map's coordinate space into r, keeping its aspect ratio.
        val inset = dp(10f)
        val scale = min((r.width() - inset * 2) / map.width, (r.height() - inset * 2) / map.height)
        val dw = map.width * scale
        val dh = map.height * scale
        val ox = r.left + (r.width() - dw) / 2
        val oy = r.top + (r.height() - dh) / 2
        val dest = RectF(ox, oy, ox + dw, oy + dh)

        val bitmap = bitmapFor(map)
        if (bitmap != null) {
            canvas.save()
            val clip = Path().apply { addRoundRect(dest, dp(10f), dp(10f), Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(bitmap, null, dest, bitmapPaint)
            // dim it a little into the night, darker toward the edges
            fill.shader = LinearGradient(0f, dest.top, 0f, dest.bottom,
                intArrayOf(Color.argb(70, 10, 6, 24), Color.argb(20, 10, 6, 24), Color.argb(90, 10, 6, 24)),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(dest, fill)
            fill.shader = null
            val flash = MansionStyle.lightning(t)
            if (flash > 0f) {
                fill.color = Color.argb((flash * 60).toInt(), 230, 225, 255)
                canvas.drawRect(dest, fill)
            }
            canvas.restore()
        } else {
            drawSchematic(canvas, map, ox, oy, scale)
        }

        // Floor label in the corner, as a small glass tab.
        labelPaint.color = MansionStyle.dim
        labelPaint.textSize = dp(11f)
        val label = map.label.uppercase()
        val lw = labelPaint.measureText(label) + dp(16f)
        val tab = RectF(r.left + dp(10f), r.bottom - dp(34f), r.left + dp(10f) + lw, r.bottom - dp(10f))
        fill.color = Color.argb(170, 14, 9, 30)
        canvas.drawRoundRect(tab, dp(12f), dp(12f), fill)
        canvas.drawText(label, tab.left + dp(8f), tab.centerY() + labelPaint.textSize * .36f, labelPaint)

        // Luigi's room.
        val room = lastRoom
        if (room != null && room.hasMarker && room.map == key) {
            val cx = ox + room.x!! * scale
            val cy = oy + room.y!! * scale
            MansionStyle.drawMarker(canvas, cx, cy, max(dp(7f), min(dw, dh) * .024f), t, density)
        }
    }

    /** Draws the floor as room names at their marker positions (used when no image is present). */
    private fun drawSchematic(canvas: Canvas, map: LmMap, ox: Float, oy: Float, scale: Float) {
        val rooms = table.markersOn(map.key)
        bodyPaint.textSize = max(dp(8.5f), min(dp(11f), map.width * scale / 32f))
        bodyPaint.textAlign = Paint.Align.CENTER
        for (room in rooms) {
            val cx = ox + room.x!! * scale
            val cy = oy + room.y!! * scale
            val current = room.name == lastRoom?.name
            fill.color = if (current) MansionStyle.spectral else MansionStyle.faint
            canvas.drawCircle(cx, cy, dp(2.6f), fill)
            bodyPaint.color = if (current) MansionStyle.text else MansionStyle.dim
            canvas.drawText(shortName(room.name), cx, cy - dp(7f), bodyPaint)
        }
        bodyPaint.textAlign = Paint.Align.LEFT
    }

    private fun shortName(name: String) =
        name.removeSuffix(" Room").removeSuffix("'s").replace("Hallway", "Hall")

    /** Loads each floor image once; a missing image is remembered as null and not retried. */
    private fun bitmapFor(map: LmMap): Bitmap? {
        if (bitmaps.containsKey(map.key)) return bitmaps[map.key]
        val f = imageFolder?.let { File(it, map.image) }
        val bitmap = if (f == null || !f.isFile) null else try {
            BitmapFactory.decodeFile(f.path)
        } catch (e: Exception) {
            CompanionLog.e("Couldn't load ${f.path}", e)
            null
        }
        bitmaps[map.key] = bitmap
        return bitmap
    }

    private enum class Icon { HEART, GHOST, COINS, KEY }

    private class Tile(val label: String, val value: String?, val extra: String?, val color: Int, val icon: Icon)

    private fun drawStatus(canvas: Canvas, s: LmState, r: RectF, columns: Int) {
        val tiles = listOf(
            Tile("HEALTH", s.health?.toString(), null, MansionStyle.heart, Icon.HEART),
            Tile("BOOS", s.boos?.toString(), "/ 50", MansionStyle.ghost, Icon.GHOST),
            Tile("TREASURE", s.treasure?.let { numberFormat.format(it) }, "G", MansionStyle.gold, Icon.COINS),
            Tile("KEYS", s.keys?.size?.toString(), null, MansionStyle.spectral, Icon.KEY),
        )
        val gap = dp(10f)
        val rows = (tiles.size + columns - 1) / columns
        val keysLines = if (s.keys.isNullOrEmpty()) 0 else 2
        val keysH = if (keysLines > 0) dp(17f) * keysLines + gap else 0f
        val tileW = (r.width() - gap * (columns - 1)) / columns
        val tileH = min((r.height() - keysH - gap * (rows - 1)) / rows, dp(84f))

        for ((i, t) in tiles.withIndex()) {
            val col = i % columns
            val row = i / columns
            val left = r.left + col * (tileW + gap)
            val top = r.top + row * (tileH + gap)
            drawTile(canvas, t, RectF(left, top, left + tileW, top + tileH))
        }

        if (keysLines > 0) {
            bodyPaint.color = MansionStyle.dim
            bodyPaint.textSize = dp(12f)
            val names = s.keys!!.joinToString(" · ") { it.removeSuffix(" Key") }
            val top = r.top + rows * tileH + (rows - 1) * gap + gap + bodyPaint.textSize
            val text = TextUtils.ellipsize(names, bodyPaint, r.width() * keysLines - dp(30f), TextUtils.TruncateAt.END).toString()
            val cut = bodyPaint.breakText(text, true, r.width(), null)
            canvas.drawText(text, 0, cut, r.left + dp(4f), top, bodyPaint)
            if (cut < text.length) {
                canvas.drawText(text, cut, text.length, r.left + dp(4f), top + bodyPaint.textSize * 1.35f, bodyPaint)
            }
        }
    }

    private fun drawTile(canvas: Canvas, t: Tile, r: RectF) {
        MansionStyle.drawGlass(canvas, r, dp(16f), density)

        // The icon in a soft disc of its colour.
        val iconSize = min(r.height() * .42f, dp(30f))
        val icx = r.left + dp(14f) + iconSize / 2
        val icy = r.centerY()
        fill.shader = null
        fill.color = MansionStyle.withAlpha(t.color, .16f)
        canvas.drawCircle(icx, icy, iconSize * .78f, fill)
        when (t.icon) {
            Icon.HEART -> MansionStyle.drawHeart(canvas, icx, icy + iconSize * .06f, iconSize * .62f, t.color)
            Icon.GHOST -> MansionStyle.drawGhost(canvas, icx, icy, iconSize * .78f, t.color, Color.rgb(30, 20, 60))
            Icon.COINS -> MansionStyle.drawCoins(canvas, icx, icy, iconSize * .74f, t.color, Color.rgb(170, 120, 30))
            Icon.KEY -> MansionStyle.drawKey(canvas, icx, icy, iconSize * .8f, t.color, density)
        }

        val textLeft = icx + iconSize * .78f + dp(10f)
        val textW = r.right - textLeft - dp(10f)
        labelPaint.color = MansionStyle.dim
        labelPaint.textSize = min(dp(10.5f), r.height() * .17f)
        canvas.drawText(t.label, textLeft, r.top + r.height() * .4f, labelPaint)

        valuePaint.color = if (t.value == null) MansionStyle.faint else MansionStyle.text
        valuePaint.textSize = min(dp(24f), r.height() * .34f)
        val value = TextUtils.ellipsize(t.value ?: "—", valuePaint, textW, TextUtils.TruncateAt.END).toString()
        val baseline = r.top + r.height() * .76f
        canvas.drawText(value, textLeft, baseline, valuePaint)
        if (t.value != null && t.extra != null) {
            val vx = textLeft + valuePaint.measureText(value) + dp(5f)
            bodyPaint.color = MansionStyle.dim
            bodyPaint.textSize = valuePaint.textSize * .55f
            if (vx + bodyPaint.measureText(t.extra) < r.right - dp(6f)) canvas.drawText(t.extra, vx, baseline, bodyPaint)
        }
    }

    companion object {
        private const val FRAME_MS = 33L
        private const val MANUAL_TIMEOUT_MS = 20_000L
    }
}
