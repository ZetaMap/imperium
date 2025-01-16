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

import java.security.SecureRandom
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

interface PasswordHashFunction {
    fun hash(password: CharArray, salt: ByteArray = salt()): PasswordHash

    fun salt(): ByteArray
}

object ImperiumArgon2PasswordHashFunction : PasswordHashFunction {
    private val random = SecureRandom()

    override fun hash(password: CharArray, salt: ByteArray): PasswordHash {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withMemoryAsKB(64 * 1024)
                .withIterations(3)
                .withParallelism(2)
                .withSalt(salt)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .build()
        )
        val hash = ByteArray(64)
        generator.generateBytes(password, hash)
        return PasswordHash(hash, salt)
    }

    override fun salt() = ByteArray(64).also(random::nextBytes)
}
