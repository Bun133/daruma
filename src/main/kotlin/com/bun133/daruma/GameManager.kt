package com.bun133.daruma

import com.destroystokyo.paper.Title
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import kotlin.math.abs

class GameManager(var daruma: Daruma) {
    var isGoingOn: Boolean = false
    var isFreezing: Boolean = false

    var teamManager: TeamManager = TeamManager(daruma)
    var roleManager = RoleManager(teamManager)
    var moveAnalyzer: MoveAnalyzer = MoveAnalyzer(teamManager, this)


    init {
        daruma.server.pluginManager.registerEvents(moveAnalyzer, daruma)
    }
}

class TitleBroadCaster {
    companion object {
        fun sendTitle(player: Player, string: String) {
            val title: Title = Title(string)
            player.sendTitle(title)
        }

        fun allTitle(string:String){
            Bukkit.getOnlinePlayers().forEach { sendTitle(it,string) }
        }
    }
}

class TeamManager(var daruma: Daruma) : Listener {
    init {
        daruma.server.pluginManager.registerEvents(this, daruma)
    }

    val joiner: MutableList<Player> = mutableListOf()

    @Deprecated("Please Use #join(player,role)")
    fun join(player: Player) {
        joiner.add(player)
    }

    fun join(player: Player, role: GameRole) {
        joiner.add(player)
        role.getList(this).add(player)
    }

    fun setDemon(p:Player){
        demonManager = DemonManager(p)
    }

    val demonTeam: MutableList<Player> = mutableListOf()
    var demonManager: DemonManager? = null
    val aliveTeam: MutableList<Player> = mutableListOf()
    val clearedTeam: MutableList<Player> = mutableListOf()
    val deadTeam: MutableList<Player> = mutableListOf()

    fun onEnd() {
        joiner.clear()
        demonTeam.clear()
        aliveTeam.clear()
        clearedTeam.clear()
        deadTeam.clear()
    }

    @Override
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        if (Daruma.manager.isGoingOn) {
            join(e.player)
        }
    }

    @Override
    @EventHandler
    fun onLeft(e: PlayerQuitEvent) {
        when (GameRole.get(e.player, this)) {
            GameRole.DEAMON -> {
                Bukkit.broadcastMessage("${GameRole.DEAMON.aliase}の${e.player.displayName}が退出しました")
                joiner.remove(e.player)
                if (GameRole.DEAMON.getList(this).isEmpty()) {
                    Bukkit.broadcastMessage("${GameRole.DEAMON.aliase}がいないためゲームが成立しなくなったので、ゲームを終了します")
                    onEnd()
                }
            }

            GameRole.DEAD -> {
                joiner.remove(e.player)
            }

            GameRole.ALIVE -> {
                Bukkit.broadcastMessage("${GameRole.ALIVE.aliase}の${e.player.displayName}が退出しました")
                joiner.remove(e.player)
                if (GameRole.ALIVE.getList(this).isEmpty()) {
                    Bukkit.broadcastMessage("${GameRole.ALIVE.aliase}がいないためゲームが成立しなくなったので、ゲームを終了します")
                    onEnd()
                }
            }

            GameRole.CLEARED -> {
                joiner.remove(e.player)
            }

            GameRole.JOINER -> {
                Bukkit.broadcastMessage("${GameRole.JOINER.aliase}の${e.player.displayName}が退出しました")
                Bukkit.broadcastMessage("まぁバグなんだけどね")
            }

            GameRole.UNKNOWN -> {
                Bukkit.broadcastMessage("${GameRole.JOINER.aliase}の${e.player.displayName}が退出しました")
                Bukkit.broadcastMessage("まぁバグなんだけどね")
            }
        }
    }

    fun changeRole(p: Player, role: GameRole) {
        GameRole.get(p, this).getList(this).remove(p)
        role.getList(this).add(p)
    }
}

enum class GameRole(var aliase: String) {
    DEAMON("[" + ChatColor.RED + "鬼" + ChatColor.RESET + "]"),
    ALIVE("[" + ChatColor.AQUA + "生存者" + ChatColor.RESET + "]"),
    CLEARED("[" + ChatColor.WHITE + "クリア者" + ChatColor.RESET + "]"),
    DEAD("[" + ChatColor.DARK_GRAY + "死亡者" + ChatColor.RESET + "]"),
    JOINER("[" + ChatColor.WHITE + "参加者" + ChatColor.RESET + "]"),
    UNKNOWN("[" + ChatColor.DARK_GRAY + "不明" + ChatColor.RESET + "]");

