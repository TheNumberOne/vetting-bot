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

import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.util.nullable
import vettingbot.util.respondEmbed

@Component
class DisableCommand(private val guildConfigService: GuildConfigService) :
    AbstractCommand("disable", "Disables vetting for this server.", Permission.ADMINISTRATOR) {
    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val previouslyEnabled = guildConfigService.isEnabled(guildId)
        guildConfigService.setEnabled(guildId, false)
        message.respondEmbed {
            title("Enable/Disable")
            if (!previouslyEnabled) {
                description("Vetting is already disabled.")
            } else {
                description("Disabled vetting for this server.")
            }
        }
    }
}