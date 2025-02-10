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
package com.xpdustry.imperium.mindustry.user

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.MindustryUSID
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.user.Setting
import com.xpdustry.imperium.common.user.UserRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mindustry.game.EventType
import mindustry.gen.Player

typealias SettingMap = Map<Setting, Boolean>

interface UserSettingService {

    fun selectSetting(player: Player, setting: Setting): Boolean {
        return selectSettings(player)[setting] == true
    }

    fun selectSettings(player: Player): SettingMap

    suspend fun toggleSetting(player: Player, setting: Setting)
}

@Serializable
data class UserSettingUpdateMessage(
    val uuid: MindustryUUID,
    val usid: MindustryUSID,
    val setting: Setting,
    val value: Boolean,
) : Message

class SimpleUserSettingService(private val repository: UserRepository, private val messenger: Messenger) :
    UserSettingService, ImperiumApplication.Listener {
    private val cache = ConcurrentHashMap<MUUID, SettingMap>()

    override fun onImperiumInit() {
        messenger.consumer<UserSettingUpdateMessage> { event ->
            val muuid = MUUID.of(event.uuid, event.usid)
            val settings = repository.selectSettings(event.uuid)
            cache.computeIfPresent(muuid) { _, _ -> settings + (event.setting to event.value) }
        }
    }

    @EventHandler
    internal fun onPlayerJoin(event: EventType.PlayerJoin) {
        ImperiumScope.MAIN.launch { cache[MUUID.from(event.player)] = repository.selectSettings(event.player.uuid()) }
    }

    @EventHandler
    internal fun onPlayerQuit(event: EventType.PlayerLeave) {
        cache.remove(MUUID.from(event.player))
    }

    override fun selectSettings(player: Player): SettingMap = cache[MUUID.from(player)] ?: emptyMap()

    override suspend fun toggleSetting(player: Player, setting: Setting) {
        val settings = repository.selectSettings(player.uuid())
        val value = !(settings[setting] ?: setting.default)
        repository.updateSetting(player.uuid(), setting, value)
        cache[MUUID.from(player)] = settings + (setting to value)
    }
}
