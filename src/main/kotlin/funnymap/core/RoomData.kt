package funnymap.core

import funnymap.core.map.RoomType

data class RoomData(
    val name: String,
    var type: RoomType,
    val cores: List<Int>,
    val crypts: Int,
    val secrets: Int,
    val trappedChests: Int,
    val dirCores: List<Int>?,
    val turn: List<Int>?,
    val distance: Int?,
    val strict: Boolean,
) {
    companion object {
        fun createUnknown(type: RoomType) = RoomData("Unknown", type, emptyList(), 0, 0, 0, null, null, null, false)
    }
}
