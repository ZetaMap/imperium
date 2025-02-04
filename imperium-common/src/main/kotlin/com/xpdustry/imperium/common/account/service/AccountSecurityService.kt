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
import com.xpdustry.imperium.common.security.PasswordHashFunction
import com.xpdustry.imperium.common.security.requirement.PasswordRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AccountSecurityService {

    suspend fun changePassword(id: Int, oldPassword: CharArray, newPassword: CharArray): AccountResult
}

class SimpleAccountSecurityService(
    private val accounts: AccountRepository,
    private val function: PasswordHashFunction,
) : AccountSecurityService {
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
