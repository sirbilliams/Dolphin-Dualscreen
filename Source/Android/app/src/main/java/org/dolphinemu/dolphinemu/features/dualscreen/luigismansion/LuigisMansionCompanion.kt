// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.content.Context
import org.dolphinemu.dolphinemu.features.dualscreen.CompanionGame
import org.dolphinemu.dolphinemu.features.dualscreen.CompanionView
import org.dolphinemu.dolphinemu.features.dualscreen.MemorySnapshot
import org.dolphinemu.dolphinemu.features.dualscreen.WatchList

/**
 * Luigi's Mansion (USA, GLME01) companion screen: a mansion map that follows Luigi's room, plus
 * health, Boos captured, treasure and keys.
 *
 * Memory addresses come from the Luigi's Mansion Archipelago client
 * (https://github.com/BootsinSoots/Archipelago, worlds/luigismansion, MIT license).
 */
object LuigisMansionCompanion : CompanionGame {
    override val gameIds = setOf("GLME01")

    // 2 when the game is in a playable state (not title screen, loading or a cutscene transition).
    private const val PLAY_STATE_ADDR = 0x803A3AE4L
    // 1 = E. Gadd's lab, 2 = mansion, 3 = training room, 6 = portrait gallery, 9/11/13 = bosses.
    private const val MAP_ID_ADDR = 0x804D80A4L
    // Pointer to Luigi's data; the current room ID is at +0x35C and the wallet starts at +0x324.
    private const val LUIGI_PTR = 0x803D8B7CL
    private const val ROOM_ID_OFFSET = 0x35C
    // Pointer whose target holds Luigi's health (16 bits) at +0xB8.
    private const val HEALTH_PTR = 0x803D8B40L
    private const val HEALTH_OFFSET = 0xB8
    // 8 bytes of Boo capture flags; the number of set bits is the number of Boos caught.
    private const val BOO_FLAGS_ADDR = 0x803D5E04L
    // Door key flags live in the 12 bytes from here (read as three 32-bit words).
    private const val KEY_FLAGS_ADDR = 0x803D5E14L

    class DoorKey(val name: String, val address: Long, val bit: Int)

    val doorKeys = listOf(
        DoorKey("Heart Key", 0x803D5E14, 3),
        DoorKey("Club Key", 0x803D5E19, 2),
        DoorKey("Diamond Key", 0x803D5E1B, 3),
        DoorKey("Spade Key", 0x803D5E1D, 0),
        DoorKey("Parlor Key", 0x803D5E18, 2),
        DoorKey("Anteroom Key", 0x803D5E18, 6),
        DoorKey("Wardrobe Key", 0x803D5E19, 3),
        DoorKey("Family Hallway Key", 0x803D5E18, 1),
        DoorKey("Master Bedroom Key", 0x803D5E17, 7),
        DoorKey("Nursery Key", 0x803D5E17, 3),
        DoorKey("Twins Bedroom Key", 0x803D5E17, 4),
        DoorKey("Ballroom Key", 0x803D5E15, 7),
        DoorKey("Storage Room Key", 0x803D5E16, 0),
        DoorKey("Fortune Teller Key", 0x803D5E14, 4),
        DoorKey("Laundry Room Key", 0x803D5E14, 7),
        DoorKey("Lower 2F Stairwell Key", 0x803D5E1D, 2),
        DoorKey("Conservatory Key", 0x803D5E16, 5),
        DoorKey("Dining Room Key", 0x803D5E15, 6),
        DoorKey("North Rec Room Key", 0x803D5E17, 1),
        DoorKey("Billiards Room Key", 0x803D5E16, 1),
        DoorKey("Safari Room Key", 0x803D5E1B, 0),
        DoorKey("Balcony Key", 0x803D5E1B, 6),
        DoorKey("Cellar Key", 0x803D5E1C, 4),
        DoorKey("Clockwork Key", 0x803D5E1A, 5),
        DoorKey("Armory Key", 0x803D5E1A, 3),
        DoorKey("Sitting Room Key", 0x803D5E17, 5),
        DoorKey("Pipe Room Key", 0x803D5E1C, 5),
        DoorKey("Cold Storage Key", 0x803D5E1C, 1),
        DoorKey("Artist's Studio Key", 0x803D5E1B, 7),
        DoorKey("Wardrobe Balcony Key", 0x803D5E19, 1),
        DoorKey("Study Key", 0x803D5E18, 0),
        DoorKey("Basement Stairwell Key", 0x803D5E15, 1),
        DoorKey("1F Bathroom Key", 0x803D5E16, 7),
        DoorKey("1F Washroom Key", 0x803D5E16, 4),
        DoorKey("Kitchen Key", 0x803D5E15, 3),
        DoorKey("Boneyard Key", 0x803D5E15, 2),
        DoorKey("Projection Room Key", 0x803D5E16, 2),
        DoorKey("Mirror Room Key", 0x803D5E14, 5),
        DoorKey("Butler's Room Key", 0x803D5E14, 1),
        DoorKey("Tea Room Key", 0x803D5E19, 7),
        DoorKey("South Rec Room Key", 0x803D5E17, 0),
        DoorKey("Upper 2F Stairwell Key", 0x803D5E1D, 3),
        DoorKey("2F Bathroom Key", 0x803D5E1A, 0),
        DoorKey("2F Washroom Key", 0x803D5E19, 5),
        DoorKey("Nana's Room Key", 0x803D5E1A, 1),
        DoorKey("Astral Hall Key", 0x803D5E19, 4),
        DoorKey("Observatory Key", 0x803D5E19, 0),
        DoorKey("Guest Room Key", 0x803D5E17, 6),
        DoorKey("East Attic Hallway Key", 0x803D5E1A, 7),
        DoorKey("Telephone Room Key", 0x803D5E1A, 4),
        DoorKey("Ceramics Studio Key", 0x803D5E1A, 2),
        DoorKey("Breaker Room Key", 0x803D5E1C, 7),
        DoorKey("Basement Hallway Key", 0x803D5E1C, 3),
        DoorKey("Altar Hallway Key", 0x803D5E1C, 6),
    )

