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
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.mindustry.MindustryRuntime
import java.util.concurrent.ConcurrentHashMap

interface AccountLookupService {

    suspend fun selectByUsername(account: String): Account?

    suspend fun selectById(account: Int): Account?

    suspend fun selectByDiscord(discord: Long): Account?

    fun selectBySessionCached(key: MindustrySession.Key): Account?
}

class SimpleAccountLookupService(
    private val accounts: AccountRepository,
    private val sessions: AccountSessionRepository,
    private val messages: Messenger,
    private val mindustry: MindustryRuntime,
) : AccountLookupService, ImperiumApplication.Listener {
    private val cache = ConcurrentHashMap<MindustrySession.Key, Account>()

    override fun onImperiumInit() {
        messages.consumer<AccountProfileUpdateMessage> { event ->
            val account = selectById(event.account)!!
            for (session in sessions.selectByAccount(account.id)) {
                cache.computeIfPresent(session.key) { _, _ -> account }
            }
        }

        mindustry.addPlayerListener(
            { cache[it] = accounts.selectById(sessions.selectByKey(it)?.account ?: return@addPlayerListener)!! },
            { cache.remove(it) },
        )
    }

    override suspend fun selectByUsername(account: String): Account? {
        return accounts.selectByUsername(account)
    }

    override suspend fun selectById(account: Int): Account? {
        return accounts.selectById(account)
    }

    override suspend fun selectByDiscord(discord: Long): Account? {
        return accounts.selectByDiscord(discord)
    }

    override fun selectBySessionCached(key: MindustrySession.Key): Account? {
        return cache[key]
    }
}
