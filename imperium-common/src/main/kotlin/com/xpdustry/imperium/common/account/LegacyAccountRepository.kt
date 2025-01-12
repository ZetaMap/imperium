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
import com.xpdustry.imperium.common.hash.PBKDF2Params
import java.security.MessageDigest
import javax.sql.DataSource
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

interface LegacyAccountRepository {

    suspend fun selectByUsername(username: String): LegacyAccount?

    suspend fun deleteById(id: Int): Boolean
}

class ExposedLegacyAccountRepository(private val source: DataSource) :
    LegacyAccountRepository, ImperiumApplication.Listener {
    override fun onImperiumInit() {
        runBlocking {
            source.transaction { connection ->
                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `legacy_account` (
                            `id`                INT         NOT NULL AUTO_INCREMENT,
                            `username_hash`     BINARY(32)  NOT NULL,
                            `password_hash`     BINARY(32)  NOT NULL,
                            `password_salt`     BINARY(16)  NOT NULL,
                            `games`             INT         NOT NULL DEFAULT 0,
                            `playtime`          BIGINT      NOT NULL DEFAULT 0,
                            `rank`              VARCHAR(32) NOT NULL DEFAULT 'EVERYONE',
                            CONSTRAINT `pk_legacy_account`
                                PRIMARY KEY (`id`),
                            CONSTRAINT `uq_legacy_account_username`
                                UNIQUE (`username_hash`)
                        )
                        """
                            .trimIndent())
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS `legacy_account_achievement` (
                            `legacy_account_id`   INT           NOT NULL,
                            `achievement`         VARCHAR(32)   NOT NULL,
                            CONSTRAINT `pk_legacy_account_achievement`
                                PRIMARY KEY (`legacy_account_id`, `achievement`),
                            CONSTRAINT `fk_legacy_account_achievement_legacy_account_id`
                                FOREIGN KEY (`legacy_account_id`)
                                REFERENCES `legacy_account`(`id`)
                                ON DELETE CASCADE
                        )
                        """
                            .trimIndent())
                    .use { statement -> statement.executeUpdate() }
            }
        }
    }

    override suspend fun selectByUsername(username: String) =
        source.transaction { connection ->
            val hashed = MessageDigest.getInstance("SHA-256").digest(username.toByteArray())
            connection
                .prepareStatement(
                    "SELECT * FROM `legacy_account` WHERE `username_hash` = ? LIMIT 1;")
                .use { statement ->
                    statement.setBytes(1, hashed)
                    statement.executeQuery().use { result ->
                        val id = result.getInt("id")
                        val achievements = enumSetOf<Achievement>()
                        connection
                            .prepareStatement(
                                "SELECT `achievement` FROM `legacy_account_achievement` WHERE `legacy_account_id` = ?")
                            .use { statement ->
                                statement.setInt(1, id)
                                statement.executeQuery().use { result ->
                                    while (result.next()) {
                                        achievements +=
                                            Achievement.valueOf(result.getString("achievement"))
                                    }
                                }
                            }
                        val password =
                            Hash(
                                LEGACY_PASSWORD_PARAMS,
                                result.getBytes("password_hash"),
                                result.getBytes("password_salt"))
                        LegacyAccount(
                            id = id,
                            username = username.lowercase(),
                            password = password,
                            games = result.getInt("games"),
                            playtime = result.getLong("playtime").seconds,
                            rank = Rank.valueOf(result.getString("rank")),
                            achievements = achievements)
                    }
                }
        }

    override suspend fun deleteById(id: Int) =
        source.transaction { connection ->
            connection.prepareStatement("DELETE FROM `legacy_account` WHERE `id` = ?").use {
                statement ->
                statement.setInt(1, id)
                statement.executeUpdate() > 0
            }
        }

    companion object {
        internal val LEGACY_PASSWORD_PARAMS =
            PBKDF2Params(
                hmac = PBKDF2Params.Hmac.SHA256,
                iterations = 10000,
                length = 256,
                saltLength = 16,
            )
    }
}
