/*
 * Copyright (C) 2020  Rosetta Roberts <rosettafroberts@gmail.com>
 *
 * This file is part of VettingBot.
 *
 * VettingBot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VettingBot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VettingBot.  If not, see <https://www.gnu.org/licenses/>.
 */

package vettingbot.util

import discord4j.common.util.Snowflake

fun Long.toSnowflake(): Snowflake = Snowflake.of(this)

private val SNOWFLAKE_REGEX = Regex("\\d+")

fun findAndParseSnowflake(text: String): Snowflake? {
    val role = SNOWFLAKE_REGEX.find(text) ?: return null
    return Snowflake.of(role.value)
}

fun findAndParseAllSnowflakes(text: String): List<Snowflake> {
    return SNOWFLAKE_REGEX.findAll(text).map { Snowflake.of(it.value) }.toList()
}

fun Snowflake.roleMention(guildId: Snowflake? = null) = if (this == guildId) "@everyone" else "<@&${asString()}>"

fun Snowflake.memberMention() = "<@!${asString()}>"