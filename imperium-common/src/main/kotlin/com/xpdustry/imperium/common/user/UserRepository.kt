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

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.mapTo
import com.xpdustry.imperium.common.database.transaction
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.toCRC32Muuid
import com.xpdustry.imperium.common.misc.toShortMuuid
import java.net.InetAddress
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import javax.sql.DataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

interface UserRepository {

    suspend fun upsert(uuid: MindustryUUID, name: String, address: InetAddress): Int

    suspend fun searchByName(query: String): Flow<User>

    suspend fun selectById(id: Int): User?

    suspend fun selectByUuid(uuid: MindustryUUID): User?

    suspend fun selectByLastAddress(address: InetAddress): List<User>

    suspend fun selectNamesAndAddressesById(id: Int): User.NamesAndAddresses

    suspend fun incrementJoins(uuid: MindustryUUID)

    suspend fun selectSetting(uuid: MindustryUUID, setting: Setting): Boolean

    suspend fun selectSettings(uuid: MindustryUUID): Map<Setting, Boolean>

    suspend fun updateSetting(uuid: MindustryUUID, setting: Setting, value: Boolean)
}

class SQLUserRepository(private val source: DataSource) : UserRepository, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        runBlocking {
            source.transaction { connection ->
                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `user` (
                            `id`            INT             NOT NULL AUTO_INCREMENT,
                            `uuid`          BINARY(8)       NOT NULL,
                            `last_name`     VARCHAR(64)     NOT NULL,
                            `last_address`  BINARY(16)      NOT NULL,
                            `times_joined`  INTEGER         NOT NULL DEFAULT 0,
                            `last_join`     TIMESTAMP(0)    NOT NULL DEFAULT CURRENT_TIMESTAMP(),
                            `first_join`    TIMESTAMP(0)    NOT NULL DEFAULT `last_join`,
                            CONSTRAINT `pk_user`
                                PRIMARY KEY (`id`),
                            CONSTRAINT `uq_user_uuid`
                                UNIQUE (`uuid`)
                        )
                        """
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `user_name` (
                            `user_id`   INT         NOT NULL,
                            `name`      VARCHAR(64) NOT NULL,
                            CONSTRAINT `pk_user_name`
                                PRIMARY KEY (`user_id`, `name`),
                            CONSTRAINT `fk_user_name_user_id`
                                FOREIGN KEY (`user_id`)
                                REFERENCES `user`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `user_address` (
                            `user_id`   INT         NOT NULL,
                            `address`   BINARY(16)  NOT NULL,
                            CONSTRAINT `pk_user_address`
                                PRIMARY KEY (`user_id`, `address`),
                            CONSTRAINT `fk_user_address_user_id`
                                FOREIGN KEY (`user_id`)
                                REFERENCES `user`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `user_setting` (
                            `user_id`   INT         NOT NULL,
                            `setting`   VARCHAR(64) NOT NULL,
                            `value`     BOOLEAN     NOT NULL,
                            CONSTRAINT `pk_user_setting`
                                PRIMARY KEY (`user_id`, `setting`),
                            CONSTRAINT `fk_user_setting_user_id`
                                FOREIGN KEY (`user_id`)
                                REFERENCES `user`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }
            }
        }
    }

    override suspend fun upsert(uuid: MindustryUUID, name: String, address: InetAddress): Int =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO `user` (`uuid`, `last_name`, `last_address`)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE `last_name` = VALUES(`last_name`), `last_address` = VALUES(`last_address`)
                    """
                        .trimIndent(),
                    Statement.RETURN_GENERATED_KEYS,
                )
                .use { statement1 ->
                    statement1.setBytes(1, uuid.toShortMuuid())
                    statement1.setString(2, name)
                    statement1.setBytes(3, address.address)
                    statement1.executeUpdate()
                    val id =
                        statement1.generatedKeys.use { result ->
                            if (!result.next()) error("No generated keys")
                            result.getInt(1)
                        }

                    connection
                        .prepareStatement(
                            """
                            INSERT IGNORE INTO `user_name` (`user_id`, `name`)
                            VALUES (?, ?)
                            """
                                .trimIndent()
                        )
                        .use { statement2 ->
                            statement2.setInt(1, id)
                            statement2.setString(2, name)
                            statement2.executeUpdate()
                        }

                    connection
                        .prepareStatement(
                            """
                        INSERT IGNORE INTO `user_address` (`user_id`, `address`)
                        VALUES (?, ?)
                        """
                                .trimIndent()
                        )
                        .use { statement3 ->
                            statement3.setInt(1, id)
                            statement3.setBytes(2, address.address)
                            statement3.executeUpdate()
                        }

                    id
                }
        }

    override suspend fun searchByName(query: String) = flow {
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                        SELECT `id`, `uuid`, `last_name`, `last_address`, `times_joined`, `last_join`, `first_join`
                        FROM `user`
                        WHERE `last_name` LIKE ?
                        """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setString(1, "%$query%")
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            emit(result.toUser())
                        }
                    }
                }
        }
    }

    override suspend fun selectById(id: Int) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT *
                    FROM `user`
                    WHERE `id` = ?
                    LIMIT 1;
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.executeQuery().use { result ->
                        if (!result.next()) return@transaction null
                        result.toUser()
                    }
                }
        }

    private fun selectIdByUuid(connection: Connection, uuid: MindustryUUID) =
        connection
            .prepareStatement(
                """
                SELECT `id`
                FROM `user`
                WHERE `uuid` = ?
                LIMIT 1;
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setBytes(1, uuid.toShortMuuid())
                statement.executeQuery().use { result -> if (!result.next()) null else result.getInt("id") }
            }

    override suspend fun selectByUuid(uuid: MindustryUUID): User? {
        TODO("Not yet implemented")
    }

    override suspend fun selectByLastAddress(address: InetAddress): List<User> {
        TODO("Not yet implemented")
    }

    override suspend fun selectNamesAndAddressesById(id: Int): User.NamesAndAddresses =
        source.transaction { connection ->
            val names =
                connection
                    .prepareStatement(
                        """
                    SELECT `name`
                    FROM `user_name`
                    WHERE `user_id` = ?
                    """
                            .trimIndent()
                    )
                    .use { statement ->
                        statement.setInt(1, id)
                        statement.executeQuery().use { result -> result.mapTo(hashSetOf()) { it.getString("name") } }
                    }

            val addresses =
                connection
                    .prepareStatement(
                        """
                    SELECT `address`
                    FROM `user_address`
                    WHERE `user_id` = ?
                    """
                            .trimIndent()
                    )
                    .use { statement ->
                        statement.setInt(1, id)
                        statement.executeQuery().use { result ->
                            result.mapTo(hashSetOf()) { InetAddress.getByAddress(it.getBytes("address")) }
                        }
                    }

            User.NamesAndAddresses(names, addresses)
        }

    override suspend fun incrementJoins(uuid: MindustryUUID) {
        source.transaction { connection ->
            val id = selectIdByUuid(connection, uuid) ?: return@transaction

            connection
                .prepareStatement(
                    """
                    UPDATE `user`
                    SET `times_joined` = `times_joined` + 1, `last_join` = CURRENT_TIMESTAMP()
                    WHERE `id` = ?
                    LIMIT 1;
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.executeUpdate()
                }
        }
    }

    override suspend fun selectSetting(uuid: MindustryUUID, setting: Setting): Boolean =
        source.transaction { connection ->
            val id = selectIdByUuid(connection, uuid) ?: return@transaction setting.default

            connection
                .prepareStatement(
                    """
                    SELECT `value`
                    FROM `user_setting`
                    WHERE `user_id` = ? AND `setting` = ?
                    LIMIT 1;
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.setString(2, setting.name)
                    statement.executeQuery().use { result ->
                        if (!result.next()) return@transaction setting.default
                        result.getBoolean("value")
                    }
                }
        }

    override suspend fun selectSettings(uuid: MindustryUUID): Map<Setting, Boolean> =
        source.transaction { connection ->
            val id = selectIdByUuid(connection, uuid) ?: return@transaction emptyMap()

            connection
                .prepareStatement(
                    """
                    SELECT `setting`, `value`
                    FROM `user_setting`
                    WHERE `user_id` = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.executeQuery().use { result ->
                        buildMap {
                            while (result.next()) {
                                val setting = Setting.valueOfOrNull(result.getString("setting")) ?: continue
                                put(setting, result.getBoolean("value"))
                            }
                        }
                    }
                }
        }

    override suspend fun updateSetting(uuid: MindustryUUID, setting: Setting, value: Boolean): Unit =
        source.transaction { connection ->
            val id = selectIdByUuid(connection, uuid) ?: return@transaction

            connection
                .prepareStatement(
                    """
                    INSERT INTO `user_setting` (`user_id`, `setting`, `value`)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, id)
                    statement.setString(2, setting.name)
                    statement.setBoolean(3, value)
                    statement.executeUpdate()
                }
        }

    private fun ResultSet.toUser() =
        User(
            id = getInt("id"),
            uuid = getBytes("uuid").toCRC32Muuid(),
            lastName = getString("last_name"),
            lastAddress = InetAddress.getByAddress(getBytes("last_address")),
            timesJoined = getInt("times_joined"),
            lastJoin = getTimestamp("last_join").toInstant(),
            firstJoin = getTimestamp("first_join").toInstant(),
        )
}
