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

data class Password(val hash: ByteArray, val salt: ByteArray) {

    override fun equals(other: Any?) =
        other == this ||
            (other is Password && hash.contentEquals(other.hash) && salt.contentEquals(other.salt))

    override fun hashCode() = Objects.hash(hash.contentHashCode(), salt.contentHashCode())

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() =
        "Password(hash=${hash.toHexString(HexFormat.UpperCase)}, salt=${salt.toHexString(HexFormat.UpperCase)})"
}
