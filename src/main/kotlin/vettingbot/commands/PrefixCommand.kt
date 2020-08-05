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

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.util.nullable
import vettingbot.util.respondEmbed
import vettingbot.util.respondMessage

@Component
class PrefixCommand(private val guildService: GuildConfigService) :
    AbstractCommand("prefix", "Set the prefix of the bot", Permission.ADMINISTRATOR) {
    override suspend fun canExecute(guildId: Snowflake, member: Member): Boolean {
        return member.basePermissions.awaitSingle().contains(Permission.ADMINISTRATOR)
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guild = message.guildId.nullable ?: return
        val newPrefix = args.trim()
        val oldPrefix = guildService.getPrefix(guild)
        if (newPrefix.isEmpty()) {
            message.respondEmbed {
                description("Current prefix: `$oldPrefix`")
            }
            return
        }
        guildService.setPrefix(guild, args)

        message.respondMessage {
            embed {
                description("Changed prefix.")
                field("Before", oldPrefix, true)
                field("After", args, true)
            }
        }
    }
}