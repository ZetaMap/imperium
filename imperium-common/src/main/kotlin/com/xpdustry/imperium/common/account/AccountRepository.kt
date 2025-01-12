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
import com.xpdustry.imperium.common.collection.enumSetOf
import com.xpdustry.imperium.common.database.transaction
import com.xpdustry.imperium.common.hash.Hash
import java.sql.PreparedStatement
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and

interface AccountRepository {

    suspend fun selectByUsername(username: String): Account?

    suspend fun selectById(id: Int): Account?

    suspend fun selectByDiscord(discord: Long): Account?

    suspend fun updateDiscord(id: Int, discord: Long): Boolean

    suspend fun incrementGames(id: Int): Boolean

    suspend fun incrementPlaytime(id: Int, duration: Duration): Boolean

    suspend fun updateRank(id: Int, rank: Rank): Boolean

    suspend fun updatePassword(id: Int, password: Hash): Boolean

    suspend fun updateAchievement(id: Int, achievement: Achievement, completed: Boolean): Boolean

    suspend fun updateMetadata(id: Int, key: String, value: String): Boolean
}

class SQLAccountRepository(private val source: DataSource) :
    AccountRepository, ImperiumApplication.Listener {

    override fun onImperiumInit() {
        runBlocking {
            source.transaction { connection ->
                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `account` (
                            `id`                INT             NOT NULL AUTO_INCREMENT,
                            `username`          VARCHAR(32)     NOT NULL,
                            `password_hash`     BINARY(64)      NOT NULL,
                            `password_salt`     BINARY(64)      NOT NULL,
                            `games`             INT             NOT NULL DEFAULT 0,
                            `playtime`          BIGINT          NOT NULL DEFAULT 0,
                            `rank`              VARCHAR(32)     NOT NULL DEFAULT 'EVERYONE',
                            `creation`          TIMESTAMP(0)    NOT NULL DEFAULT CURRENT_TIMESTAMP(),
                            `legacy`            BOOLEAN         NOT NULL DEFAULT FALSE,
                            CONSTRAINT `pk_account`
                                PRIMARY KEY (`id`),
                            CONSTRAINT `uq_account_username`
                                UNIQUE (`username`)
                        )
                        """
                            .trimIndent())
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `account_achievement` (
                            `account_id`    INT         NOT NULL,
                            `achievement`   VARCHAR(32) NOT NULL,
                            CONSTRAINT `pk_account_achievement`
                                PRIMARY KEY (`account_id`, `achievement`),
                            CONSTRAINT `fk_account_achievement_account_id`
                                FOREIGN KEY (`account_id`)
                                REFERENCES `account`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent())
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `account_metadata` (
                            `account_id`    INT         NOT NULL,
                            `key`           VARCHAR(32) NOT NULL,
                            `value`         VARCHAR(64) NOT NULL,
                            CONSTRAINT `pk_account_metadata`
                                PRIMARY KEY (`account_id`, `key`),
                            CONSTRAINT `fk_account_metadata_account_id`
                                FOREIGN KEY (`account_id`)
                                REFERENCES `account`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent())
                    .use { statement -> statement.executeUpdate() }
            }
        }
    }

    override suspend fun selectByUsername(username: String) =
        selectBy0("SELECT * FROM `account` WHERE `username` = ?") { it.setString(1, username) }

    override suspend fun selectById(id: Int) =
        selectBy0("SELECT * FROM `account` WHERE `id` = ?") { it.setInt(1, id) }

    override suspend fun selectByDiscord(discord: Long) =
        selectBy0("SELECT * FROM `account` WHERE `discord` = ?") { it.setLong(1, discord) }

    private suspend inline fun selectBy0(
        query: String,
        crossinline params: (PreparedStatement) -> Unit
    ) =
        source.transaction { connection ->
            connection.prepareStatement(query).use { statement ->
                params(statement)
                statement.executeQuery().use { result ->
                    if (!result.next()) return@transaction null
                    val id = result.getInt("id")
                    val achievements = enumSetOf<Achievement>()
                    connection
                        .prepareStatement(
                            "SELECT `achievement` FROM `account_achievement` WHERE `account_id` = ?")
                        .use { statement ->
                            statement.setInt(1, id)
                            statement.executeQuery().use { result ->
                                while (result.next()) {
                                    achievements +=
                                        Achievement.valueOf(result.getString("achievement"))
                                }
                            }
                        }
                    val metadata = HashMap<String, String>()
                    connection
                        .prepareStatement(
                            "SELECT `key`, `value` FROM `account_metadata` WHERE `account_id` = ?")
                        .use { statement ->
                            statement.setInt(1, id)
                            statement.executeQuery().use { result ->
                                while (result.next()) {
                                    metadata[result.getString("key")] = result.getString("value")
                                }
                            }
                        }
                    Account(
                        id = result.getInt("id"),
                        username = result.getString("username"),
                        discord = result.getLong("discord"),
                        games = result.getInt("games"),
                        playtime = result.getLong("playtime").seconds,
                        creation = result.getTimestamp("creation").toInstant(),
                        rank = Rank.valueOf(result.getString("rank")),
                        achievements = achievements,
                        metadata = metadata,
                        legacy = result.getBoolean("legacy"))
                }
            }
        }

    override suspend fun updateDiscord(id: Int, discord: Long) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE `account`
                    SET `discord` = ?
                    WHERE `id` = ?
                    LIMIT 1
                    """
                        .trimIndent())
                .use { statement ->
                    statement.setLong(1, discord)
                    statement.setInt(2, id)
                    statement.executeUpdate() > 0
                }
        }

    override suspend fun incrementGames(id: Int) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE `account`
                    SET `games` = `games` + 1
                    WHERE `id` = ?
                    LIMIT 1
                    """
                        .trimIndent())
                .use { statement ->
                    statement.setInt(1, id)
                    statement.executeUpdate() > 0
                }
        }

    override suspend fun incrementPlaytime(id: Int, duration: Duration) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE `account`
                    SET `playtime` = `playtime` + ?
                    WHERE `id` = ?
                    LIMIT 1
                    """
                        .trimIndent())
                .use { statement ->
                    statement.setLong(1, duration.inWholeSeconds)
                    statement.setInt(2, id)
                    statement.executeUpdate() > 0
                }
        }

    override suspend fun updateRank(id: Int, rank: Rank) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE `account`
                    SET `rank` = ?
                    WHERE `id` = ?
                    LIMIT 1
                    """
                        .trimIndent())
                .use { statement ->
                    statement.setString(1, rank.name)
                    statement.setInt(2, id)
                    statement.executeUpdate() > 0
                }
        }

    override suspend fun updatePassword(id: Int, password: Hash) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE `account`
                    SET `password_hash` = ?, `password_salt` = ?
                    WHERE `id` = ?
                    LIMIT 1
                    """
                        .trimIndent())
                .use { statement ->
                    statement.setBytes(1, password.hash)
                    statement.setBytes(2, password.salt)
                    statement.setInt(3, id)
                    statement.executeUpdate() > 0
                }
        }

    override suspend fun updateAchievement(id: Int, achievement: Achievement, completed: Boolean) =
        source.transaction { connection ->
            if (completed) {
                connection
                    .prepareStatement(
                        """
                        INSERT IGNORE INTO `account_achievement` (`account_id`, `achievement`)
                        VALUES (?, ?)
                        """
                            .trimIndent())
                    .use { statement ->
                        statement.setInt(1, id)
                        statement.setString(2, achievement.name)
                        statement.executeUpdate() > 0
                    }
            } else {
                connection
                    .prepareStatement(
                        """
                        DELETE FROM `account_achievement`
                        WHERE `account_id` = ? AND `achievement` = ?
                        LIMIT 1
                        """
                            .trimIndent())
                    .use { statement ->
                        statement.setInt(1, id)
                        statement.setString(2, achievement.name)
                        statement.executeUpdate() > 0
                    }
            }
        }

    override suspend fun updateMetadata(id: Int, key: String, value: String) =
        source.transaction { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO `account_metadata` (`account_id`, `key`, `value`)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE `value` = ?
                    """
                        .trimIndent())
                .use { statement ->
                    statement.setInt(1, id)
                    statement.setString(2, key)
                    statement.setString(3, value)
                    statement.setString(4, value)
                    statement.executeUpdate() > 0
                }
        }
}
