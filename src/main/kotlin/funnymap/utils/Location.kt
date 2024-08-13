package funnymap.utils

import funnymap.FunnyMap.mc
import funnymap.config.Config
import funnymap.core.RoomData
import funnymap.core.map.Direction
import funnymap.core.map.Room
import funnymap.core.map.RoomType
import funnymap.events.ChatEvent
import funnymap.events.EnterBossEvent
import funnymap.features.dungeon.Dungeon
import funnymap.features.dungeon.ScanUtils
import funnymap.features.dungeon.ScoreCalculation
import net.minecraft.util.BlockPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

object Location {

    private var onHypixel = false
    var inSkyblock = false
    var island = Island.Unknown
    val inDungeons
        get() = island == Island.Dungeon
    var dungeonFloor = -1
    var masterMode = false
    var inBoss = false
    private var lastRoomPos: Pair<Int, Int>? = null
    var currentRoom: Room? = null

    private var islandRegex = Regex("^§r§b§l(?:Area|Dungeon): §r§7(.+)§r\$")

    private val entryMessages = listOf(
        "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.",
        "[BOSS] Scarf: This is where the journey ends for you, Adventurers.",
        "[BOSS] The Professor: I was burdened with terrible news recently...",
        "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!",
        "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.",
        "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!"
    )

    private var tickCount = 0

    fun onTick() {
        if (mc.theWorld == null) return
        tickCount++
        if (tickCount % 20 != 0) return
        if (Config.forceSkyblock) {
            inSkyblock = true
            island = Island.Dungeon
            dungeonFloor = 7
            return
        }

        inSkyblock = onHypixel && mc.theWorld.scoreboard?.getObjectiveInDisplaySlot(1)?.name == "SBScoreboard"

        if (island == Island.Unknown) {
            TabList.getTabList().firstNotNullOfOrNull { islandRegex.find(it.second) }
                ?.groupValues?.getOrNull(1)?.let { areaName ->
                    Island.entries.find { it.displayName == areaName }?.let { island = it }
                }
        }

        if (island == Island.Dungeon && dungeonFloor == -1) {
            Scoreboard.getLines().find {
                Scoreboard.cleanLine(it).run {
                    contains("The Catacombs (") && !contains("Queue")
                }
            }?.let {
                val line = it.substringBefore(")")
                dungeonFloor = line.lastOrNull()?.digitToIntOrNull() ?: 0
                masterMode = line[line.length - 2] == 'M'
            }
        }
    }

    @SubscribeEvent
    fun onMove(event: LivingEvent.LivingUpdateEvent) {
        if (mc.theWorld == null ||! inDungeons ||! event.entity.equals(mc.thePlayer) || inBoss) return
        ScanUtils.getRoomCentre(mc.thePlayer.posX.toInt(), mc.thePlayer.posZ.toInt()).run {
            if (this != lastRoomPos) {
                lastRoomPos = this
                setCurrentRoom(this)
            }
        }
    }

    fun setCurrentRoom(pos: Pair<Int, Int>) {
        val data = ScanUtils.getRoomFromPos(pos)?.data?: return
        val room: Room = Dungeon.Info.uniqueRooms.toList().find { data.name == it.mainRoom.data.name }?.mainRoom?: return
        if (room.direction == null) {
            room.direction = ScanUtils.getDirection(pos.first, pos.second, data, room.core)
        }
        if (room.corner == null && room.direction != null) {
            room.corner = ScanUtils.getCorner(room.direction, room.data.name)
        }
        if (room != currentRoom || (currentRoom?.direction == null || currentRoom?.corner == null)) {
            currentRoom = room
        }
    }

    fun relativeOfActual(pos: BlockPos) : BlockPos? {
        if (currentRoom == null || currentRoom?.data?.type == RoomType.BOSS) return BlockPos(pos.x, pos.y, pos.z)
        val corner = currentRoom?.corner ?: return null
        return when (currentRoom?.direction) {
            Direction.NW -> BlockPos(pos.x - corner.x, pos.y, pos.z - corner.y)
            Direction.NE -> BlockPos(pos.z - corner.y, pos.y, -(pos.x - corner.x))
            Direction.SE -> BlockPos(-(pos.x - corner.x), pos.y, -(pos.z - corner.y))
            Direction.SW -> BlockPos(-(pos.z - corner.y), pos.y, pos.x - corner.x)
            else -> null
        }
    }

    fun actualOfRelative(pos: BlockPos) : BlockPos? {
        if (currentRoom == null || currentRoom?.data?.type == RoomType.BOSS) return pos
        val corner = currentRoom?.corner ?: return null
        return when (currentRoom?.direction) {
            Direction.NW -> BlockPos(pos.x + corner.x, pos.y, pos.z + corner.y)
            Direction.NE -> BlockPos(-(pos.z - corner.x), pos.y, pos.x + corner.y)
            Direction.SE -> BlockPos(-(pos.x - corner.x), pos.y, -(pos.z - corner.y))
            Direction.SW -> BlockPos(pos.z + corner.x, pos.y, -(pos.x - corner.y))
            else -> null
        }
    }

    @SubscribeEvent
    fun onChat(event: ChatEvent) {
        val wasInBoss = inBoss
        if (event.packet.type.toInt() == 2 || !inDungeons) return
        if (event.text.startsWith("[BOSS] Maxor: ")) inBoss = true
        if (entryMessages.any { it == event.text }) inBoss = true
        if(inBoss && !wasInBoss) {
            MinecraftForge.EVENT_BUS.post(EnterBossEvent())
        }

        if (event.packet.type.toInt() == 2 || !inDungeons || inBoss) return
        val index = (entryMessages.indexOf(event.text) + 1).let { if (it == 0) { if (event.text.startsWith("[BOSS] Maxor: ")) 7 else 0 } else it }
        if (index != 0) {
            dungeonFloor = index
            inBoss = true
            currentRoom = Room(-1, -1, RoomData(dungeonFloor.toString(), RoomType.BOSS, emptyList(), 0, 0, 0, null, null, null, false))
            ScoreCalculation.updateScore()
        }
    }

    @SubscribeEvent
    fun onConnect(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
        onHypixel = mc.runCatching {
            !event.isLocal && ((thePlayer?.clientBrand?.lowercase()?.contains("hypixel")
                ?: currentServerData?.serverIP?.lowercase()?.contains("hypixel")) == true)
        }.getOrDefault(false)
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        island = Island.Unknown
        dungeonFloor = -1
        inBoss = false
        lastRoomPos = null
        currentRoom = null
    }

    @SubscribeEvent
    fun onDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        onHypixel = false
        inSkyblock = false
        island = Island.Unknown
        dungeonFloor = -1
        inBoss = false
        currentRoom = null
    }

    enum class Island(val displayName: String) {
        PrivateIsland("Private Island"),
        Garden("Garden"),
        SpiderDen("Spider's Den"),
        CrimsonIsle("Crimson Isle"),
        TheEnd("The End"),
        GoldMine("Gold Mine"),
        DeepCaverns("Deep Caverns"),
        DwarvenMines("Dwarven Mines"),
        GlaciteMineshaft("Mineshaft"),
        CrystalHollows("Crystal Hollows"),
        FarmingIsland("The Farming Islands"),
        ThePark("The Park"),
        Dungeon("Catacombs"),
        DungeonHub("Dungeon Hub"),
        Hub("Hub"),
        DarkAuction("Dark Auction"),
        JerryWorkshop("Jerry's Workshop"),
        Kuudra("Kuudra"),
        Unknown("(Unknown)");
    }
}
