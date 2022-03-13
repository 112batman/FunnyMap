package funnymap.core

import funnymap.FunnyMap.Companion.config
import java.awt.Color

data class Room(override var x: Int, override var z: Int, var data: RoomData) : Tile(x, z) {

    var hasMimic = false
    var isSeparator = false

    override val color: Color
        get() = when (data.type) {
            RoomType.BLOOD -> config.colorBlood
            RoomType.CHAMPION -> config.colorMiniboss
            RoomType.ENTRANCE -> config.colorEntrance
            RoomType.FAIRY -> config.colorFairy
            RoomType.PUZZLE -> config.colorPuzzle
            RoomType.RARE -> config.colorRare
            RoomType.TRAP -> config.colorTrap
            else -> config.colorRoom
        }
}
