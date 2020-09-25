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
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Permission
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import vettingbot.command.AbstractCommand
import vettingbot.command.Command
import vettingbot.commands.custom.CustomVettingCommandConfig
import vettingbot.commands.custom.CustomVettingCommandsService
import vettingbot.commands.custom.pingTemplate
import vettingbot.guild.GuildConfigService
import vettingbot.template.showValidation
import vettingbot.util.*

@Component
class CommandCommand(
    private val guildService: GuildConfigService,
    private val service: CustomVettingCommandsService
) : AbstractCommand(
    listOf("command", "commands"),
    "Allows managing custom vetting commands.",
    Permission.ADMINISTRATOR
) {
    override val subCommands: List<Command> = listOf(NewCommand(), AddCommand(), RemoveCommand(), DeleteCommand())

    override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
        description("This manages custom commands that can be executed in channels created for vetting new users. Running this command lists the current commands for this server. To create or change commands, use one of the subcommands.")
        field(
            "Permissions", """
            Each command is individually configured on who can run it. To determine if someone can run a custom command, the following is performed:
            
            1. Is the user explicitly forbidden? If so, they cannot run the command.
            
            2. Is the user explicitly allowed? Then, they can run the command.
            
            3. Does the user have a role that is forbidden to run the command? Then, they cannot run the command.
            
            4. Does the user have a role that is allowed to run the command? If so, then they can run the command.
            
            5. Is @everyone allowed to run the command? If so, the user is allowed to run the command.
            
            6. The user is not allowed to run the command.
        """.trimIndent()
        )
    }

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
                lines += "Adds roles: " + command.addRoles.joinToString { it.roleMention(command.guildId) }
            }
            if (command.removeRoles.isNotEmpty()) {
                lines += "Removes roles: " + command.removeRoles.joinToString { it.roleMention(command.guildId) }
            }
            if (command.addRoles.isEmpty() && command.removeRoles.isEmpty() && !command.ping) {
                lines += "Does nothing."
            }
        }
        if (command.ping) {
            lines += "Pings in ${command.pingChannel!!.channelMention()}: ${command.pingMessage}"
        }
        if (command.forbiddenUsers.isNotEmpty()) {
            lines += "Forbidden users: " + command.forbiddenUsers.joinToString { it.memberMention() }
        }
        if (command.allowedUsers.isNotEmpty()) {
            lines += "Allowed users: " + command.allowedUsers.joinToString { it.memberMention() }
        }
        if (command.forbiddenRoles.isNotEmpty()) {
            lines += "Forbidden roles: " + command.forbiddenRoles.joinToString { it.roleMention(command.guildId) }
        }
        if (command.allowedRoles.isNotEmpty()) {
            lines += "Allowed roles: " + command.allowedRoles.joinToString { it.roleMention(command.guildId) }
        }
        if (command.allowedRoles.isEmpty() && command.allowedUsers.isEmpty()) {
            lines += "**Warning:** Nobody can execute the command."
        }
        return lines.joinToString("\n")
    }


    inner class NewCommand :
        AbstractCommand(listOf("new", "update-set", "set", "="), "Creates or replaces a vetting command.") {

        override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
            description("This creates or replaces a vetting command.")
            field(
                "Syntax", """
                `command new name arguments...` - This creates a new custom command. Here are the different possible arguments.
                
                  • `+ @role` - Add the specified role to the person being vetted.
                
                  • `- @role` - Remove the specified role from the person being vetted.
                
                  • `kick <reason>` - Kick the person being vetted for the specific reason. Warning: no further settings can be specified after this argument, as they are interpreted as part of the reason.
                  
                  • `ban <reason>` - Ban the person being vetted for the specified reason. Warning: no further settings can be specified after this argument, as they are interpreted as part of the reason.
                  
                  • `ping #channel message` - Pings the person being vetted in specified channel. {member} is replaced a mention of the person being vetted, {mod} is replaced with a mention of the mod who did the vetting, {channel} is replaced with a mention to the ping channel. Warning: no further settings can be specified after this argument, as they are interpreted as part of the message.
                  
                  • `allow @user` - Allow @user to run this command.
                  
                  • `allow @role` - Allow @role to run this command.
                  
                  • `forbid @user` - Forbid @user from running this command.
                  
                  • `forbid @role` - Forbid @role from running this command.
            """.trimIndent()
            )
            field(
                "Example Usage", """
                `command new minor - @vetting + @vetted + @role allow @mod`
                Creates/replaces the command `minor` which removes the @vetting role, adds the @vetted role, adds the @minor role, and can only be executed by people with the @mod role.
                
                `command = admin + @admin allow @admin`
                Creates/replaces the command `admin` which adds the @admin role and can only be executed by people with the @admin role.
                
                `command set selfvet - @vetting + @vetted allow @everyone ping #general Welcome to this wonderful server {member}!`
                Creates/replaces the command `selfvet` which removes the @vetting role, adds the @vetted role, can be executed by anyone, then pings them in the #general channel.
            """.trimIndent()
            )
        }

        override suspend fun run(message: MessageCreateEvent, args: String) {
            val (name, args1) = args.split(" ", limit = 2)
            val guild = message.guild.awaitFirstOrNull() ?: return
            val commandArgs = parseCommandConfig(guild, args1)
            if (!commandArgs.validate(guild, message.message.channel.awaitSingle() as TextChannel)) {
                return
            }
            val newCommand = commandArgs.addTo(CustomVettingCommandConfig(guild.id, name))
            val result = service.createNewOrSetExisting(newCommand)
            val prefix = guildService.getPrefix(guild.id)
            message.respondEmbed {
                title("Add Command")
                description("Added new command `$prefix${newCommand.name}`\n\n" + getCommandDescription(result))
            }
        }
    }

    inner class AddCommand : AbstractCommand(listOf("add", "update-add", "+"), "Adds behavior to a command.") {

        override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
            description("This adds behavior to an existing vetting command.")
            field(
                "Syntax", """
                `command add name arguments...` - This adds to an existing custom command. Here are the different possible arguments.
                
                  • `+ @role` - Add the specified role to the person being vetted.
                
                  • `- @role` - Remove the specified role from the person being vetted.
                
                  • `kick <reason>` - Kick the person being vetted for the specific reason. Warning: no further settings can be specified after this argument, as they are interpreted as part of the reason.
                  
                  • `ban <reason>` - Ban the person being vetted for the specified reason. Warning: no further settings can be specified after this argument, as they are interpreted as part of the reason.
                  
                  • `ping #channel message` - Pings the person being vetted in specified channel. {member} is replaced a mention of the person being vetted, {mod} is replaced with a mention of the mod who did the vetting, {channel} is replaced with a mention to the ping channel. Warning: no further settings can be specified after this argument, as they are interpreted as part of the message.
                  
                  • `allow @user` - Allow @user to run this command.
                  
                  • `allow @role` - Allow @role to run this command.
                  
                  • `forbid @user` - Forbid @user from running this command.
                  
                  • `forbid @role` - Forbid @role from running this command.
            """.trimIndent()
            )
            field(
                "Example Usage", """
                `command add minor ban This is an 18+ server`
                Edits the `minor` command so that it now bans the person being vetted. 
                
                `command + admin allow @Fred forbid @Weasley ping #mod-chat Welcome no admin ;)`
                Edits the `admin` command so that none of the Weasley's are allowed to execute it, except Fred. Pings the person being vetted in #mod-chat.
                
                `command + selfvet + @self-made-person`
                Edits the `selfvet` command so that it now also adds the `@self-made-person` role.
            """.trimIndent()
            )
        }

        override suspend fun run(message: MessageCreateEvent, args: String) =
            addOrRemoveFromCommand(message, args, true)
    }

    suspend fun addOrRemoveFromCommand(message: MessageCreateEvent, args: String, add: Boolean) {
        val (name, args1) = args.split(" ", limit = 2)
        val guild = message.guild.awaitFirstOrNull() ?: return
        val commandArgs = parseCommandConfig(guild, args1, add)
        if (add) {
            if (!commandArgs.validate(guild, message.message.channel.awaitSingle() as TextChannel)) {
                return
            }
        }
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


        override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
            description("This removes behavior from an existing vetting command.")
            field(
                "Syntax", """
                `command remove name arguments...` - Removes behavior from an existing custom command. Here are the different possible arguments.
                
                  • `+ @role` - The command no longer adds the specified role to the person being vetted.
                
                  • `- @role` - The command no longer removes the specified role from the person being vetted.
                
                  • `kick` - The command no longer kicks the person being vetted.
                  
                  • `ban` - The command no longer bans the person being vetted.
                  
                  • `ping` - No longer pings the person being vetted.
                  
                  • `allow @user` - The command no longer allows @user to run this command.
                  
                  • `allow @role` - The command no longer allows @role to run this command.
                  
                  • `forbid @user` - The command no longer forbids @user from running this command.
                  
                  • `forbid @role` - The command no longer forbids @role from running this command.
            """.trimIndent()
            )
            field(
                "Example Usage", """
                `command - minor ban`
                Edits the `minor` command so that it no longer bans people.
                
                `command remove admin forbid @Weasley`
                Edits the `admin` command so that Weasleys aren't specifically forbidden anymore.
                
                `command - selfvet - @vetting + @vetted`
                Edits the `selfvet` command so that it no longer removes the @vetting role and no longer adds the @vetted role.
            """.trimIndent()
            )
        }
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

        override suspend fun displayHelp(guildId: Snowflake): (EmbedCreateSpec) -> Unit = embedDsl {
            description("This deletes vetting commands. This cannot be used to delete regular commands.")
            field(
                "Syntax", """
                `command delete name` - Deletes the command named `name`.
            """.trimIndent()
            )
            field(
                "Example Usage", """
                `command delete admin`
                Deletes the `admin` vetting command.    
            """.trimIndent()
            )
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
        var ping: Boolean = false
        var pingChannel: Snowflake? = null
        var pingMessage: String? = null

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
                forbiddenUsers = config.forbiddenUsers - allowedUsers + forbiddenUsers,
                pingChannel = pingChannel ?: config.pingChannel,
                pingMessage = pingMessage ?: config.pingMessage,
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
                forbiddenUsers = config.forbiddenUsers - forbiddenUsers,
                pingChannel = if (ping) null else config.pingChannel,
                pingMessage = if (ping) null else config.pingMessage,
            )
        }

        /**
         * @return true if valid.
         */
        suspend fun validate(guild: Guild, errorChannel: TextChannel): Boolean {
            val invalidIds = Flux.fromIterable(addRoles + removeRoles).flatMap { id ->
                guild.getRoleById(id).then(Mono.empty<Snowflake>()).onDiscordNotFound { Mono.just(id) }
            }.collectList().awaitSingle()

            if (invalidIds.isNotEmpty()) {
                errorChannel.sendEmbed {
                    if (invalidIds.size == 1) {
                        description(invalidIds.single().roleMention(guild.id) + " is not a valid role.")
                    } else {
                        description(invalidIds.joinToString { it.roleMention(guild.id) } + " are not valid roles.")
                    }
                }
                return false
            }

            if (ping) {
                val channel = pingChannel
                if (channel == null) {
                    errorChannel.sendEmbed {
                        description("Missing channel for ping.")
                    }
                    return false
                }
                if (guild.getChannelById(channel).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull() == null) {
                    errorChannel.sendEmbed {
                        description("${channel.channelMention()} is not a valid channel.")
                    }
                    return false
                }
                val message = pingMessage
                if (message.isNullOrBlank()) {
                    errorChannel.sendEmbed {
                        description("Missing message for ping.")
                    }
                    return false
                }
                pingTemplate.validate(message)?.let {
                    errorChannel.sendEmbed {
                        showValidation(pingTemplate, message, it)
                    }
                    return false
                }
            }
            return true
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
                part == "ping" -> {
                    config.ping = true
                    if (i + 1 > parts.lastIndex || !includeReason) {
                        continue@loop
                    }
                    val channel = findAndParseSnowflake(parts[i + 1])
                    val message = parts.subList(i + 2, parts.size).joinToString(" ")
                    config.pingChannel = channel
                    config.pingMessage = message
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