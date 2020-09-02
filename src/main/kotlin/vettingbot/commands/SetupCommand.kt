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
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import vettingbot.command.AbstractCommand
import vettingbot.guild.GuildConfigService
import vettingbot.util.*
import vettingbot.vetting.VettingChannelService
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class SetupCommand(
    private val guildConfigService: GuildConfigService,
    private val vettingChannelService: VettingChannelService,
    private val categoryCommand: CategoryCommand
) :
    AbstractCommand("setup", "Sets up the bot for a guild.", Permission.ADMINISTRATOR) {
    override suspend fun run(message: MessageCreateEvent, args: String) = coroutineScope {
        val guildId = message.guildId.nullable ?: return@coroutineScope
        val guild = message.guild.awaitSingle()
        val channel = message.message.channel.awaitSingle() as TextChannel
        val client = message.message.client
        val member = message.member.nullable ?: return@coroutineScope
        val messages = client.on(MessageCreateEvent::class.java)
            .filter {
                it.guildId.nullable == guildId
                        && it.message.channelId == channel.id
                        && it.message.author.nullable?.id == member.id
            }
            .map { it.message.content }
            .timeout(Duration.ofMinutes(5))

        val cancelAll = { cancel() }

        launch {
            messages.filter { it == "exit" }.timeout(Duration.ofHours(1), Mono.empty()).awaitFirstOrNull()
            channel.sendEmbed {
                description("Stopped setup.")
            }
            cancelAll()
        }

        launch {
            try {
                doSetup(guild, member.id, channel, messages)
            } catch (t: TimeoutException) {
                message.respondEmbed {
                    description("Timed out.")
                    color(Color.RED)
                }
            }
            cancelAll()
        }
    }

    private suspend fun doSetup(
        guild: Guild,
        userId: Snowflake,
        channel: TextChannel,
        messages: Flux<String>
    ) {
        val nextMessage = messages.next()

        channel.sendEmbed {
            title("Setup")
            description("Welcome to the automatic setup of vetting bot. Type `exit` to exit setup immediately. The setup will exit if the total time passes an hour or if you take longer than 5 minutes to answer a prompt.")
        }

        // 1. vetting role
        //   a. If already exists, ask if you want to change it to a different role.
        setupVettingRole(guild, userId, channel, nextMessage)

        // 2. vetted role
        setupVettedRole(guild, userId, channel, nextMessage)

        // 3. Offer to forbid all permissions to @everyone
        checkHasLimitedPermissions(userId, channel, guild.everyoneRole.awaitSingle())

        // 4. vetting category
        //   a. If already exists, ask if you want to change the name.
        //   b. Otherwise, ask for the new name of the category.
        //   c. Configure bot category permissions.
        val categories = vettingChannelService.getVettingCategories(guild)
        if (categories.isEmpty()) {
            channel.sendEmbed {
                title("Vetting Category")
                description(
                    """
                    There is currently no category configured for vetting channels to be created under.
                    Do one of the following:
                    1. Type out the name of a category to set it as the category.
                    2. Type out the name of a category which will be created.
                    """.trimIndent()
                )
            }
            val category = nextMessage.awaitSingle()
            categoryCommand.createOrRenameCategories(guild, categories, category, channel)
        } else {
            val rename = channel.sendEmbed {
                title("Vetting Category")
                description(
                    if (categories.size == 1) {
                        "Category ${categories.first().name} is configured for vetting channels to be created under.\n"
                    } else {
                        "${categories.size} categories are configured for vetting channels to be created under.\n"
                    } + "React with ✅ below if this is fine, or \uD83D\uDEAB to rename the categories."
                )
            }.promptBoolean(userId)
            if (rename) {
                categoryCommand.createOrRenameCategories(guild, categories, nextMessage.awaitSingle(), channel)
            }
        }
        val nowCategories = vettingChannelService.getVettingCategories(guild)
        // 5. default commands and moderator roles
        //   a. Do you wish to create the custom commands vet, ban, and kick?
        //   b. Which roles and users should be able to run these vetting commands?
        //      i. configure category permissions.
        // 6. vetting message
        //   a. Which channel?
        //   b. Which emoji?
        //   c. What is the text of the message?
        //   d. Do you wish to create another?
        // 7. vetting text
        //   a. Do you wish to change the vetting text?
        // 8. enable
        channel.sendEmbed {
            title("Setup")
            description("Completed setup.")
        }
    }

    private suspend fun setupVettedRole(
        guild: Guild,
        userId: Snowflake,
        channel: TextChannel,
        nextMessage: Mono<String>
    ) {
        val previousVettedRole = guildConfigService.getVettedRole(guild.id)
        if (previousVettedRole == null) {
            channel.sendEmbed {
                title("Vetted Role")
                description(
                    """
                    The role that is assigned to members after they are vetted has not been specified.
                    Please either
                    1. Mention an existing role to use as this role.
                    or 2. Type out the name of a new role to use as this role.
                    """.trimIndent()
                )
            }
            guildConfigService.setVettedRole(guild.id, readRole(guild, channel, nextMessage).id)
        } else {
            val isFine = channel.sendEmbed {
                title("Vetted Role")
                description(
                    """
                    The role ${previousVettedRole.roleMention()} is assigned to members after they are vetted.
                    Please react to ✅ below if this is fine, or 🚫 if you want to change it.
                    """.trimIndent()
                )
            }.promptBoolean(userId)

            if (!isFine) {
                channel.sendEmbed {
                    title("Vetted Role")
                    description(
                        """
                        1. Mention an existing role to use as this role.
                        or 2. Type out the name of a new role to use as this role.
                        """.trimIndent()
                    )
                }
                guildConfigService.setVettedRole(guild.id, readRole(guild, channel, nextMessage).id)
            }
        }
        val role = guild.getRoleById(guildConfigService.getVettedRole(guild.id)!!).awaitSingle()
        val permissions = PermissionSet.of(
            Permission.SEND_MESSAGES,
            Permission.VIEW_CHANNEL,
            Permission.READ_MESSAGE_HISTORY
        ) - role.permissions
        if (permissions.isNotEmpty()) {
            val fix = channel.sendEmbed {
                description(
                    """
                    Role ${role.mention} can't see or send messages by default because of its permissions.
                    React with ✅ to fix, or 🚫 to not fix.
                    """.trimIndent()
                )
            }.promptBoolean(userId)
            if (fix) {
                role.edit {
                    it.setPermissions(PermissionSet.of(*(role.permissions + permissions).toTypedArray()))
                }.awaitCompletion()
            }
        }
    }

    private suspend fun setupVettingRole(
        guild: Guild,
        userId: Snowflake,
        channel: TextChannel,
        nextMessage: Mono<String>
    ) {
        val previousVettingRole = guildConfigService.getVettingRole(guild.id)
        if (previousVettingRole == null) {
            channel.sendEmbed {
                title("Vetting Role")
                description(
                    """
                    The role that is assigned to members while they are being vetted has not been specified.
                    Please either
                    1. Mention an existing role to use as this role.
                    or 2. Type out the name of a new role to use as this role.
                    """.trimIndent()
                )
            }
            guildConfigService.setVettingRole(guild.id, readRole(guild, channel, nextMessage).id)
        } else {
            val isFine = channel.sendEmbed {
                title("Vetting Role")
                description(
                    """
                    The role ${previousVettingRole.roleMention()} is assigned to members while they are being vetted.
                    Please react to ✅ below if this is fine, or 🚫 if you want to change it.
                    """.trimIndent()
                )
            }.promptBoolean(userId)

            logger.debug("User chose to keep vetting role or not.", isFine)

            if (!isFine) {
                channel.sendEmbed {
                    title("Vetting Role")
                    description(
                        """
                        1. Mention an existing role to use as this role.
                        or 2. Type out the name of a new role to use as this role.
                        """.trimIndent()
                    )
                }
                guildConfigService.setVettingRole(guild.id, readRole(guild, channel, nextMessage).id)
            }
        }
        val role = guild.getRoleById(guildConfigService.getVettingRole(guild.id)!!).awaitSingle()
        checkHasLimitedPermissions(userId, channel, role)
    }

    private suspend fun checkHasLimitedPermissions(
        userId: Snowflake,
        channel: TextChannel,
        role: Role
    ) {
        val permissions = role.permissions intersect PermissionSet.of(
            Permission.VIEW_CHANNEL,
        )
        if (permissions.isNotEmpty()) {
            val fix = channel.sendEmbed {
                description(
                    """
                    Role ${role.mention} can see or send messages by default.
                    React with ✅ to fix, or 🚫 to not fix.
                    """.trimIndent()
                )
            }.promptBoolean(userId)
            if (fix) {
                role.edit {
                    it.setPermissions(PermissionSet.of(*(role.permissions - permissions).toTypedArray()))
                }.awaitCompletion()
            }
        }
    }

    private suspend fun readRole(
        guild: Guild,
        channel: TextChannel,
        nextMessage: Mono<String>
    ): Role {
        val response = nextMessage.awaitSingle()
        val existingRole = findAndParseSnowflake(response)
            ?.let { guild.getRoleById(it).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull() }
        return if (existingRole == null) {
            val createdRole = guild.createRole {
                it.setName(response)
            }.awaitSingle()
            channel.sendEmbed {
                description("Created role ${createdRole.mention}")
            }
            createdRole
        } else {
            channel.sendEmbed {
                description("Using role ${existingRole.mention}")
            }
            existingRole
        }
    }
}