    /** In-game value of each kind of treasure and where its count is kept (offset from LUIGI_PTR). */
    class Treasure(val offset: Int, val value: Long)

    private val treasures = listOf(
        Treasure(0x324, 5_000), // coins
        Treasure(0x328, 20_000), // bills
        Treasure(0x32C, 100_000), // gold bars
        Treasure(0x330, 500_000), // sapphires
        Treasure(0x334, 800_000), // emeralds
        Treasure(0x338, 1_000_000), // rubies
        Treasure(0x33C, 2_000_000), // diamonds
        Treasure(0x344, 20_000_000), // gold diamonds
        Treasure(0x348, 50_000), // small pearls
        Treasure(0x34C, 100_000), // medium pearls
        Treasure(0x350, 1_000_000), // large pearls
    )

    // Watch indices, filled in by buildWatches().
    private var iPlayState = 0
    private var iMapId = 0
    private var iRoomId = 0
    private var iHealth = 0
    private var iBoos = 0
    private var iKeys = 0
    private var iTreasure = 0

    override fun buildWatches(): WatchList = WatchList().apply {
        iPlayState = u32(PLAY_STATE_ADDR)
        iMapId = u32(MAP_ID_ADDR)
        iRoomId = deref(LUIGI_PTR, ROOM_ID_OFFSET, 4)
        iHealth = deref(HEALTH_PTR, HEALTH_OFFSET, 2)
        iBoos = u32(BOO_FLAGS_ADDR)
        u32(BOO_FLAGS_ADDR + 4)
        iKeys = u32(KEY_FLAGS_ADDR)
        u32(KEY_FLAGS_ADDR + 4)
        u32(KEY_FLAGS_ADDR + 8)
        iTreasure = deref(LUIGI_PTR, treasures[0].offset, 4)
        for (t in treasures.drop(1)) deref(LUIGI_PTR, t.offset, 4)
    }

    override fun createView(context: Context): CompanionView = LuigisMansionView(context)

    /** Decodes a snapshot. Fields are null when the game didn't have a valid value. */
    fun parse(s: MemorySnapshot): LmState {
        val mapId = s.value(iMapId)?.toInt()
        val inGame = s.value(iPlayState) == 2L

        val boos = if (s.isValid(iBoos) && s.isValid(iBoos + 1))
            java.lang.Long.bitCount(s.value(iBoos)!!) + java.lang.Long.bitCount(s.value(iBoos + 1)!!)
        else null

        val keys = ArrayList<String>()
        val keyWords = (0 until 3).map { s.value(iKeys + it) }
        if (keyWords.all { it != null }) {
            for (k in doorKeys) {
                val byteIndex = (k.address - KEY_FLAGS_ADDR).toInt()
                val word = keyWords[byteIndex / 4]!!
                val byte = (word shr ((3 - byteIndex % 4) * 8)) and 0xFFL
                if ((byte shr k.bit) and 1L == 1L) keys.add(k.name)
            }
        }

        var treasure: Long? = 0L
        for ((i, t) in treasures.withIndex()) {
            val count = s.value(iTreasure + i)
            if (count == null) {
                treasure = null
                break
            }
            treasure = treasure!! + count * t.value
        }

        return LmState(
            inGame = inGame,
            mapId = mapId,
            roomId = s.value(iRoomId)?.toInt(),
            // Health reads aren't meaningful in the lab.
            health = if (mapId == 1) null else s.value(iHealth)?.toInt(),
            boos = boos,
            treasure = treasure,
            keys = if (keyWords.all { it != null }) keys else null,
        )
    }
}

data class LmState(
    val inGame: Boolean,
    val mapId: Int?,
    val roomId: Int?,
    val health: Int?,
    val boos: Int?,
    val treasure: Long?,
    val keys: List<String>?,
)
