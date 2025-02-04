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
import com.xpdustry.imperium.common.account.repository.AccountRepository
import com.xpdustry.imperium.common.account.repository.LegacyAccountRepository
import com.xpdustry.imperium.common.security.PasswordHashFunction
import com.xpdustry.imperium.common.security.requirement.PasswordRequirement
import com.xpdustry.imperium.common.security.requirement.UsernameRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AccountRegistrationService {

    suspend fun register(username: String, password: CharArray): AccountResult
}

class SimpleAccountRegistrationService(
    private val accounts: AccountRepository,
    private val legacy: LegacyAccountRepository,
    private val passwords: PasswordHashFunction,
) : AccountRegistrationService {
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
}
