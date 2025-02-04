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

import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.MindustrySession
import com.xpdustry.imperium.common.account.repository.AccountRepository
import com.xpdustry.imperium.common.account.repository.MindustrySessionRepository
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.mindustry.MindustryLifecycle
import com.xpdustry.imperium.common.misc.buildCache

interface AccountLookupService {

    suspend fun selectByUsername(account: String): Account?

    suspend fun selectById(account: Int): Account?

    suspend fun selectByDiscord(discord: Int): Account?

    fun selectBySession(key: MindustrySession.Key): Account?
}

class SimpleAccountLookupService(
    private val accounts: AccountRepository,
    private val sessions: MindustrySessionRepository,
    private val messages: Messenger,
    private val mindustry: MindustryLifecycle,
) : AccountLookupService, ImperiumApplication.Listener {
    private val cache = buildCache<MindustrySession.Key, Account> {}

    override fun onImperiumInit() {
        messages.consumer<AccountProfileUpdateMessage> { event ->
            val account = selectById(event.account)!!
            for (session in sessions.selectByAccount(account.id)) {
                if (cache.getIfPresent(session.key) != null) {
                    cache.put(session.key, account)
                }
            }
        }
    }

    override suspend fun selectByUsername(account: String): Account? {
        TODO("Not yet implemented")
    }

    override suspend fun selectById(account: Int): Account? {
        TODO("Not yet implemented")
    }

    override suspend fun selectByDiscord(discord: Int): Account? {
        TODO("Not yet implemented")
    }

    override fun selectBySession(key: MindustrySession.Key): Account? {
        TODO("Not yet implemented")
    }
}
