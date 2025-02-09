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
package com.xpdustry.imperium.common.database

import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> DataSource.transaction(block: suspend (Connection) -> T): T =
    withContext(Dispatchers.IO) {
        val connection = this@transaction.connection
        connection.autoCommit = false
        connection.transactionIsolation = Connection.TRANSACTION_READ_UNCOMMITTED
        try {
            val result = block(connection)
            connection.commit()
            return@withContext result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.close()
        }
    }

fun <T> ResultSet.map(transform: (ResultSet) -> T): List<T> = mapTo(ArrayList(), transform)

fun <T, C : MutableCollection<T>> ResultSet.mapTo(collection: C, transform: (ResultSet) -> T): C {
    while (next()) collection.add(transform(this))
    return collection
}
