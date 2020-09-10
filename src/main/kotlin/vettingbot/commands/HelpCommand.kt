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
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.command.CommandsService
import vettingbot.guild.GuildConfigService
import vettingbot.util.*

@Component
class HelpCommand(
    private val guilds: GuildConfigService,
    @Lazy private val commandsService: CommandsService
) : AbstractCommand(listOf("help", "h"), "Provides help for commands.") {

    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
        description(
            """
                This command is used to display help for other commands.
                Pass the name of another command to see help for that command.
            """.trimIndent()
        )
        field("Syntax", "${guilds.getPrefix(guildId)}help [command]")
        field(
            "Example Usage", """
            `help help` - Display help for the `help` command.
            `help command new` - Display help for the `new` subcommand of `command`.
            `help` - Display general help information.
        """.trimIndent()
        )
    }

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val member = message.member.nullable ?: return
        val prefix = guilds.getPrefix(guildId)

        val commandNames = args.removePrefix(prefix).split(" ").filter { it.isNotBlank() }

        if (args == "time") {
            message.respondEmbed {
                title("Time Parameters")
                displayDurationHelp()
            }
            return
        }

        if (commandNames.isEmpty()) {
            message.respondEmbed {
                title("Help")
                description(
                    """
                    |Vetting Bot is a bot used for vetting new members to servers.
                    |
                    |Run the `${prefix}setup` command to be guided through setting up the bot.
                    |${if (guilds.isEnabled(guildId)) "Vetting is currently enabled for this server." else "**Vetting is currently not enabled for this server.**"}
                    """.trimMargin()
                )

                commandsService.commands
                    .filter { it.canExecute(guildId, member) }
                    .sortedBy { it.names.first() }
                    .forEach {
                        field("`$prefix${it.names.first()}`", it.quickHelp)
                    }
            }
            return
        }

        var command = commandsService.findCommand(commandNames.first())

        if (command?.canExecute(guildId, member) != true) {
            message.respondMessage {
                embed {
                    title("Help")
                    description("Command $prefix${commandNames.first()} does not exist or you do not have permissions to execute it.")
                }
            }
            return
        }
        for ((previousName, name) in commandNames.windowed(2)) {
            val next = command!!.subCommands.find { it.names.contains(name) }
            if (next?.canExecute(guildId, member) != true) {
                message.respondEmbed {
                    title("Help")
                    description("Subcommand $name does not exist within command $prefix$previousName or you do not have permission to execute it.")
                }
                return
            }
            command = next
        }
        command!!

        message.respondEmbed {
            title("`$prefix${commandNames.joinToString(" ")}`")
            description(command.quickHelp)
            if (command.names.size > 1) {
                field(
                    "Aliases",
                    command.names.joinToString(", ") { "`$prefix${(commandNames.dropLast(1) + it).joinToString(" ")}`" })
            }
            command.displayHelp(guildId)(spec)
            command.subCommands
                .filter { it.canExecute(guildId, member) }
                .forEach {
                    field("`$prefix${(commandNames + it.names.first()).joinToString(" ")}`", it.quickHelp)
                }
        }
    }
}