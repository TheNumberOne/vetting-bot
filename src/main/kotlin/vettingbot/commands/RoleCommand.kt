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
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.command.Command
import vettingbot.guild.GuildConfigService
import vettingbot.util.*

@Component
class RoleCommand(
    private val guildConfigService: GuildConfigService
) : AbstractCommand(
    "role",
    "Manages the role used for members who have been vetted or vetting.",
    Permission.ADMINISTRATOR
) {
    override suspend fun run(message: MessageCreateEvent, args: String) {
        message.respondEmbed {
            description("Please specify `vetting` or `vetted` to manage the roles assigned during vetting or after being vetted.")
        }
    }

    override val subCommands: List<Command> = listOf(VettedRoleCommand(), VettingRoleCommand())

    suspend fun runImpl(
        message: MessageCreateEvent,
        args: String,
        getter: suspend (guildId: Snowflake) -> Snowflake?,
        setter: suspend (guildId: Snowflake, roleId: Snowflake) -> Unit,
        name: String,
        description: String
    ) {
        val guildId = message.guildId.nullable ?: return
        val nameLower = name.toLowerCase()
        val nameCapitalized = nameLower.capitalize()

        val beforeRole = getter(guildId)
        if (args.isEmpty()) {
            message.respondEmbed {
                title("$nameCapitalized Role")
                if (beforeRole == null) {
                    description("There is currently no $nameLower role set for this server.")
                } else {
                    description("The ${beforeRole.roleMention()} role is $description.")
                }
            }
            return
        }

        val roleId = findAndParseSnowflake(args)
        if (roleId == null) {
            message.respondEmbed {
                title("$nameCapitalized Role")
                description("Couldn't find role $args. Please make sure to pass the id or mention the role.")
            }
            return
        }

        message.client.getRoleById(guildId, roleId).onDiscordNotFound {
            mono {
                message.respondEmbed {
                    title("$nameCapitalized Role")
                    description("$args doesn't define a role.")
                }
                null
            }
        }.awaitSingle()

        setter(guildId, roleId)
        message.respondEmbed {
            title("$nameCapitalized Role")
            if (beforeRole != null) {
                description("Changed the role $description.")
                field("Before", beforeRole.roleMention())
                field("After", roleId.roleMention())
            } else {
                description("Set the role $description to ${roleId.roleMention()}")
            }
        }
    }

    inner class VettedRoleCommand : AbstractCommand(
        "vetted",
        "Manages the role used for members who have been vetted.",
        Permission.ADMINISTRATOR
    ) {
        override suspend fun run(message: MessageCreateEvent, args: String) {
            runImpl(
                message,
                args,
                guildConfigService::getVettedRole,
                guildConfigService::setVettedRole,
                "vetted",
                "assigned to members after they have completed the vetting process"
            )
        }
    }

    inner class VettingRoleCommand : AbstractCommand(
        "vetting",
        "Manages the role used for members currently in the vetting process.",
        Permission.ADMINISTRATOR
    ) {
        override suspend fun run(message: MessageCreateEvent, args: String) {
            runImpl(
                message,
                args,
                guildConfigService::getVettedRole,
                guildConfigService::setVettedRole,
                "vetting",
                "assigned to members as they go through the vetting process"
            )
        }
    }
}

