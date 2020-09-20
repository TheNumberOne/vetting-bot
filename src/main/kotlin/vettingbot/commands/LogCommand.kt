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

package vettingbot.commands

import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import vettingbot.command.AbstractCommand
import vettingbot.logging.GuildLoggerService
import vettingbot.util.findAndParseSnowflake
import vettingbot.util.onDiscordNotFound
import vettingbot.util.respondEmbed

@Component
class LogCommand(private val guildLoggerService: GuildLoggerService) : AbstractCommand(
    "log",
    "Manages the channel used to log bans and kicks performed by this bot.",
    Permission.ADMINISTRATOR
) {
    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guild = message.guild.awaitFirstOrNull() ?: return
        val previousChannel = guildLoggerService.getLogger(guild)
        if (args.isEmpty()) {
            if (previousChannel == null) {
                message.respondEmbed {
                    description("There is currently no log channel configured for this server.")
                }
            } else {
                message.respondEmbed {
                    description("Log messages are currently sent to ${previousChannel.mention}.")
                }
            }
            return
        }
        val channel = findAndParseSnowflake(args)?.let {
            guild.getChannelById(it).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull() as? TextChannel
        } ?: run {
            message.respondEmbed {
                description("$args is not a channel id or channel mention.")
            }
            return
        }
        guildLoggerService.markLogger(channel)
        if (previousChannel == null) {
            message.respondEmbed { description("Log messages will now be sent to ${channel.mention}") }
        } else {
            message.respondEmbed {
                description("Changed the channel log messages are sent to from ${previousChannel.mention} to ${channel.mention}")
            }
        }
    }
}