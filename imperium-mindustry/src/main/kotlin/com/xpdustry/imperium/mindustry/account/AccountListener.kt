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
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountLookupService
import com.xpdustry.imperium.common.account.AccountProfileService
import com.xpdustry.imperium.common.account.AccountProfileUpdateMessage
import com.xpdustry.imperium.common.account.AccountSecurityService
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.user.Setting
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.sessionKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Iconc
import mindustry.gen.Player

class AccountListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val lookup = instances.get<AccountLookupService>()
    private val profile = instances.get<AccountProfileService>()
    private val security = instances.get<AccountSecurityService>()
    private val users = instances.get<UserManager>()
    private val playtime = ConcurrentHashMap<Player, Long>()
    private val messenger = instances.get<Messenger>()

    override fun onImperiumInit() {
        messenger.consumer<AccountProfileUpdateMessage.Achievement> { message ->
            if (!message.completed) return@consumer
            for (player in Entities.getPlayersAsync()) {
                val account = lookup.selectBySessionCached(player.sessionKey)
                if (account != null && account.id == message.account) {
                    Call.warningToast(
                        player.con,
                        Iconc.infoCircle.code,
                        "Congrats, you obtained the achievement [orange]${message.achievement.name.lowercase()}.",
                    )
                    break
                }
            }
        }
    }

    @TaskHandler(delay = 1L, interval = 1L, unit = MindustryTimeUnit.MINUTES)
    internal fun onPlaytimeAchievementCheck() {
        for (player in Entities.getPlayers()) {
            val account = lookup.selectBySessionCached(player.sessionKey) ?: continue
            val now = System.currentTimeMillis()
            val playtime = (now - (playtime[player] ?: now)).milliseconds
            ImperiumScope.MAIN.launch { checkPlaytimeAchievements(account, playtime) }
        }
    }

    @EventHandler
    internal fun onPlayerJoin(event: EventType.PlayerJoin) {
        playtime[event.player] = System.currentTimeMillis()
        ImperiumScope.MAIN.launch { users.incrementJoins(event.player.identity) }
    }

    @EventHandler
    internal fun onGameOver(event: EventType.GameOverEvent) {
        Entities.getPlayers().forEach { player ->
            ImperiumScope.MAIN.launch {
                val account = lookup.selectBySessionCached(player.sessionKey) ?: return@launch
                profile.incrementGames(account.id)
            }
        }
    }

    @EventHandler
    internal fun onPlayerLeave(event: EventType.PlayerLeave) {
        val playerPlaytime = playtime.remove(event.player)
        ImperiumScope.MAIN.launch {
            val now = System.currentTimeMillis()
            val account = lookup.selectBySessionCached(event.player.sessionKey)
            if (account != null) {
                val playtime = (now - (playerPlaytime ?: now)).milliseconds
                profile.incrementPlaytime(account.id, playtime)
                checkPlaytimeAchievements(account, playtime)
            }
            if (!users.getSetting(event.player.uuid(), Setting.REMEMBER_LOGIN)) {
                security.logout(event.player.sessionKey)
            }
        }
    }

    private suspend fun checkPlaytimeAchievements(account: Account, playtime: Duration) {
        if (playtime >= 8.hours) {
            profile.updateAchievement(account.id, Achievement.GAMER, true)
        }
        checkDailyLoginAchievement(account, playtime)
        val total = playtime + account.playtime
        if (total >= 1.days) {
            profile.updateAchievement(account.id, Achievement.DAY, true)
        }
        if (total >= 7.days) {
            profile.updateAchievement(account.id, Achievement.WEEK, true)
        }
        if (total >= 30.days) {
            profile.updateAchievement(account.id, Achievement.MONTH, true)
        }
    }

    private suspend fun checkDailyLoginAchievement(account: Account, playtime: Duration) {
        if (playtime < 30.minutes) return
        val now = System.currentTimeMillis()
        var last = account.metadata[PLAYTIME_ACHIEVEMENT_LAST_GRANT]?.toLongOrNull()
        var increment = account.metadata[PLAYTIME_ACHIEVEMENT_INCREMENT]?.toIntOrNull() ?: 0

        if (last == null) {
            last = now
            increment++
        } else {
            val elapsed = (now - last).coerceAtLeast(0).milliseconds
            if (elapsed < 1.days) {
                return
            } else if (elapsed < (2.days + 12.hours)) {
                last = now
                increment++
            } else {
                last = now
                increment = 1
            }
        }

        if (increment >= 7 && Achievement.ACTIVE !in account.achievements) {
            profile.updateAchievement(account.id, Achievement.ACTIVE, true)
        }
        if (increment >= 30 && Achievement.HYPER !in account.achievements) {
            profile.updateAchievement(account.id, Achievement.HYPER, true)
        }

        profile.updateMetadata(
            account.id,
            mapOf(
                PLAYTIME_ACHIEVEMENT_LAST_GRANT to last.toString(),
                PLAYTIME_ACHIEVEMENT_INCREMENT to increment.toString(),
            ),
        )
    }

    companion object {
        private const val PLAYTIME_ACHIEVEMENT_LAST_GRANT = "playtime_achievement_last_grant"
        private const val PLAYTIME_ACHIEVEMENT_INCREMENT = "playtime_achievement_increment"
    }
}
