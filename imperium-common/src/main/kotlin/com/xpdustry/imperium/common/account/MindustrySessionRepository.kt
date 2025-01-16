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
import com.xpdustry.imperium.common.database.transaction
import java.net.InetAddress
import java.sql.Statement
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.runBlocking

interface MindustrySessionRepository {

    suspend fun upsertSession(session: MindustrySession): Int?

    suspend fun selectByKey(key: MindustrySession.Key): MindustrySession?

    suspend fun deleteByKey(key: MindustrySession.Key): Boolean

    suspend fun selectByAccount(id: Int): List<MindustrySession>

    suspend fun deleteByAccount(id: Int): Boolean
}

class SQLSessionRepository(private val source: DataSource, private val accounts: AccountRepository) :
    MindustrySessionRepository, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        runBlocking {
            source.transaction { connection ->
                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `account_session_mindustry` (
                            `account_id`    INT             NOT NULL,
                            `uuid`          VARBINARY(16)   NOT NULL,
                            `usid`          VARBINARY(8)    NOT NULL,
                            `address`       VARBINARY(16)   NOT NULL,
                            `server`        VARCHAR(32)     NOT NULL,
                            `expiration`    TIMESTAMP(0)    NOT NULL,
                            CONSTRAINT `pk_account_session_mindustry`
                                PRIMARY KEY (`account_id`, `uuid`, `usid`, `address`),
                            CONSTRAINT `fk_account_session_mindustry_id`
                                FOREIGN KEY (`account_id`)
                                REFERENCES `account`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun upsertSession(session: MindustrySession): Int? {
        if (!accounts.existsById(session.account)) return null
        return source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO `account_session_mindustry` (`account_id`, `uuid`, `usid`, `address`, `server`, `expiration`)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE `server` = VALUES(`server`), `expiration` = VALUES(`expiration`);
                    """
                        .trimIndent(),
                    Statement.RETURN_GENERATED_KEYS,
                )
                .use { statement ->
                    statement.setInt(1, session.account)
                    statement.setBytes(2, Base64.decode(session.key.uuid))
                    statement.setBytes(3, Base64.decode(session.key.usid))
                    statement.setBytes(4, session.key.address.address)
                    statement.setString(5, session.server)
                    statement.setTimestamp(6, Timestamp.from(session.expiration))
                    if (statement.executeUpdate() == 0) {
                        return@transaction null
                    }
                    statement.generatedKeys.use { keys ->
                        if (!keys.next()) return@transaction null
                        keys.getInt(1)
                    }
                }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun selectByKey(key: MindustrySession.Key) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT `server`, `account_id`, `expiration` FROM `account_session_mindustry`
                    WHERE `uuid` = ? AND `usid` = ? AND `address` = ?
                    LIMIT 1;
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setBytes(1, Base64.decode(key.uuid))
                    statement.setBytes(2, Base64.decode(key.usid))
                    statement.setBytes(3, key.address.address)
                    statement.executeQuery().use { result ->
                        if (!result.next()) return@transaction null
                        MindustrySession(
                            key,
                            result.getString("server"),
                            result.getInt("account_id"),
                            result.getTimestamp("expiration").toInstant(),
                        )
                    }
                }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun deleteByKey(key: MindustrySession.Key): Boolean =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    DELETE FROM `account_session_mindustry`
                    WHERE `uuid` = ? AND `usid` = ? AND `address` = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setBytes(1, Base64.decode(key.uuid))
                    statement.setBytes(2, Base64.decode(key.usid))
                    statement.setBytes(3, key.address.address)
                    statement.executeUpdate() > 0
                }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun selectByAccount(id: Int) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT `uuid`, `usid`, `address`, `server`, `account_id`, `expiration` FROM `account_session_mindustry`
                    WHERE `account_id` = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.executeQuery().use { result ->
                        val sessions = ArrayList<MindustrySession>()
                        while (result.next()) {
                            sessions +=
                                MindustrySession(
                                    MindustrySession.Key(
                                        Base64.encode(result.getBytes("uuid")),
                                        Base64.encode(result.getBytes("usid")),
                                        InetAddress.getByAddress(result.getBytes("address")),
                                    ),
                                    result.getString("server"),
                                    result.getInt("account_id"),
                                    result.getTimestamp("expiration").toInstant(),
                                )
                        }
                        sessions
                    }
                }
        }

    override suspend fun deleteByAccount(id: Int) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    DELETE FROM `account_session_mindustry`
                    WHERE `account_id` = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.executeUpdate() > 0
                }
        }
}
