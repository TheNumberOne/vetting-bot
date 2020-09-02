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

import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Color
import mu.KotlinLogging
import org.springframework.stereotype.Component
import vettingbot.commands.custom.CustomVettingCommandsService
import vettingbot.discord.DiscordEventListener
import vettingbot.guild.GuildConfigService
import vettingbot.util.nullable
import vettingbot.util.respondEmbed

private val logger = KotlinLogging.logger {}

@Component
class CommandListener(
    private val commands: CommandsService,
    private val customCommands: CustomVettingCommandsService,
    private val guildService: GuildConfigService
) : DiscordEventListener<MessageCreateEvent> {

    override suspend fun on(event: MessageCreateEvent) {
        val content = event.message.content
        val server = event.guildId.nullable ?: return
        val prefix = guildService.getPrefix(server)
        val member = event.member.nullable ?: return
        if (!content.startsWith(prefix)) {
            return
        }
        val commandText = content.substring(prefix.length).trim()
        val parts = commandText.split(' ', limit = 2)
        val commandName = if (parts.isNotEmpty()) parts[0] else ""

        var commandArguments = if (parts.size >= 2) parts[1] else ""
        var command = commands.findCommand(commandName) ?: customCommands.findCommand(server, commandName) ?: return

        while (command.subCommands.isNotEmpty()) {
            if (!command.canExecute(server, member)) return

            val subCommandParts = commandArguments.split(' ', limit = 2)
            val subCommandName = if (subCommandParts.isNotEmpty()) subCommandParts[0] else ""
            val subCommandArguments = if (subCommandParts.size >= 2) subCommandParts[1] else ""
            command = command.subCommands.find { it.names.contains(subCommandName) } ?: break
            commandArguments = subCommandArguments
        }

        if (!command.canExecute(server, member)) return
        try {
            logger.debug { "Executing command: $content" }
            command.run(event, commandArguments)
        } catch (e: ClientException) {
            logger.error("Error while executing command: $content", e)
            val message = e.errorResponse.nullable?.fields?.get("message") as? String
                ?: e.errorResponse.nullable?.fields?.get("after") as? String
                ?: e.status.reasonPhrase()
                ?: "Unknown discord error."
            event.respondEmbed {
                title("Discord Error")
                description(message)
                color(Color.RED)
            }
        } catch (e: Exception) {
            logger.error("Error while executing command: $content", e)
        }


    }
}