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
import com.xpdustry.imperium.common.security.requirement.PasswordRequirement
import com.xpdustry.imperium.common.security.requirement.UsernameRequirement
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

interface AccountSecurityService {

    suspend fun login(key: MindustrySession.Key, username: String, password: CharArray): AccountResult

    suspend fun logout(key: MindustrySession.Key, all: Boolean = false)

    suspend fun register(username: String, password: CharArray): AccountResult

    suspend fun changePassword(id: Int, oldPassword: CharArray, newPassword: CharArray): AccountResult
}

@Serializable
data class AccountAuthenticationMessage(val player: MindustrySession.Key, val type: Type) : Message {
    enum class Type {
        LOGIN,
        LOGOUT,
    }
}

class SimpleAccountSecurityService(
    private val accounts: AccountRepository,
    private val function: PasswordHashFunction,
    private val legacy: LegacyAccountRepository,
    private val passwords: PasswordHashFunction,
    private val sessions: AccountSessionRepository,
    private val messenger: Messenger,
    private val config: ImperiumConfig,
) : AccountSecurityService {
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
        messenger.publish(AccountAuthenticationMessage(key, AccountAuthenticationMessage.Type.LOGIN), local = true)
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
            messenger.publish(AccountAuthenticationMessage(key, AccountAuthenticationMessage.Type.LOGOUT), local = true)
        }
    }

    override suspend fun register(username: String, password: CharArray): AccountResult {
        if (accounts.existsByUsername(username)) {
            return AccountResult.AlreadyRegistered
        }
        if (legacy.existsByUsername(username)) {
            return AccountResult.InvalidUsername(listOf(UsernameRequirement.Reserved(username)))
        }
        val missingPwdRequirements = PasswordRequirement.DEFAULT.filter { !it.check(password) }
        if (missingPwdRequirements.isNotEmpty()) {
            return AccountResult.InvalidPassword(missingPwdRequirements)
        }
        val missingUsrRequirements = UsernameRequirement.DEFAULT.filter { !it.check(username) }
        if (missingUsrRequirements.isNotEmpty()) {
            return AccountResult.InvalidUsername(missingUsrRequirements)
        }
        val hash = withContext(Dispatchers.IO) { passwords.hash(password) }
        return AccountResult.Success(accounts.insertAccount(username, hash))
    }

    override suspend fun changePassword(id: Int, oldPassword: CharArray, newPassword: CharArray): AccountResult {
        val oldHash0 = accounts.selectPasswordById(id) ?: return AccountResult.NotFound
        val oldHash1 = withContext(Dispatchers.IO) { function.hash(oldPassword, oldHash0.salt) }
        if (oldHash0 != oldHash1) {
            return AccountResult.WrongPassword
        }
        val missing = PasswordRequirement.DEFAULT.filter { !it.check(newPassword) }
        if (missing.isNotEmpty()) {
            return AccountResult.InvalidPassword(missing)
        }
        val newHash = withContext(Dispatchers.IO) { function.hash(newPassword) }
        accounts.updatePassword(id, newHash)
        return AccountResult.Success(id)
    }
}
