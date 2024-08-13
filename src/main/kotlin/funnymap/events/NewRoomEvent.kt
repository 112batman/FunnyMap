package funnymap.events

import funnymap.core.map.Room
import net.minecraftforge.fml.common.eventhandler.Event

class NewRoomEvent(val room: Room) : Event()