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
package com.xpdustry.imperium.common.account.service

import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.account.repository.AccountRepository
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import kotlin.time.Duration
import kotlinx.serialization.Serializable

interface AccountProfileService {

    suspend fun updateDiscord(id: Int, discord: Long)

    suspend fun incrementGames(id: Int)

    suspend fun incrementPlaytime(id: Int, duration: Duration)

    suspend fun updateAchievement(id: Int, achievement: Achievement, completed: Boolean)

    suspend fun updateRank(id: Int, rank: Rank)

    suspend fun updateMetadata(id: Int, key: String, value: String)

    suspend fun updateMetadata(id: Int, entries: Map<String, String>)
}

@Serializable data class AccountProfileUpdateMessage(val account: Int) : Message

class SimpleAccountProfileService(private val accounts: AccountRepository, private val messenger: Messenger) :
    AccountProfileService {
    override suspend fun updateDiscord(id: Int, discord: Long) {
        accounts.updateDiscord(id, discord)
        messenger.publish(AccountProfileUpdateMessage(id), local = true)
    }

    override suspend fun incrementGames(id: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun incrementPlaytime(id: Int, duration: Duration) {
        TODO("Not yet implemented")
    }

    override suspend fun updateAchievement(id: Int, achievement: Achievement, completed: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun updateRank(id: Int, rank: Rank) {
        TODO("Not yet implemented")
    }

    override suspend fun updateMetadata(id: Int, key: String, value: String) {
        TODO("Not yet implemented")
    }

    override suspend fun updateMetadata(id: Int, entries: Map<String, String>) {
        TODO("Not yet implemented")
    }
}
