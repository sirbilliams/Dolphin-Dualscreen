// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Luigi's Mansion bottom screen: the spectral scanner (ScannerStyle), an original ghost-hunting handheld held up
 * against the stormy night (MansionStyle).
 *
 * Its phosphor screen shows the floor map with Luigi's room as a blip; below it, health and Boo gauges, a rolling
 * treasure counter, keys and a signal meter. On a wide display the readouts move to the right of the screen.
 *
 * Floor images are read from <user dir>/Dualscreen/LuigisMansion/ (floor_1.png, f2.png, ...) and shown in phosphor.
 * Without them the map is drawn as the rooms' names at their places.
 *
 * Tap the screen to look at another floor; tap the top of the scanner (or wait) to follow Luigi again.
 */
class LuigisMansionView(context: Context) : CompanionView(context) {
    private val table = LmRoomTable.load(context)
    private val density = resources.displayMetrics.density
    private val startedAt = SystemClock.uptimeMillis()

    private var state: LmState? = null
    private var lastRoom: LmRoom? = null
    private var lastFloorKey: String? = null

    // Manual floor browsing.
    private var manualFloor: String? = null
    private var manualUntil = 0L

    // The treasure counter rolls toward the real value.
    private var shownTreasure = -1.0
    private var lastFrame = 0L

    private val bitmaps = HashMap<String, Bitmap?>()
    private val imageFolder: File? = CompanionLog.folder()?.let { File(it, "LuigisMansion") }

    // Hit areas, updated in onDraw.
    private val headerRect = RectF()
    private val screenRect = RectF()

