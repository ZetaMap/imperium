/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.mindustry.permission

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.permission.MutablePermissionTree
import com.xpdustry.distributor.api.permission.PermissionTree
import com.xpdustry.distributor.api.permission.rank.EnumRankNode
import com.xpdustry.distributor.api.permission.rank.RankNode
import com.xpdustry.distributor.api.permission.rank.RankPermissionSource
import com.xpdustry.distributor.api.permission.rank.RankProvider
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountSessionMessage
import com.xpdustry.imperium.common.account.AccountSessionService
import com.xpdustry.imperium.common.account.AccountUpdateMessage
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.MindustrySession
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.user.Setting
import com.xpdustry.imperium.common.user.SettingChangedMessage
import com.xpdustry.imperium.common.user.UserSettingService
import com.xpdustry.imperium.mindustry.account.AccountCacheService
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.registerDistributorService
import com.xpdustry.imperium.mindustry.misc.sessionKey
import java.util.Collections
import mindustry.game.EventType
import mindustry.gen.Player

class ImperiumPermissionListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val plugin = instances.get<MindustryPlugin>()
    private val config = instances.get<ImperiumConfig>()
    private val cache = instances.get<AccountCacheService>()
    private val sessions = instances.get<AccountSessionService>()
    private val messenger = instances.get<Messenger>()
    private val settings = instances.get<UserSettingService>()

    override fun onImperiumInit() {
        registerDistributorService<RankPermissionSource>(plugin, ImperiumRankPermissionSource())
        registerDistributorService<RankProvider>(plugin, ImperiumRankProvider())

        messenger.consumer<AccountUpdateMessage.Rank> { message ->
            val keys = sessions.selectByAccount(message.account).map(MindustrySession::key)
            for (player in Entities.getPlayersAsync()) {
                if (player.sessionKey in keys) {
                    syncAdminStatus(player)
                }
            }
        }

        messenger.consumer<AccountSessionMessage> { message ->
            syncAdminStatus(Entities.getPlayersAsync().find { it.sessionKey == message.player } ?: return@consumer)
        }

        messenger.consumer<SettingChangedMessage> { message ->
            if (message.setting == Setting.UNDERCOVER) {
                for (player in Entities.getPlayersAsync()) {
                    if (player.uuid() == message.player) {
                        syncAdminStatus(player)
                    }
                }
            }
        }
    }

    @EventHandler
    internal fun onPlayerJoin(event: EventType.PlayerJoin) {
        syncAdminStatus(event.player)
    }

    private fun syncAdminStatus(player: Player) {
        val undercover = settings.selectSetting(player.uuid(), Setting.UNDERCOVER)
        val rank = cache.selectByPlayer(player)?.rank ?: Rank.EVERYONE
        player.admin(if (undercover) false else rank >= Rank.OVERSEER)
    }

    inner class ImperiumRankProvider : RankProvider {
        override fun getRanks(player: Player): List<RankNode> {
            val account = cache.selectByPlayer(player)
            val nodes = ArrayList<RankNode>()
            nodes += EnumRankNode.linear(account?.rank ?: Rank.EVERYONE, "imperium", true)
            nodes += account?.achievements.orEmpty().map { EnumRankNode.singular(it, "imperium") }
            return Collections.unmodifiableList(nodes)
        }
    }

    inner class ImperiumRankPermissionSource : RankPermissionSource {
        override fun getRankPermissions(node: RankNode): PermissionTree {
            val tree = MutablePermissionTree.create()
            tree.setPermission("imperium.gamemode.${config.mindustry.gamemode.name.lowercase()}", true)
            if (node is EnumRankNode<*> && node.value is Rank) {
                (node.value as Rank).getRanksBelow().forEach { rank ->
                    tree.setPermission("imperium.rank.${rank.name.lowercase()}", true)
                }
            }
            if (node is EnumRankNode<*> && node.value is Achievement) {
                tree.setPermission("imperium.achievement.${(node.value as Achievement).name.lowercase()}", true)
            }
            return tree
        }
    }
}
