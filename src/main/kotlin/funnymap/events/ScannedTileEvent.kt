package funnymap.events

import funnymap.core.map.Tile
import net.minecraftforge.fml.common.eventhandler.Event

class ScannedTileEvent(val tile: Tile) : Event()