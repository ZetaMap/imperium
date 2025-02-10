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
package com.xpdustry.imperium.mindustry.chat

import arc.graphics.Color
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.flex.placeholder.PlaceholderContext
import com.xpdustry.flex.processor.Processor
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.user.Setting
import com.xpdustry.imperium.common.user.UserSettingService
import com.xpdustry.imperium.mindustry.account.AccountCacheService
import com.xpdustry.imperium.mindustry.bridge.DiscordAudience
import com.xpdustry.imperium.mindustry.misc.toHexString
import java.text.DecimalFormat
import mindustry.graphics.Pal

class ImperiumPlaceholderProcessor(private val cache: AccountCacheService, private val settings: UserSettingService) :
    Processor<PlaceholderContext, String?> {

    override fun process(context: PlaceholderContext): String? {
        return when (context.query.lowercase()) {
            "hours" -> {
                val hours =
                    when (val subject = context.subject) {
                        is DiscordAudience -> subject.hours
                        is PlayerAudience ->
                            cache.selectByPlayer(subject.player)?.playtime?.inWholeHours?.takeUnless {
                                settings.selectSetting(subject.player.uuid(), Setting.UNDERCOVER)
                            }

                        else -> null
                    }
                hours?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
            }

            "is_discord" ->
                when (context.subject) {
                    is DiscordAudience -> "discord"
                    else -> ""
                }

            "rank_color" -> {
                val rank =
                    when (val subject = context.subject) {
                        is DiscordAudience -> subject.rank
                        is PlayerAudience ->
                            cache.selectByPlayer(subject.player)?.rank?.takeUnless {
                                settings.selectSetting(subject.player.uuid(), Setting.UNDERCOVER)
                            } ?: Rank.EVERYONE

                        else -> Rank.EVERYONE
                    }
                rank.toColor().toHexString()
            }

            "name_colored" -> {
                val audience = context.subject
                val name = context.subject.metadata[StandardKeys.DECORATED_NAME] ?: return null
                if (
                    audience is PlayerAudience &&
                        Achievement.SUPPORTER in cache.selectByPlayer(audience.player)?.achievements.orEmpty() &&
                        settings.selectSetting(audience.player.uuid(), Setting.RAINBOW_NAME) &&
                        !settings.selectSetting(audience.player.uuid(), Setting.UNDERCOVER)
                ) {
                    buildString {
                        val plain = ComponentStringBuilder.plain(context.subject.metadata).append(name).toString()
                        val initial = (((System.currentTimeMillis() / 1000L) % 60) / 60F) * 360F
                        val color = Color().a(1F)
                        for ((index, char) in plain.withIndex()) {
                            color.fromHsv(initial + (index * 8F), 0.55F, 0.9F)
                            append("[#")
                            append(color)
                            append(']')
                            append(char)
                        }
                    }
                } else {
                    ComponentStringBuilder.mindustry(context.subject.metadata).append(name).toString()
                }
            }

            else -> null
        }
    }

    private fun Rank.toColor() =
        when (this) {
            Rank.EVERYONE -> Color.lightGray
            Rank.VERIFIED -> Pal.accent
            Rank.OVERSEER -> Color.green
            Rank.MODERATOR -> Color.royal
            Rank.ADMIN,
            Rank.OWNER -> Color.scarlet
        }

    companion object {
        val CHAOTIC_HOUR_FORMAT = DecimalFormat("000")
    }
}
