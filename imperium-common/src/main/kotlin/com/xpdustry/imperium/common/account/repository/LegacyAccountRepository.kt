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
package com.xpdustry.imperium.common.account.repository

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.database.transaction
import java.security.MessageDigest
import javax.sql.DataSource
import kotlinx.coroutines.runBlocking

interface LegacyAccountRepository {

    suspend fun existsByUsername(username: String): Boolean
}

class SQLLegacyAccountRepository(private val source: DataSource) :
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
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }

                connection
                    .prepareStatement(
                        """
                        CREATE INDEX IF NOT EXISTS `idx_legacy_account_username_hash` ON `legacy_account` (`username_hash`)
                        """
                            .trimIndent()
                    )
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
                            .trimIndent()
                    )
                    .use { statement -> statement.executeUpdate() }
            }
        }
    }

    override suspend fun existsByUsername(username: String) =
        source.transaction { connection ->
            val hashed = MessageDigest.getInstance("SHA-256").digest(username.toByteArray())
            connection
                .prepareStatement(
                    """
                    SELECT 1 FROM `legacy_account` WHERE `username_hash` = ? LIMIT 1
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setBytes(1, hashed)
                    statement.executeQuery().next()
                }
        }
}
