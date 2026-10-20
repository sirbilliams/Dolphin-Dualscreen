// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import org.dolphinemu.dolphinemu.features.dualscreen.CompanionLog
import org.dolphinemu.dolphinemu.features.dualscreen.CompanionView
import org.dolphinemu.dolphinemu.features.dualscreen.MemorySnapshot
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Luigi's Mansion bottom screen.
 *
 * Layout (portrait, as on the Thor): room name and floor at the top, the floor map in the middle
 * with Luigi's room marked, and health / Boos / treasure / keys below. On a wide display the
 * status column moves to the right of the map.
 *
 * Floor images are read from <user dir>/Dualscreen/LuigisMansion/ (floor_1.png, f2.png, ...). If
 * they're missing, the map is drawn as room names at their positions instead.
 *
 * Tap the map to look at another floor; tap the top bar (or wait) to follow Luigi again.
 */
class LuigisMansionView(context: Context) : CompanionView(context) {
    private val table = LmRoomTable.load(context)
    private val density = resources.displayMetrics.density
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

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

    private val bg = Color.rgb(11, 14, 20)
    private val panel = Color.rgb(22, 27, 36)
    private val green = Color.rgb(76, 195, 90)
    private val textMain = Color.rgb(240, 242, 245)
    private val textDim = Color.rgb(140, 148, 160)
    private val heartRed = Color.rgb(229, 72, 77)
    private val gold = Color.rgb(242, 193, 78)
    private val booBlue = Color.rgb(201, 212, 255)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = text(bold = true)
    private val bodyPaint = text(bold = false)
    private val labelPaint = text(bold = true)
    private val valuePaint = text(bold = true)

    init {
        setBackgroundColor(bg)
        CompanionLog.i(
            "Luigi's Mansion screen: ${table.rooms.size} rooms; map images in ${imageFolder?.path} " +
                    table.maps.values.joinToString { "${it.image}=${File(imageFolder, it.image).isFile}" }
        )
    }

