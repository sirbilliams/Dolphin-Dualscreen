// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen

/**
 * Access to the core's per-frame memory snapshot (Source/Core/Core/DualScreen/MemoryWatch.h).
 * The core copies the watched values on its own thread, so calling these never pauses emulation.
 */
object DualScreenNative {
    /** [spec] holds groups of four ints: address, offset, size (1/2/4), deref (0/1). */
    @JvmStatic
    external fun setWatches(spec: IntArray?)

    /** Returns [serial, value0, valid0, value1, valid1, ...]; serial is 0 before the first capture. */
    @JvmStatic
    external fun getSnapshot(): IntArray
}

/** Builds the watch spec for [DualScreenNative.setWatches]. Each add returns the watch's index. */
class WatchList {
    private val spec = ArrayList<Int>()
    var size = 0
        private set

    fun u8(address: Long) = add(address, 0, 1, false)
    fun u16(address: Long) = add(address, 0, 2, false)
    fun u32(address: Long) = add(address, 0, 4, false)

    /** Reads a pointer at [pointer], then [size] bytes at pointer + [offset]. */
    fun deref(pointer: Long, offset: Int, size: Int) = add(pointer, offset, size, true)

    private fun add(address: Long, offset: Int, bytes: Int, deref: Boolean): Int {
        spec.add(address.toInt())
        spec.add(offset)
        spec.add(bytes)
        spec.add(if (deref) 1 else 0)
        return size++
    }

    fun toIntArray(): IntArray = spec.toIntArray()
}

/** One capture from the core. Values are unsigned; use [value] to read them as Long. */
class MemorySnapshot(private val raw: IntArray) {
    /** Changes every time the core refreshes the values; 0 means nothing captured yet. */
    val serial: Int get() = if (raw.isEmpty()) 0 else raw[0]

    fun isValid(index: Int): Boolean {
        val i = 2 + index * 2
        return i < raw.size && raw[i] != 0
    }

    /** The watched value, or null if the read failed or the pointer wasn't valid. */
    fun value(index: Int): Long? {
        if (!isValid(index)) return null
        return raw[1 + index * 2].toLong() and 0xFFFFFFFFL
    }
}
