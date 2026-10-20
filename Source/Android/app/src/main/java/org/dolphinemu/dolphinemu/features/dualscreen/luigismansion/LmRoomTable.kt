// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.dualscreen.luigismansion

import android.content.Context
import org.json.JSONObject

/** A floor map: an image from the PopTracker pack plus its pixel size (the room coordinate space). */
class LmMap(val key: String, val label: String, val image: String, val width: Int, val height: Int)

/** A room. [x]/[y] are pixel positions on map [map]; rooms without a marker only name a floor. */
class LmRoom(
    val id: Int,
    val mapId: Int,
    val name: String,
    val map: String?,
    val x: Int?,
    val y: Int?,
    private val floorMap: String?
) {
    /** The floor this room is shown on, if known. */
    val floorKey: String? get() = map ?: floorMap
    val hasMarker: Boolean get() = map != null && x != null && y != null
}

/** Loaded from assets/dualscreen/luigis_mansion_rooms.json. */
class LmRoomTable(
    val maps: LinkedHashMap<String, LmMap>,
    private val areas: Map<Int, String>,
    private val areaMaps: Map<Int, String>,
    val rooms: List<LmRoom>
) {
    private val byKey = rooms.associateBy { it.mapId to it.id }

    fun room(mapId: Int, roomId: Int): LmRoom? = byKey[mapId to roomId]

    fun areaName(mapId: Int): String? = areas[mapId]

    /** Floor to show for an area when there's no room marker (e.g. the lab). */
    fun areaFloor(mapId: Int): String? = areaMaps[mapId]

    /** Rooms with a marker on [mapKey], for drawing the schematic. Duplicates by name removed. */
    fun markersOn(mapKey: String): List<LmRoom> =
        rooms.filter { it.map == mapKey && it.hasMarker }.distinctBy { it.name }

    companion object {
        private const val ASSET = "dualscreen/luigis_mansion_rooms.json"

        fun load(context: Context): LmRoomTable {
            val json = JSONObject(context.assets.open(ASSET).bufferedReader().use { it.readText() })

            val maps = LinkedHashMap<String, LmMap>()
            val mapsJson = json.getJSONObject("maps")
            for (key in mapsJson.keys()) {
                val m = mapsJson.getJSONObject(key)
                maps[key] = LmMap(
                    key, m.getString("label"), m.getString("image"),
                    m.getInt("width"), m.getInt("height")
                )
            }

            fun intKeyed(name: String): Map<Int, String> {
                val o = json.optJSONObject(name) ?: return emptyMap()
                return o.keys().asSequence().associate { it.toInt() to o.getString(it) }
            }

            val rooms = ArrayList<LmRoom>()
            val roomsJson = json.getJSONArray("rooms")
            for (i in 0 until roomsJson.length()) {
                val r = roomsJson.getJSONObject(i)
                rooms.add(
                    LmRoom(
                        id = r.getInt("id"),
                        mapId = r.getInt("mapId"),
                        name = r.getString("name"),
                        map = if (r.has("map")) r.getString("map") else null,
                        x = if (r.has("x")) r.getInt("x") else null,
                        y = if (r.has("y")) r.getInt("y") else null,
                        floorMap = if (r.has("floorMap")) r.getString("floorMap") else null,
                    )
                )
            }
            return LmRoomTable(maps, intKeyed("areas"), intKeyed("areaMaps"), rooms)
        }
    }
}