    private fun text(bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun dp(v: Float) = v * density

    override fun update(snapshot: MemorySnapshot) {
        val s = LuigisMansionCompanion.parse(snapshot)
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
        val pad = dp(10f)

        val s = state
        if (s == null || (s.mapId == null && lastRoom == null)) {
            drawWaiting(canvas, w, h)
            return
        }

        val wide = w > h * 1.15f
        val headerH = min(dp(64f), h * 0.16f)
        headerRect.set(0f, 0f, w, headerH)
        drawHeader(canvas, s, RectF(pad, pad * 0.6f, w - pad, headerH))

        if (wide) {
            val statusW = w * 0.32f
            mapRect.set(pad, headerH + pad * 0.5f, w - statusW - pad, h - pad)
            drawMap(canvas, mapRect)
            drawStatus(canvas, s, RectF(w - statusW, headerH + pad * 0.5f, w - pad, h - pad), columns = 1)
        } else {
            val statusH = min(h * 0.34f, dp(230f))
            mapRect.set(pad, headerH + pad * 0.5f, w - pad, h - statusH - pad)
            drawMap(canvas, mapRect)
            drawStatus(canvas, s, RectF(pad, h - statusH, w - pad, h - pad), columns = 2)
        }

        // Keep the marker pulsing (and the manual-browse countdown ticking).
        postInvalidateDelayed(FRAME_MS)
    }

    private fun drawWaiting(canvas: Canvas, w: Float, h: Float) {
        titlePaint.color = textMain
        titlePaint.textSize = min(w, h) * 0.07f
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Luigi's Mansion", w / 2, h / 2 - titlePaint.textSize * 0.2f, titlePaint)
        bodyPaint.color = textDim
        bodyPaint.textSize = titlePaint.textSize * 0.5f
        bodyPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Waiting for the mansion…", w / 2, h / 2 + bodyPaint.textSize * 1.6f, bodyPaint)
        titlePaint.textAlign = Paint.Align.LEFT
        bodyPaint.textAlign = Paint.Align.LEFT
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
        val sub = listOfNotNull(floorLabel, area?.takeIf { it != title }).joinToString(" · ")

        // Status chip on the right.
        val manual = manualFloor != null && shownFloorKey() != null
        val chip = when {
            manual -> "Viewing ${table.maps[shownFloorKey()]?.label} · tap to follow"
            !s.inGame -> "Paused / loading"
            else -> "Following Luigi"
        }
        labelPaint.textSize = r.height() * 0.24f
        val chipW = labelPaint.measureText(chip) + dp(16f)
        val chipH = labelPaint.textSize * 1.8f
        val chipRect = RectF(r.right - chipW, r.top + (r.height() - chipH) / 2, r.right, r.top + (r.height() + chipH) / 2)
        fill.color = if (manual || !s.inGame) panel else Color.argb(50, Color.red(green), Color.green(green), Color.blue(green))
        canvas.drawRoundRect(chipRect, chipH / 2, chipH / 2, fill)
        labelPaint.color = if (manual || !s.inGame) textDim else green
        canvas.drawText(chip, chipRect.left + dp(8f), chipRect.centerY() + labelPaint.textSize * 0.35f, labelPaint)

        val textW = r.width() - chipW - dp(10f)
        titlePaint.color = textMain
        titlePaint.textSize = r.height() * 0.46f
        val t = TextUtils.ellipsize(title, titlePaint, textW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(t, r.left, r.top + titlePaint.textSize, titlePaint)
        if (sub.isNotEmpty()) {
            bodyPaint.color = textDim
            bodyPaint.textSize = r.height() * 0.26f
            canvas.drawText(sub, r.left, r.top + titlePaint.textSize + bodyPaint.textSize * 1.35f, bodyPaint)
        }
    }

    private fun drawMap(canvas: Canvas, r: RectF) {
        fill.color = panel
        val corner = dp(10f)
        canvas.drawRoundRect(r, corner, corner, fill)

        val key = shownFloorKey()
        val map = key?.let { table.maps[it] }
        if (map == null) {
            bodyPaint.color = textDim
            bodyPaint.textSize = dp(14f)
            bodyPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No map for this area", r.centerX(), r.centerY(), bodyPaint)
            bodyPaint.textAlign = Paint.Align.LEFT
            return
        }

        // Fit the map's coordinate space into r, keeping its aspect ratio.
        val inset = dp(6f)
        val scale = min((r.width() - inset * 2) / map.width, (r.height() - inset * 2) / map.height)
        val dw = map.width * scale
        val dh = map.height * scale
        val ox = r.left + (r.width() - dw) / 2
        val oy = r.top + (r.height() - dh) / 2
        val dest = RectF(ox, oy, ox + dw, oy + dh)

        val bitmap = bitmapFor(map)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, dest, bitmapPaint)
        } else {
            drawSchematic(canvas, map, ox, oy, scale)
        }

        // Floor label in the corner.
        labelPaint.color = textDim
        labelPaint.textSize = dp(11f)
        canvas.drawText(map.label, r.left + dp(8f), r.bottom - dp(8f), labelPaint)

        // Luigi's room.
        val room = lastRoom
        if (room != null && room.hasMarker && room.map == key) {
            val cx = ox + room.x!! * scale
            val cy = oy + room.y!! * scale
            val base = max(dp(7f), min(dw, dh) * 0.025f)
            val phase = (SystemClock.uptimeMillis() % PULSE_MS) / PULSE_MS.toFloat()
            stroke.color = green
            stroke.alpha = ((1f - phase) * 200).toInt()
            stroke.strokeWidth = dp(2.5f)
            canvas.drawCircle(cx, cy, base * (1.2f + phase * 1.6f), stroke)
            fill.color = Color.WHITE
            canvas.drawCircle(cx, cy, base * 1.15f, fill)
            fill.color = green
            canvas.drawCircle(cx, cy, base * (0.85f + 0.08f * sin(phase * 6.283f)), fill)
        }
    }

