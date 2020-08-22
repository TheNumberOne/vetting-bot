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

package vettingbot.commands.custom

import discord4j.common.util.Snowflake
import org.neo4j.springframework.data.core.schema.GeneratedValue
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node

@Node
data class CustomVettingCommandConfig(
    val guildId: Snowflake,
    val name: String,
    val kick: Boolean = false,
    val kickReason: String? = "",
    val ban: Boolean = false,
    val banReason: String? = "",
    val addRoles: List<Snowflake> = emptyList(),
    val removeRoles: List<Snowflake> = emptyList(),
    val allowedRoles: List<Snowflake> = emptyList(),
    val allowedUsers: List<Snowflake> = emptyList(),
    val forbiddenRoles: List<Snowflake> = emptyList(),
    val forbiddenUsers: List<Snowflake> = emptyList(),
    @Id @GeneratedValue val id: Long? = null
)