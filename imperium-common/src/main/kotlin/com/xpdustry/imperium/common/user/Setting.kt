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

import com.xpdustry.imperium.common.account.Achievement

enum class Setting(val default: Boolean, val achievement: Achievement? = null) {
    SHOW_WELCOME_MESSAGE(true),
    RESOURCE_HUD(true),
    REMEMBER_LOGIN(true),
    DOUBLE_TAP_TILE_LOG(true),
    ANTI_BAN_EVADE(false),
    AUTOMATIC_LANGUAGE_DETECTION(true),
    UNDERCOVER(false),
    RAINBOW_NAME(false, achievement = Achievement.SUPPORTER);

    companion object {
        fun valueOfOrNull(name: String) =
            try {
                valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
