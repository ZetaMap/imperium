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
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.security.PasswordHashFunction
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

interface AccountSessionService {

    suspend fun selectByKey(key: MindustrySession.Key): MindustrySession?

    suspend fun selectByAccount(account: Int): List<MindustrySession>

    suspend fun login(key: MindustrySession.Key, username: String, password: CharArray): AccountResult

    suspend fun logout(key: MindustrySession.Key, all: Boolean = false)
}

@Serializable
data class AccountSessionMessage(val player: MindustrySession.Key, val type: Type) : Message {
    enum class Type {
        LOGIN,
        LOGOUT,
    }
}

class SimpleAccountSessionService(
    private val accounts: AccountRepository,
    private val sessions: AccountSessionRepository,
    private val function: PasswordHashFunction,
    private val messenger: Messenger,
    private val config: ImperiumConfig,
) : AccountSessionService {
    // TODO Check session expiration
    override suspend fun selectByKey(key: MindustrySession.Key): MindustrySession? {
        return sessions.selectByKey(key)
    }

    // TODO Check session expiration
    override suspend fun selectByAccount(account: Int): List<MindustrySession> {
        return sessions.selectByAccount(account)
    }

    override suspend fun login(key: MindustrySession.Key, username: String, password: CharArray): AccountResult {
        if (sessions.selectByKey(key)?.expired == false) {
            return AccountResult.AlreadyLogged
        }
        val account = accounts.selectByUsername(username) ?: return AccountResult.NotFound
        val hash0 = accounts.selectPasswordById(account.id)!!
        val hash1 = withContext(Dispatchers.IO) { function.hash(password, hash0.salt) }
        if (hash0 != hash1) {
            return AccountResult.WrongPassword
        }
        sessions.upsertSession(
            MindustrySession(key, config.server.name, account.id, Instant.now().plus(30, ChronoUnit.DAYS))
        )
        messenger.publish(AccountSessionMessage(key, AccountSessionMessage.Type.LOGIN), local = true)
        return AccountResult.Success(account.id)
    }

    override suspend fun logout(key: MindustrySession.Key, all: Boolean) {
        val success =
            if (all) {
                val account = sessions.selectByKey(key) ?: return
                sessions.deleteByAccount(account.account)
            } else {
                sessions.deleteByKey(key)
            }
        if (success) {
            messenger.publish(AccountSessionMessage(key, AccountSessionMessage.Type.LOGOUT), local = true)
        }
    }
}
