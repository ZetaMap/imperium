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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.misc.MindustryUUID
import kotlinx.serialization.Serializable

interface UserSettingService {

    fun selectSetting(uuid: MindustryUUID, setting: Setting): Boolean {
        return selectSettings(uuid)[setting] == true
    }

    fun selectSettings(uuid: MindustryUUID): Map<Setting, Boolean>

    suspend fun updateSetting(uuid: MindustryUUID, setting: Setting, value: Boolean)

    suspend fun toggleSetting(uuid: MindustryUUID, setting: Setting)
}

@Serializable
data class SettingChangedMessage(val player: MindustryUUID, val setting: Setting, val value: Boolean) : Message

class SimpleUserSettingService() {}
