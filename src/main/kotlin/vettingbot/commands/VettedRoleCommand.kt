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
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.util.*

@Component
class VettedRoleCommand(
    private val guildConfigService: GuildConfigService
) : AbstractCommand(
    "vettedrole",
    "Manages the role used for members who have been vetted.",
    Permission.ADMINISTRATOR
) {
    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return

        val beforeRole = guildConfigService.getVettedRole(guildId)
        if (args.isEmpty()) {
            message.respondEmbed {
                title("Vetted Role")
                if (beforeRole == null) {
                    description("There is currently no vetted role set for this server.")
                } else {
                    description("The ${beforeRole.roleMention()} role is assigned to members after they have completed the vetting process.")
                }
            }
            return
        }

        val roleId = findAndParseSnowflake(args)
        if (roleId == null) {
            message.respondEmbed {
                title("Vetted Role")
                description("Couldn't find role $args. Please make sure to pass the id or mention the role.")
            }
            return
        }

        message.client.getRoleById(guildId, roleId).onDiscordNotFound {
            mono {
                message.respondEmbed {
                    title("Vetted Role")
                    description("$args doesn't define a role.")
                }
                null
            }
        }.awaitSingle()

        guildConfigService.setVettedRole(guildId, roleId)
        message.respondEmbed {
            title("Vetted Role")
            if (beforeRole != null) {
                description("Changed the role assigned to members after the vetting process.")
                field("Before", beforeRole.roleMention())
                field("After", roleId.roleMention())
            } else {
                description("Set the role assigned to members after the vetting process to ${roleId.roleMention()}")
            }
        }
    }
}