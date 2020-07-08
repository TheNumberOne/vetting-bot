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

package vettingbot.command

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.MessageCreateSpec

interface Command {
    /**
     * The primary name must come first.
     */
    val names: List<String>

    val quickHelp: String

    /**
     * Null specifies that it applies to all servers.
     */
    val guildId: Snowflake?

    val subCommands: List<Command>

    suspend fun displayHelp(): (MessageCreateSpec) -> Unit

    suspend fun canExecute(guildId: Snowflake, member: Member): Boolean

    suspend fun run(message: MessageCreateEvent, args: String)
}