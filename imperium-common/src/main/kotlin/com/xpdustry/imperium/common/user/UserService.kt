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
package com.xpdustry.imperium.common.user

import com.xpdustry.imperium.common.misc.MindustryUUID
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow

interface UserService {

    suspend fun upsertUser(uuid: MindustryUUID, name: String, address: InetAddress): Int

    suspend fun searchByName(query: String): Flow<User>

    suspend fun selectById(id: Int): User?

    suspend fun selectByUuid(uuid: MindustryUUID): User?

    suspend fun selectByAddress(address: InetAddress): Flow<User>

    suspend fun selectNamesAndAddressesById(id: Int): User.NamesAndAddresses

    suspend fun incrementJoins(uuid: MindustryUUID)
}

class SimpleUserService(private val repository: UserRepository) : UserService {

    override suspend fun upsertUser(uuid: MindustryUUID, name: String, address: InetAddress): Int {
        return repository.upsertUser(uuid, name, address)
    }

    override suspend fun searchByName(query: String): Flow<User> {
        return repository.searchByName(query)
    }

    override suspend fun selectById(id: Int): User? {
        return repository.selectById(id)
    }

    override suspend fun selectByUuid(uuid: MindustryUUID): User? {
        return repository.selectByUuid(uuid)
    }

    override suspend fun selectByAddress(address: InetAddress): Flow<User> {
        return repository.selectByAddress(address)
    }

    override suspend fun selectNamesAndAddressesById(id: Int): User.NamesAndAddresses {
        return repository.selectNamesAndAddressesById(id)
    }

    override suspend fun incrementJoins(uuid: MindustryUUID) {
        repository.incrementJoins(uuid)
    }
}
