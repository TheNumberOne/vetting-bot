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
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import vettingbot.command.AbstractCommand
import vettingbot.command.Command
import vettingbot.mod.ModService
import vettingbot.util.*

@Component
class ModCommand(private val modService: ModService) : AbstractCommand(
    listOf("mod", "mods"),
    "Manages the moderators that have access to the vetting channels.",
    Permission.ADMINISTRATOR
) {
    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
        description("Lists the roles that have access to the vetting channels. Use one of the subcommands to add or remove from this list.")
        field(
            "Syntax", """
            `mod` - Lists the roles that have access to the vetting channels.
        """.trimIndent()
        )
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val modRoles = modService.getModRoles(guildId)
        message.respondEmbed {
            title("Moderator Roles")
            if (modRoles.isEmpty()) {
                description("There are currently no roles that have access to vetting channels by default.")
            } else {
                description("These roles have access to vetting channels by default: " + modRoles.joinToString(", ") {
                    it.roleMention(
                        guildId
                    )
                })
            }
        }
    }

    override val subCommands: List<Command> = listOf(AddModCommand(), RemoveModCommand())

    inner class AddModCommand : AbstractCommand("add", "Adds a role to the roles that are moderators.") {
        override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
            description("Adds to the roles that have access to the vetting channels.")
            field(
                "Syntax", """
                `mod add @role` - Allow members with the @role to access vetting channels created in the future.
            """.trimIndent()
            )
        }

        override suspend fun run(message: MessageCreateEvent, args: String) {
            val roleIds = findAndParseAllSnowflakes(args)
            if (roleIds.isEmpty()) {
                message.respondEmbed {
                    title("Add Moderator Roles")
                    description("No roles found in arguments.")
                }
                return
            }
            val guild = message.guild.awaitSingle()
            val roles = Flux.merge(roleIds.map {
                guild.getRoleById(it)
            }).onDiscordNotFound {
                mono {
                    message.respondEmbed {
                        title("Add Moderator Roles")
                        description("Not all roles are valid roles. Make sure to either mention roles directly or pass the id of the role.")
                    }
                    null
                }
            }.collectList().awaitSingle()
            if (roles.size != roleIds.size) {
                return
            }

            modService.addModRoles(guild.id, roleIds)
            message.respondEmbed {
                title("Add Moderator Roles")
                description("Added moderator roles: " + roleIds.joinToString(", ") { it.roleMention(guild.id) })
            }
        }
    }

    inner class RemoveModCommand : AbstractCommand("remove", "Removes a role from the roles that are moderators.") {
        override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
            description("Removes from the roles that have access to the vetting channels.")
            field(
                "Syntax", """
                `mod remove @role` - Stops adding members with the @role to vetting channels created in the future. Note that they will still be able to access the vetting channels if they have a role that is allowed.
            """.trimIndent()
            )
        }

        override suspend fun run(message: MessageCreateEvent, args: String) {
            val roleIds = findAndParseAllSnowflakes(args)
            if (roleIds.isEmpty()) {
                message.respondEmbed {
                    title("Remove Moderator Roles")
                    description("No roles found in arguments.")
                }
                return
            }
            val guild = message.guild.awaitSingle()
            val roles = Flux.merge(roleIds.map {
                guild.getRoleById(it)
            }).onDiscordNotFound {
                mono {
                    message.respondEmbed {
                        title("Remove Moderator Roles")
                        description("Not all roles are valid roles. Make sure to either mention roles directly or pass the id of the role.")
                    }
                    null
                }
            }.collectList().awaitSingle()
            if (roles.size != roleIds.size) {
                return
            }

            modService.removeModRoles(guild.id, roleIds)
            message.respondEmbed {
                title("Remove Moderator Roles")
                description("Removed moderator roles: " + roleIds.joinToString(", ") { it.roleMention(guild.id) })
            }
        }
    }
}