    companion object {
        fun get(p: Player, tm: TeamManager): GameRole {
            return when {
                tm.demonTeam.contains(p) -> {
                    DEAMON
                }
                tm.aliveTeam.contains(p) -> {
                    ALIVE
                }
                tm.clearedTeam.contains(p) -> {
                    CLEARED
                }
                tm.deadTeam.contains(p) -> {
                    DEAD
                }
                tm.joiner.contains(p) -> {
                    JOINER
                }
                else -> UNKNOWN
            }
        }
    }

    fun getList(tm: TeamManager): MutableList<Player> {
        when {
            this === DEAMON -> {
                return tm.demonTeam
            }
            this === JOINER -> {
                return tm.joiner
            }
            this === UNKNOWN -> {
                return mutableListOf()
            }
            this === CLEARED -> {
                return tm.clearedTeam
            }
            this === ALIVE -> {
                return tm.aliveTeam
            }
            this === DEAD -> {
                return tm.deadTeam
            }
        }
        return mutableListOf()
    }
}

class RoleManager(var tm: TeamManager) {
    fun assignAll(setting: RoleAssignSetting) {
        setting.deamons.forEach { tm.demonTeam.add(it) }
        setting.alives.forEach { tm.aliveTeam.add(it) }
    }
}

data class RoleAssignSetting(
        var deamons: MutableList<Player>,
        var alives: MutableList<Player>
)

class MoveAnalyzer(var tm: TeamManager, var gm: GameManager) : Listener {
    @Override
    @EventHandler
    fun onMove(p: PlayerMoveEvent) {
        if (gm.isGoingOn && gm.isFreezing) {
            when (GameRole.get(p.player, tm)) {
                GameRole.ALIVE -> {
                    if (isMoved(getDiff(p.from, p.to))) {
                        Bukkit.broadcastMessage("${GameRole.get(p.player, tm).aliase}の${p.player.displayName}さんが動いちゃった!")
                        tm.changeRole(p.player, GameRole.DEAD)
                    }
                }

                GameRole.DEAMON -> {
                    // たるまさんが転んだ表示処理
                    tm.demonManager?.demonShowTitle()
                    gm.isFreezing = (tm.demonManager?.checkFreeze() == true)
                    if(gm.isFreezing){
                        // Freezing!
                    }
                }
            }
        }
    }

    fun getDiff(from: Location, to: Location): Location {
        return Location(
                null,
                abs(from.x - to.x),
                abs(from.y - to.y),
                abs(from.z - to.z),
                abs(from.yaw - to.yaw),
                abs(from.pitch - to.pitch)
        )
    }

    fun isMoved(diff: Location): Boolean {
        if (diff.blockX >= 1 || diff.blockY >= 1 || diff.blockZ >= 1) {
            Daruma.logger.info("player moved")
            return true
        } else {
            if (diff.yaw >= 10 || diff.pitch >= 10) {
                Daruma.logger.info("player yaw or pitch moved")
                return true
            }
        }
        return false
    }
}

enum class Direction(var yaw: Float) {
    South(0.0f), West(90.0f), North(180.0f), East(270.0f);

    companion object {
        fun get(yaw: Float): Direction {
            if (yaw in 0.0..45.0) {
                return South
            } else if (yaw in 45.0..135.0) {
                return West
            } else if (yaw in 135.0..225.0) {
                return North
            } else if (yaw in 225.0..315.0) {
                return East
            }
            return South
        }
    }
}

class DemonManager(var demon: Player) {
    var demonOriginYaw: Float = 0.0f
    fun setOrigin(demon: Player) {
        demonOriginYaw = Direction.get(demon.location.yaw).yaw
    }

    fun setOrigin() {
        setOrigin(demon)
    }

    fun checkFreeze(): Boolean {
        if(getDemonShowTitle() === TitleStrings.DA_){
            return true
        }
        return false
    }

    fun demonShowTitle() {
        TitleBroadCaster.allTitle(getDemonShowTitle().string)
    }

    fun getDemonShowTitle():TitleStrings = TitleStrings.get(calDistanceYaw())

    fun calDistanceYaw(): Float = abs(demon.location.yaw - demonOriginYaw)
}

enum class TitleStrings(var startYaw: Float, var endYaw: Float, var string: String) {
    DA(0.0f,18.0f,"だ"),
    RU(18.0f,36.0f,"る"),
    MA(36.0f,54.0f,"ま"),
    SA(54.0f,72.0f,"さ"),
    N(72.0f,90.0f,"ん"),
    GA(90.0f,108.0f,"が"),
    KO(108.0f,126.0f,"こ"),
    RO(126.0f,144.0f,"ろ"),
    N_(144.0f,162.0f,"ん"),
    DA_(162.0f,180.0f,"だ");

    companion object{
        fun get(yawDistance:Float): TitleStrings {
            for(data in values()){
                if(yawDistance in data.startYaw..data.endYaw){
                    return data
                }
            }
            return DA_
        }
    }
}