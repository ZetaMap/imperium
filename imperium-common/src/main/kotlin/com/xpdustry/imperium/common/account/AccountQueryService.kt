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

import com.xpdustry.imperium.common.application.ImperiumApplication

interface AccountQueryService {

    suspend fun selectByUsername(account: String): Account?

    suspend fun selectById(account: Int): Account?

    suspend fun existsById(account: Int): Boolean

    suspend fun selectByDiscord(discord: Long): Account?
}

class SimpleAccountQueryService(private val accounts: AccountRepository) :
    AccountQueryService, ImperiumApplication.Listener {
    override suspend fun selectByUsername(account: String): Account? {
        return accounts.selectByUsername(account)
    }

    override suspend fun selectById(account: Int): Account? {
        return accounts.selectById(account)
    }

    override suspend fun existsById(account: Int): Boolean {
        return accounts.existsById(account)
    }

    override suspend fun selectByDiscord(discord: Long): Account? {
        return accounts.selectByDiscord(discord)
    }
}
