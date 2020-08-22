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
import discord4j.core.`object`.entity.Guild
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.mono
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.command.Command
import vettingbot.commands.custom.CustomVettingCommandConfig
import vettingbot.commands.custom.CustomVettingCommandsService
import vettingbot.guild.GuildConfigService
import vettingbot.util.*

@Component
class CommandCommand(
    private val guildService: GuildConfigService,
    private val service: CustomVettingCommandsService
) : AbstractCommand(
    listOf("command", "commands"),
    "Allows managing custom vetting commands",
    Permission.ADMINISTRATOR
) {
    override val subCommands: List<Command> = listOf(NewCommand(), AddCommand(), RemoveCommand(), DeleteCommand())

    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        val commands = service.findCommandConfigsInGuild(guildId)
        if (commands.isEmpty()) {
            message.respondEmbed {
                title("Commands")
                description("No vetting commands are currently configured for this guild.")
            }
            return
        }
        val prefix = guildService.getPrefix(guildId)
        message.respondEmbed {
            title("Custom Commands")
            for (command in commands) {
                field(
                    "$prefix${command.name}", getCommandDescription(command)
                )
            }
        }
    }

    private fun getCommandDescription(command: CustomVettingCommandConfig): String {
        val lines = mutableListOf<String>()

        if (command.ban) {
            lines += "Bans the user being vetted" + command.banReason?.let { " with reason \"$it\"" } + "."
        } else if (command.kick) {
            lines += "Kicks the user being vetted" + command.kickReason?.let { " with reason \"$it\"" } + "."
        } else {
            if (command.addRoles.isNotEmpty()) {
                lines += "Adds roles: " + command.addRoles.joinToString { it.roleMention() }
            }
            if (command.removeRoles.isNotEmpty()) {
                lines += "Removes roles: " + command.removeRoles.joinToString { it.roleMention() }
            }
            if (command.addRoles.isEmpty() && command.removeRoles.isEmpty()) {
                lines += "Does nothing."
            }
        }
        if (command.forbiddenUsers.isNotEmpty()) {
            lines += "Forbidden users: " + command.forbiddenUsers.joinToString { it.memberMention() }
        }
        if (command.allowedUsers.isNotEmpty()) {
            lines += "Allowed users: " + command.allowedUsers.joinToString { it.memberMention() }
        }
        if (command.forbiddenRoles.isNotEmpty()) {
            lines += "Forbidden roles: " + command.forbiddenRoles.joinToString { it.roleMention() }
        }
        if (command.allowedRoles.isNotEmpty()) {
            lines += "Allowed roles: " + command.allowedRoles.joinToString { it.roleMention() }
        }
        if (command.allowedRoles.isEmpty() && command.allowedUsers.isEmpty()) {
            lines += "**Warning:** Nobody can execute the command."
        }
        return lines.joinToString("\n")
    }


    inner class NewCommand :
        AbstractCommand(listOf("new", "update-set", "set", "="), "Creates a vetting command.") {

        override suspend fun run(message: MessageCreateEvent, args: String) = coroutineScope<Unit> {
            val (name, args1) = args.split(" ", limit = 2)
            val guild = message.guild.awaitFirstOrNull() ?: return@coroutineScope
            val commandArgs = parseCommandConfig(guild, args1)
            (commandArgs.addRoles + commandArgs.removeRoles).map { role ->
                async {
                    guild.getRoleById(role).onDiscordNotFound {
                        mono {
                            message.respondEmbed {
                                description(role.roleMention() + " is not a valid role.")
                            }
                            cancel()
                            null
                        }
                    }
                }
            }.awaitAll()
            val newCommand = commandArgs.addTo(CustomVettingCommandConfig(guild.id, name))
            val result = service.createNew(newCommand)
            val prefix = guildService.getPrefix(guild.id)
            message.respondEmbed {
                title("Add Command")
                if (result != null) {
                    description("Added new command `$prefix${newCommand.name}`\n\n" + getCommandDescription(result))
                } else {
                    description("Command already exists.")
                }
            }
        }
    }

    inner class AddCommand : AbstractCommand(listOf("add", "update-add", "+"), "Adds behavior to a command.") {
        override suspend fun run(message: MessageCreateEvent, args: String) =
            addOrRemoveFromCommand(message, args, true)
    }

    suspend fun addOrRemoveFromCommand(message: MessageCreateEvent, args: String, add: Boolean) = coroutineScope<Unit> {
        val (name, args1) = args.split(" ", limit = 2)
        val guild = message.guild.awaitFirstOrNull() ?: return@coroutineScope
        val commandArgs = parseCommandConfig(guild, args1, add)
        val result =
            service.updateCommand(guild.id, name) { if (add) commandArgs.addTo(it) else commandArgs.removeFrom(it) }
        val prefix = guildService.getPrefix(guild.id)
        message.respondEmbed {
            title(if (add) "Add to Command" else "Remove From Command")
            if (result == null) {
                description("Command doesn't exist.")
            } else {
                description("Updated command `$prefix${result.name}`\n\n${getCommandDescription(result)}")
            }
        }
    }

    inner class RemoveCommand :
        AbstractCommand(listOf("remove", "update-remove", "-"), "Removes behavior from a command.") {
        override suspend fun run(message: MessageCreateEvent, args: String) =
            addOrRemoveFromCommand(message, args, false)
    }

    inner class DeleteCommand : AbstractCommand("delete", "Deletes a command.") {
        override suspend fun run(message: MessageCreateEvent, args: String) {
            val guildId = message.guildId.nullable ?: return
            service.delete(guildId, args)
            message.respondEmbed {
                title("Delete Command")
                description("Deleted command $args")
            }
        }
    }


    class CommandArgsBuilder {
        val addRoles = mutableListOf<Snowflake>()
        val removeRoles = mutableListOf<Snowflake>()
        val allowedRoles = mutableListOf<Snowflake>()
        val allowedUsers = mutableListOf<Snowflake>()
        val forbiddenRoles = mutableListOf<Snowflake>()
        val forbiddenUsers = mutableListOf<Snowflake>()
        var kick = false
        var kickReason: String? = null
        var ban = false
        var banReason: String? = null

        fun addTo(config: CustomVettingCommandConfig): CustomVettingCommandConfig {
            return config.copy(
                kick = kick || config.kick,
                kickReason = kickReason ?: config.kickReason,
                ban = ban || config.ban,
                banReason = banReason ?: config.banReason,
                addRoles = config.addRoles + addRoles,
                removeRoles = config.removeRoles + removeRoles,
                allowedRoles = config.allowedRoles + allowedRoles - forbiddenRoles,
                allowedUsers = config.allowedUsers + allowedUsers - forbiddenUsers,
                forbiddenRoles = config.forbiddenRoles - allowedRoles + forbiddenRoles,
                forbiddenUsers = config.forbiddenUsers - allowedUsers + forbiddenUsers
            )
        }

        fun removeFrom(config: CustomVettingCommandConfig): CustomVettingCommandConfig {
            return config.copy(
                kick = !kick && config.kick,
                ban = !ban && config.ban,
                addRoles = config.addRoles - addRoles,
                removeRoles = config.removeRoles - removeRoles,
                allowedRoles = config.allowedRoles - allowedRoles,
                allowedUsers = config.allowedUsers - allowedUsers,
                forbiddenRoles = config.forbiddenRoles - forbiddenRoles,
                forbiddenUsers = config.forbiddenUsers - forbiddenUsers
            )
        }
    }

    fun parseCommandConfig(guild: Guild, args: String, includeReason: Boolean = true): CommandArgsBuilder {
        val parts = args.split(" ")
        val config = CommandArgsBuilder()

        var previousAdd = false
        var previousRemove = false
        var previousAllow = false
        var previousForbid = false

        loop@ for ((i, part) in parts.withIndex()) {
            when {
                part == "ban" -> {
                    config.ban = true
                    if (includeReason) {
                        config.banReason = parts.subList(i + 1, parts.size).joinToString(" ")
                        return config
                    }
                }
                part == "kick" -> {
                    config.kick = true
                    if (includeReason) {
                        config.kickReason = parts.subList(i + 1, parts.size).joinToString(" ")
                        return config
                    }
                }
                part.startsWith("+") -> {
                    val snowflake = findAndParseSnowflake(part)
                    if (snowflake != null) {
                        config.addRoles += snowflake
                    } else {
                        previousAdd = true
                        continue@loop
                    }
                }
                part.startsWith("-") -> {
                    val snowflake = findAndParseSnowflake(part)
                    if (snowflake != null) {
                        config.removeRoles += snowflake
                    } else {
                        previousRemove = true
                        continue@loop
                    }
                }
                part == "allow" -> {
                    previousAllow = true
                }
                part == "forbid" -> {
                    previousForbid = true
                }
                previousAdd -> {
                    findAndParseSnowflake(part)?.let(config.addRoles::add)
                }
                previousRemove -> {
                    findAndParseSnowflake(part)?.let(config.removeRoles::add)
                }
                previousAllow -> {
                    val snowflake = findAndParseSnowflake(part)
                    if (snowflake == guild.id || snowflake in guild.roleIds) {
                        config.allowedRoles += snowflake!!
                    } else if (snowflake != null) {
                        config.allowedUsers += snowflake
                    }
                }
                previousForbid -> {
                    val snowflake = findAndParseSnowflake(part)
                    if (snowflake == guild.id || snowflake in guild.roleIds) {
                        config.forbiddenRoles += snowflake!!
                    } else if (snowflake != null) {
                        config.forbiddenUsers += snowflake
                    }
                }
            }

            previousAdd = false
            previousRemove = false
        }

        return config
    }
}