    private val inter: Typeface = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ScannerStyle.phosphorFilter
    }
    private val readout = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono }
    private val engraved = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(inter, Typeface.BOLD)
        letterSpacing = .28f
    }

    init {
        CompanionLog.i(
            "Luigi's Mansion screen: ${table.rooms.size} rooms; map images in ${imageFolder?.path} " +
                    table.maps.values.joinToString { "${it.image}=${File(imageFolder, it.image).isFile}" }
        )
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
            screenRect.contains(event.x, event.y) -> {
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

    // --- Layout and drawing -----------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = seconds()
        val lightning = MansionStyle.lightning(t)
        MansionStyle.drawBackdrop(canvas, w, h, t, density)

        val s = state
        val waiting = s == null || (s.mapId == null && lastRoom == null)

        // The scanner's body, with a little of the night around it.
        val m = dp(10f)
        val body = RectF(m, m, w - m, h - m)
        ScannerStyle.drawBody(canvas, body, density)

        val inner = min(body.width(), body.height()) * .055f
        val stripH = max(dp(26f), body.height() * .055f)
        val strip = RectF(body.left + inner * 1.6f, body.top + inner * .5f, body.right - inner * 1.6f, body.top + inner * .5f + stripH)
        headerRect.set(0f, 0f, w, strip.bottom + dp(4f))
        ScannerStyle.drawTopStrip(canvas, strip, t, scanning = !waiting && s?.inGame == true, density = density)
        engraved.color = ScannerStyle.engraveLabel()
        engraved.textSize = min(dp(11f), stripH * .42f)
        engraved.textAlign = Paint.Align.CENTER
        canvas.drawText("SPECTRAL SCANNER", strip.centerX(), strip.centerY() + engraved.textSize * .36f, engraved)
        engraved.textAlign = Paint.Align.LEFT

        val wide = body.width() > body.height() * 1.15f
        val contentTop = strip.bottom + inner * .4f
        val well: RectF
        val readouts: RectF
        if (wide) {
            well = RectF(body.left + inner, contentTop, body.left + body.width() * .64f, body.bottom - inner)
            readouts = RectF(well.right + inner * .8f, contentTop, body.right - inner, body.bottom - inner)
        } else {
            val readoutsH = min(body.height() * .3f, dp(230f))
            well = RectF(body.left + inner, contentTop, body.right - inner, body.bottom - inner - readoutsH - inner * .5f)
            readouts = RectF(body.left + inner, well.bottom + inner * .5f, body.right - inner, body.bottom - inner)
        }
        val glass = ScannerStyle.drawScreenWell(canvas, well, density)
        screenRect.set(glass)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(glass, dp(6f), dp(6f), Path.Direction.CW) })
        if (waiting) drawNoSignal(canvas, glass, t) else drawScreen(canvas, s!!, glass, t)
        ScannerStyle.drawScreenGlass(canvas, glass, t, density, lightning)
        canvas.restore()

        drawReadouts(canvas, s, readouts, t, lightning, wide)

        // The night, the sweep and the blip keep moving.
        postInvalidateDelayed(FRAME_MS)
    }

    private fun drawNoSignal(canvas: Canvas, glass: RectF, t: Float) {
        ScannerStyle.drawStatic(canvas, glass, t, density)
        readout.textAlign = Paint.Align.CENTER
        readout.color = ScannerStyle.phosphor
        readout.textSize = min(glass.width(), glass.height()) * .09f
        readout.letterSpacing = .12f
        val blink = (t % 1.4f) < 1f
        if (blink) canvas.drawText("NO SIGNAL", glass.centerX(), glass.centerY(), readout)
        readout.textSize *= .42f
        readout.letterSpacing = .04f
        readout.color = ScannerStyle.phosphorDim
        canvas.drawText("Waiting for the mansion…", glass.centerX(), glass.centerY() + readout.textSize * 2.4f, readout)
        readout.textAlign = Paint.Align.LEFT
        readout.letterSpacing = 0f
    }

    private fun drawScreen(canvas: Canvas, s: LmState, glass: RectF, t: Float) {
        val pad = dp(10f)

        // Header line: the room, and on the right the floor and what the scanner is doing.
        val room = lastRoom
        val area = s.mapId?.let { table.areaName(it) }
        val title = when {
            room != null && (s.mapId == 2 || s.mapId == room.mapId) -> room.name
            area != null -> area
            else -> "Luigi's Mansion"
        }
        val manual = manualFloor != null && shownFloorKey() != null
        val shownLabel = shownFloorKey()?.let { table.maps[it]?.label } ?: ""
        val mode = when {
            manual -> "VIEW $shownLabel · TAP TOP TO TRACK"
            !s.inGame -> "PAUSED"
            else -> "TRACKING · $shownLabel"
        }.uppercase()
        readout.textSize = min(dp(11f), glass.height() * .045f)
        readout.letterSpacing = .08f
        val modeW = readout.measureText(mode)
        readout.color = if (manual || !s.inGame) ScannerStyle.amber else ScannerStyle.phosphorDim
        canvas.drawText(mode, glass.right - pad - modeW, glass.top + pad + readout.textSize, readout)

        readout.color = ScannerStyle.phosphor
        readout.textSize = min(dp(22f), glass.height() * .085f)
        readout.letterSpacing = .04f
        val titleText = TextUtils.ellipsize("▸ " + title.uppercase(), readout, glass.width() - pad * 2, TextUtils.TruncateAt.END).toString()
        val titleBaseline = glass.top + pad + dp(11f) + readout.textSize * 1.1f
        canvas.drawText(titleText, glass.left + pad, titleBaseline, readout)
        readout.letterSpacing = 0f

        // The map below it.
        val mapArea = RectF(glass.left + pad, titleBaseline + pad * .8f, glass.right - pad, glass.bottom - pad)
        val key = shownFloorKey()
        val map = key?.let { table.maps[it] } ?: return
        val scale = min(mapArea.width() / map.width, mapArea.height() / map.height)
        val dw = map.width * scale
        val dh = map.height * scale
        val ox = mapArea.left + (mapArea.width() - dw) / 2
        val oy = mapArea.top + (mapArea.height() - dh) / 2

        val bitmap = bitmapFor(map)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, RectF(ox, oy, ox + dw, oy + dh), mapPaint)
        } else {
            drawSchematic(canvas, map, ox, oy, scale)
        }

        if (room != null && room.hasMarker && room.map == key) {
            val cx = ox + room.x!! * scale
            val cy = oy + room.y!! * scale
            ScannerStyle.drawBlip(canvas, cx, cy, max(dp(5f), min(dw, dh) * .018f), t, density)
        }
    }

    /** The floor as room names at their places (when there's no floor image). */
    private fun drawSchematic(canvas: Canvas, map: LmMap, ox: Float, oy: Float, scale: Float) {
        val rooms = table.markersOn(map.key)
        readout.textSize = max(dp(8f), min(dp(10.5f), map.width * scale / 34f))
        readout.textAlign = Paint.Align.CENTER
        for (r in rooms) {
            val cx = ox + r.x!! * scale
            val cy = oy + r.y!! * scale
            val current = r.name == lastRoom?.name
            fill.color = if (current) ScannerStyle.phosphor else ScannerStyle.phosphorFaint
            canvas.drawRect(cx - dp(2f), cy - dp(2f), cx + dp(2f), cy + dp(2f), fill)
            readout.color = if (current) ScannerStyle.phosphor else ScannerStyle.phosphorDim
            canvas.drawText(shortName(r.name).uppercase(), cx, cy - dp(6f), readout)
        }
        readout.textAlign = Paint.Align.LEFT
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

    // --- Readouts ---------------------------------------------------------------------------

    /** A small dark inset on the body, like an LCD window, with an engraved label above it. Returns its inside. */
    private fun inset(canvas: Canvas, r: RectF, label: String): RectF {
        engraved.color = ScannerStyle.engraveLabel()
        engraved.textSize = min(dp(9.5f), r.height() * .2f)
        canvas.drawText(label, r.left + dp(2f), r.top + engraved.textSize, engraved)
        val box = RectF(r.left, r.top + engraved.textSize * 1.6f, r.right, r.bottom)
        fill.color = Color.rgb(8, 14, 13)
        canvas.drawRoundRect(box, dp(6f), dp(6f), fill)
        fill.color = Color.argb(90, 0, 0, 0)
        canvas.drawRect(box.left + dp(2f), box.top, box.right - dp(2f), box.top + dp(2f), fill)
        return RectF(box.left + dp(7f), box.top + dp(6f), box.right - dp(7f), box.bottom - dp(6f))
    }

    private fun drawReadouts(canvas: Canvas, s: LmState?, r: RectF, t: Float, lightning: Float, wide: Boolean) {
        val gap = dp(8f)
        val rows = if (wide) 4 else 2
        val cols = if (wide) 1 else 2
        val cellW = (r.width() - gap * (cols - 1)) / cols
        val cellH = (r.height() - gap * (rows - 1)) / rows
        fun cell(i: Int) = RectF(
            r.left + (i % cols) * (cellW + gap), r.top + (i / cols) * (cellH + gap),
            r.left + (i % cols) * (cellW + gap) + cellW, r.top + (i / cols) * (cellH + gap) + cellH
        )

        // Health: ten segments, amber below half, red when low.
        val health = s?.health
        val hb = inset(canvas, cell(0), "HEALTH")
        val hColor = when {
            health == null -> ScannerStyle.phosphorDim
            health < 25 -> ScannerStyle.alarm
            health < 50 -> ScannerStyle.amber
            else -> ScannerStyle.phosphor
        }
        drawGaugeWithNumber(canvas, hb, ((health ?: 0) + 9) / 10, 10, hColor, health?.toString() ?: "--")

        // Boos: fifty, in ten segments of five.
        val boos = s?.boos
        val bb = inset(canvas, cell(1), "BOOS")
        drawGaugeWithNumber(canvas, bb, (boos ?: 0) / 5, 10, ScannerStyle.phosphor, boos?.let { "$it/50" } ?: "--")

        // Treasure: a rolling counter.
        val tb = inset(canvas, cell(2), "TREASURE")
        drawOdometer(canvas, tb, s?.treasure)

        // Keys and the signal meter share the last window.
        val kb = inset(canvas, cell(3), "KEYS · SIGNAL")
        val keys = s?.keys
        readout.color = if (keys == null) ScannerStyle.phosphorDim else ScannerStyle.phosphor
        readout.textSize = min(kb.height() * .62f, dp(24f))
        val keyIcon = kb.height() * .9f
        MansionStyle.drawKey(canvas, kb.left + keyIcon * .5f, kb.centerY(), keyIcon * .75f,
            if (keys == null) ScannerStyle.phosphorDim else ScannerStyle.phosphor, density)
        canvas.drawText(keys?.size?.toString() ?: "--", kb.left + keyIcon * 1.05f, kb.centerY() + readout.textSize * .36f, readout)
        val sigW = min(kb.width() * .3f, dp(42f))
        ScannerStyle.drawSignal(canvas, RectF(kb.right - sigW, kb.top + kb.height() * .12f, kb.right, kb.bottom - kb.height() * .12f), t, lightning, density)
    }

    private fun drawGaugeWithNumber(canvas: Canvas, box: RectF, lit: Int, count: Int, color: Int, number: String) {
        readout.color = color
        readout.textSize = min(box.height() * .62f, dp(22f))
        val numW = readout.measureText("888/50")
        val num = TextUtils.ellipsize(number, readout, numW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(num, box.right - readout.measureText(num), box.centerY() + readout.textSize * .36f, readout)
        val barH = min(box.height() * .5f, dp(14f))
        ScannerStyle.drawSegments(canvas, RectF(box.left, box.centerY() - barH / 2, box.right - numW - dp(8f), box.centerY() + barH / 2),
            count, lit.coerceIn(0, count), color, density)
    }

    private fun drawOdometer(canvas: Canvas, box: RectF, treasure: Long?) {
        // Roll toward the real value (fast at first, settling).
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1000f
        lastFrame = now
        if (treasure != null) {
            shownTreasure = if (shownTreasure < 0) treasure.toDouble()
            else shownTreasure + (treasure - shownTreasure) * min(1f, dt * 4f)
            if (abs(treasure - shownTreasure) < 1) shownTreasure = treasure.toDouble()
        }
        val digits = 9
        val text = if (treasure == null) "-".repeat(digits) else shownTreasure.toLong().toString().padStart(digits, '0')
        readout.textSize = min(box.height() * .62f, dp(22f))
        val gW = readout.measureText(" G")
        val cellW = (box.width() - gW) / digits
        readout.textSize = min(readout.textSize, cellW * 1.45f)
        for ((i, ch) in text.withIndex()) {
            val x = box.left + i * cellW
            fill.color = Color.rgb(14, 30, 26)
            canvas.drawRect(x + dp(1f), box.top, x + cellW - dp(1f), box.bottom, fill)
            val leading = treasure != null && i < digits - shownTreasure.toLong().toString().length
            readout.color = if (leading) ScannerStyle.phosphorFaint else ScannerStyle.phosphor
            readout.textAlign = Paint.Align.CENTER
            canvas.drawText(ch.toString(), x + cellW / 2, box.centerY() + readout.textSize * .36f, readout)
        }
        readout.textAlign = Paint.Align.LEFT
        readout.color = ScannerStyle.phosphorDim
        canvas.drawText(" G", box.right - gW, box.centerY() + readout.textSize * .36f, readout)
    }

    companion object {
        private const val FRAME_MS = 33L
        private const val MANUAL_TIMEOUT_MS = 20_000L
    }
}
