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

import java.util.Objects

data class PasswordHash(val hash: ByteArray, val salt: ByteArray) {
    override fun equals(other: Any?) =
        other == this ||
            (other is PasswordHash && timeConstantEquals(hash, other.hash) && timeConstantEquals(salt, other.salt))

    override fun hashCode() = Objects.hash(hash.contentHashCode(), salt.contentHashCode())

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() =
        "Password(hash=${hash.toHexString(HexFormat.UpperCase)}, salt=${salt.toHexString(HexFormat.UpperCase)})"

    private fun timeConstantEquals(a: ByteArray, b: ByteArray): Boolean {
        var diff = a.size xor b.size
        var i = 0
        while (i < a.size && i < b.size) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
            i++
        }
        return diff == 0
    }
}
