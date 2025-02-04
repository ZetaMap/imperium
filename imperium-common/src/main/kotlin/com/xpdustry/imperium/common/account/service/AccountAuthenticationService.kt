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

import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.account.MindustrySession
import com.xpdustry.imperium.common.account.repository.AccountRepository
import com.xpdustry.imperium.common.account.repository.MindustrySessionRepository
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.security.PasswordHashFunction
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AccountAuthenticationService {

    suspend fun login(key: MindustrySession.Key, username: String, password: CharArray): AccountResult

    suspend fun logout(key: MindustrySession.Key, all: Boolean = false): Boolean
}

class SimpleAccountAuthenticationService(
    private val accounts: AccountRepository,
    private val sessions: MindustrySessionRepository,
    private val function: PasswordHashFunction,
    private val config: ImperiumConfig,
) : AccountAuthenticationService {
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
        return AccountResult.Success(account.id)
    }

    override suspend fun logout(key: MindustrySession.Key, all: Boolean): Boolean {
        return if (all) {
            val account = sessions.selectByKey(key) ?: return false
            sessions.deleteByAccount(account.account)
        } else {
            sessions.deleteByKey(key)
        }
    }
}