    /** Draws the floor as room names at their marker positions (used when no image is present). */
    private fun drawSchematic(canvas: Canvas, map: LmMap, ox: Float, oy: Float, scale: Float) {
        val rooms = table.markersOn(map.key)
        bodyPaint.textSize = max(dp(8f), min(dp(11f), map.width * scale / 34f))
        bodyPaint.textAlign = Paint.Align.CENTER
        for (room in rooms) {
            val cx = ox + room.x!! * scale
            val cy = oy + room.y!! * scale
            val current = room.name == lastRoom?.name
            fill.color = if (current) green else Color.rgb(70, 80, 95)
            canvas.drawCircle(cx, cy, dp(3f), fill)
            bodyPaint.color = if (current) textMain else textDim
            canvas.drawText(shortName(room.name), cx, cy - dp(6f), bodyPaint)
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

    private fun drawStatus(canvas: Canvas, s: LmState, r: RectF, columns: Int) {
        val tiles = listOf(
            Tile("HEALTH", s.health?.toString(), heartRed),
            Tile("BOOS", s.boos?.let { "$it / 50" }, booBlue),
            Tile("TREASURE", s.treasure?.let { "${numberFormat.format(it)} G" }, gold),
            Tile("KEYS", s.keys?.size?.toString(), green),
        )
        val gap = dp(8f)
        val rows = (tiles.size + columns - 1) / columns
        val keysLines = if (s.keys.isNullOrEmpty()) 0 else 2
        val keysH = if (keysLines > 0) dp(15f) * keysLines + gap else 0f
        val tileW = (r.width() - gap * (columns - 1)) / columns
        val tileH = min((r.height() - keysH - gap * (rows - 1)) / rows, dp(78f))

        for ((i, t) in tiles.withIndex()) {
            val col = i % columns
            val row = i / columns
            val left = r.left + col * (tileW + gap)
            val top = r.top + row * (tileH + gap)
            drawTile(canvas, t, RectF(left, top, left + tileW, top + tileH))
        }

        if (keysLines > 0) {
            bodyPaint.color = textDim
            bodyPaint.textSize = dp(11.5f)
            val names = s.keys!!.joinToString(", ") { it.removeSuffix(" Key") }
            val top = r.top + rows * tileH + (rows - 1) * gap + gap + bodyPaint.textSize
            val text = TextUtils.ellipsize("Keys: $names", bodyPaint, r.width() * keysLines - dp(30f), TextUtils.TruncateAt.END).toString()
            // Simple two-line wrap.
            val cut = bodyPaint.breakText(text, true, r.width(), null)
            canvas.drawText(text, 0, cut, r.left, top, bodyPaint)
            if (cut < text.length) {
                canvas.drawText(text, cut, text.length, r.left, top + bodyPaint.textSize * 1.3f, bodyPaint)
            }
        }
    }

    private class Tile(val label: String, val value: String?, val color: Int)

    private fun drawTile(canvas: Canvas, t: Tile, r: RectF) {
        fill.color = panel
        canvas.drawRoundRect(r, dp(10f), dp(10f), fill)
        fill.color = t.color
        canvas.drawRoundRect(RectF(r.left, r.top + dp(10f), r.left + dp(4f), r.bottom - dp(10f)), dp(2f), dp(2f), fill)

        labelPaint.color = textDim
        labelPaint.textSize = min(dp(11f), r.height() * 0.2f)
        canvas.drawText(t.label, r.left + dp(14f), r.top + r.height() * 0.36f, labelPaint)

        valuePaint.color = if (t.value == null) textDim else textMain
        valuePaint.textSize = min(dp(26f), r.height() * 0.38f)
        val v = TextUtils.ellipsize(t.value ?: "—", valuePaint, r.width() - dp(20f), TextUtils.TruncateAt.END).toString()
        canvas.drawText(v, r.left + dp(14f), r.top + r.height() * 0.78f, valuePaint)
    }

    companion object {
        private const val FRAME_MS = 33L
        private const val PULSE_MS = 1400L
        private const val MANUAL_TIMEOUT_MS = 20_000L
    }
}
