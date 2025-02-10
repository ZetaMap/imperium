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
package com.xpdustry.imperium.mindustry.account

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.imperium.common.account.Account
import com.xpdustry.imperium.common.account.AccountQueryService
import com.xpdustry.imperium.common.account.AccountSessionService
import com.xpdustry.imperium.common.account.AccountUpdateMessage
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.mindustry.misc.sessionKey
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import mindustry.game.EventType
import mindustry.gen.Player

interface AccountCacheService {
    fun selectByPlayer(player: Player): Account?
}

class SimpleAccountCacheService(
    private val sessions: AccountSessionService,
    private val queries: AccountQueryService,
    private val messenger: Messenger,
) : AccountCacheService, ImperiumApplication.Listener {
    private val cache = ConcurrentHashMap<MUUID, Account>()

    override fun onImperiumInit() {
        // TODO Remove MindustrySessionKey
        messenger.consumer<AccountUpdateMessage> { event ->
            val account = queries.selectById(event.account)!!
            for (session in sessions.selectByAccount(account.id)) {
                cache.computeIfPresent(MUUID.of(session.key.uuid, session.key.usid)) { _, _ -> account }
            }
        }
    }

    @EventHandler
    internal fun onPlayerJoin(event: EventType.PlayerJoin) {
        ImperiumScope.MAIN.launch {
            val id = sessions.selectByKey(event.player.sessionKey)?.account ?: return@launch
            val account = queries.selectById(id) ?: return@launch
            cache[MUUID.from(event.player)] = account
        }
    }

    @EventHandler
    internal fun onPlayerQuit(event: EventType.PlayerLeave) {
        cache.remove(MUUID.from(event.player))
    }

    override fun selectByPlayer(player: Player): Account? {
        return cache[MUUID.from(player)]
    }
}
