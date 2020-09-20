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
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Role
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.mono
import mu.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.cast
import vettingbot.banwatch.BanWatchService
import vettingbot.command.AbstractCommand
import vettingbot.commands.custom.CustomVettingCommandConfig
import vettingbot.commands.custom.CustomVettingCommandsService
import vettingbot.guild.GuildConfigService
import vettingbot.logging.GuildLoggerService
import vettingbot.mod.ModService
import vettingbot.purge.PruneService
import vettingbot.util.*
import vettingbot.vetting.MessageService
import vettingbot.vetting.VettingChannelService
import java.time.Duration
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

@Component
class SetupCommand(
    private val guildConfigService: GuildConfigService,
    private val vettingChannelService: VettingChannelService,
    private val categoryCommand: CategoryCommand,
    private val modService: ModService,
    private val customVettingCommandsService: CustomVettingCommandsService,
    private val messageService: MessageService,
    private val banWatchService: BanWatchService,
    private val pruneService: PruneService,
    private val loggerService: GuildLoggerService,
) :
    AbstractCommand("setup", "Interactively configures the bot for this server.", Permission.ADMINISTRATOR) {
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

        val jobs = mutableListOf<Job>()
        val cancelAll = { jobs.forEach { it.cancel() } }

        jobs += launch {
            messages.filter { it == "exit" }.timeout(Duration.ofHours(1), Mono.empty()).awaitFirstOrNull()
            channel.sendEmbed {
                description("Stopped setup.")
            }
            cancelAll()
        }

        jobs += launch {
            try {
                doSetup(guild, member.id, channel, messages.timeout(Duration.ofMinutes(5)).filter { it != "exit" })
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
        val self = guild.selfMember.awaitSingle()

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
        val categories = vettingChannelService.getVettingCategories(guild)
        if (categories.isEmpty()) {
            channel.sendEmbed {
                title("Vetting Category")
                description(
                    """
                    There is currently no category configured for vetting channels to be created under.
                    Do one of the following:
                    1. Type out the id of a category to set it as the category.
                    2. Type out the name of a category to set it as the category.
                    3. Type out the name of a category which will be created.
                    """.trimIndent()
                )
            }
            val category = nextMessage.awaitFirst()
            categoryCommand.createOrRenameCategories(guild, categories, category, channel)
        } else {
            val isFine = channel.sendEmbed {
                title("Vetting Category")
                description(
                    if (categories.size == 1) {
                        "Category `${categories.first().name}` is configured for vetting channels to be created under.\n"
                    } else {
                        "${categories.size} categories are configured for vetting channels to be created under.\n"
                    } + "React with ✅ below if this is fine, or \uD83D\uDEAB to rename the categories."
                )
            }.promptBoolean(userId)
            if (!isFine) {
                channel.sendEmbed {
                    title("Vetting Category")
                    description("Type out the name you wish to rename the categories to.")
                }
                categoryCommand.createOrRenameCategories(guild, categories, nextMessage.awaitFirst(), channel)
            }
        }
        // moderators
        val modRoles = modService.getModRoles(guild.id)
        if (modRoles.isEmpty()) {
            channel.sendEmbed {
                title("Moderator Roles")
                description("There are currently no moderators roles that can access the vetting channels by default. Please mention or type out the ids of roles that can access the vetting channels.")
            }
            val rolesIds = findAndParseAllSnowflakes(nextMessage.awaitFirst())
            if (rolesIds.isEmpty()) {
                channel.sendEmbed {
                    title("Moderator Roles")
                    description("No roles were specified. Continuing.")
                }
            } else {
                val roles = Flux.merge(rolesIds.map { id ->
                    guild.getRoleById(id)
                }).onDiscordNotFound {
                    mono {
                        channel.sendEmbed {
                            title("Moderator Roles")
                            description("There was an invalid role specified. Continuing.")
                        }
                        null
                    }
                }.collectList().awaitSingle()
                if (roles.size == rolesIds.size) {
                    modService.addModRoles(guild.id, rolesIds)
                    channel.sendEmbed {
                        title("Moderator Roles")
                        description("The following roles were added as moderator roles: " + rolesIds.joinToString(", ") {
                            it.roleMention(
                                guild.id
                            )
                        })
                    }
                }
            }
        }
        val commands = customVettingCommandsService.findCommandConfigsInGuild(guild.id)
        val commandRoles = commands.flatMap { it.allowedRoles }.toSet()
        val commandRolesNotModRoles =
            commandRoles - modRoles - guild.id - setOfNotNull(guildConfigService.getVettingRole(guild.id))
        if (commandRolesNotModRoles.isNotEmpty()) {
            val addAsMods = channel.sendEmbed {
                title("Moderator Roles")
                description("The following roles can run vetting commands, but cannot access vetting commands by default: "
                        + commandRolesNotModRoles.joinToString(", ") { it.roleMention(guild.id) }
                        + ".\nDo you wish for these roles to be added as moderators?")
            }.promptBoolean(userId)
            if (addAsMods) {
                modService.addModRoles(guild.id, commandRoles)
            }
        }
        val actualModRoles = modService.getModRoles(guild.id)

        // 5. default commands
        //   a. Do you wish to create the custom commands vet, ban, and kick?
        val commandNames = commands.map { it.name }
        if ("vet" !in commandNames) {
            val addVettingCommand = channel.sendEmbed {
                title("Custom Commands")
                description("Do you wish to automatically create the vetting command `v!vet` which will complete the vetting process?")
            }.promptBoolean(userId)
            if (addVettingCommand) {
                customVettingCommandsService.createNewOrSetExisting(
                    CustomVettingCommandConfig(
                        guild.id,
                        "vet",
                        addRoles = listOfNotNull(guildConfigService.getVettedRole(guild.id)),
                        removeRoles = listOfNotNull(guildConfigService.getVettingRole(guild.id)),
                        allowedRoles = actualModRoles.toList()
                    )
                )
                channel.sendEmbed {
                    title("Custom Commands")
                    description("Created command `v!vet`.")
                }
            }
        }
        if ("ban" !in commandNames) {
            val addBanCommand = channel.sendEmbed {
                title("Custom Commands")
                description("Do you wish to automatically create the vetting command `v!ban` which will ban members who fail the verification process?")
            }.promptBoolean(userId)
            if (addBanCommand) {
                customVettingCommandsService.createNewOrSetExisting(
                    CustomVettingCommandConfig(
                        guild.id,
                        "ban",
                        ban = true,
                        allowedRoles = actualModRoles.toList()
                    )
                )
                channel.sendEmbed {
                    title("Custom Commands")
                    description("Created command `v!ban`.")
                }
            }
        }
        if ("kick" !in commandNames) {
            val addKickCommand = channel.sendEmbed {
                title("Custom Commands")
                description("Do you wish to automatically create the vetting command `v!kick` which will kick members who fail the verification process?")
            }.promptBoolean(userId)
            if (addKickCommand) {
                customVettingCommandsService.createNewOrSetExisting(
                    CustomVettingCommandConfig(
                        guild.id,
                        "kick",
                        kick = true,
                        allowedRoles = actualModRoles.toList()
                    )
                )
                channel.sendEmbed {
                    title("Custom Commands")
                    description("Created command `v!kick`.")
                }
            }
        }
        val cantBeExecuted = commands.filter { it.allowedUsers.isEmpty() && it.allowedRoles.isEmpty() }
        if (cantBeExecuted.isNotEmpty()) {
            val fix = channel.sendEmbed {
                title("Custom Commands")
                description(
                    "The following custom commands cannot be executed by anyone: ${cantBeExecuted.joinToString(", ") { it.name }}\n" +
                            "Do you wish to fix this by allowing all moderators to execute them?"
                )
            }.promptBoolean(userId)
            if (fix) {
                for (command in cantBeExecuted) {
                    customVettingCommandsService.updateCommand(guild.id, command.name) {
                        it.copy(allowedRoles = it.allowedRoles + actualModRoles)
                    }
                }
                channel.sendEmbed {
                    title("Custom Commands")
                    description("Moderators can now execute the commands.")
                }
            }
        }
        // 6. vetting message
        val vettingMessages = messageService.getVettingMessagesInGuild(guild)
        if (vettingMessages.isEmpty()) {
            val createMessage = channel.sendEmbed {
                title("Welcome Message")
                description("To start the vetting process, each user needs to react to a welcome message created by this bot. There are currently no such messages configured for this server. Do you wish to create one?")
            }.promptBoolean(userId)
            if (createMessage) {
                val messageChannel =
                    promptWelcomeMessageChannel(guild, channel, userId, self, nextMessage, actualModRoles)

                while (true) {
                    val message = channel.sendEmbed {
                        description("Using channel ${messageChannel.mention}. Please react to this message with the reaction you wish to use for the vetting message.")
                    }
                    val reaction = guild.client.on(ReactionAddEvent::class.java)
                        .filter { it.messageId == message.id }
                        .timeout(Duration.ofMinutes(5))
                        .map { it.emoji }
                        .awaitFirst()
                    channel.sendEmbed {
                        description("Now, type out the contents of the message.")
                    }
                    val content =
                        nextMessage.awaitFirst() + "\n\nThis bot may record messages during the vetting process for moderation purposes."
                    val createMessagePrompt = channel.sendEmbed {
                        description("Do you wish to create the following welcome message?")
                    }
                    val example = channel.sendEmbed {
                        description(content)
                    }
                    example.addReaction(reaction).awaitCompletion()
                    if (createMessagePrompt.promptBoolean(userId)) {
                        val vetMessage = messageChannel.sendEmbed {
                            description(content)
                        }
                        vetMessage.addReaction(reaction).awaitCompletion()
                        messageService.createVettingMessage(guild.id, messageChannel.id, vetMessage.id, reaction)
                        channel.sendEmbed {
                            description("Created welcome message.")
                        }
                        break
                    }
                }
            }
        }


        // 7. vetting text
        val vettingText = guildConfigService.getVettingText(guild.id)
        val keepVettingText = channel.sendEmbed {
            title("Vetting Message")
            description("The current message sent in vetting channels after the vetting process begins is:\n\n$vettingText\n\nDo you wish to keep this?")
        }.promptBoolean(userId)
        if (!keepVettingText) {
            while (true) {
                channel.sendEmbed {
                    title("Vetting Message")
                    description("When a user begins the vetting process, a dedicated channel is created for them. Please type out the message that will be sent to members after they start vetting. {member} will be replaced with a mention to the member being vetted.")
                }
                val welcome = nextMessage.awaitFirst()
                val setVettingMessagePrompt = channel.sendEmbed {
                    title("Vetting Message")
                    description("Do you wish to set the vetting message to the following?")
                }
                channel.sendEmbed {
                    description(welcome)
                }
                if (setVettingMessagePrompt.promptBoolean(userId)) {
                    guildConfigService.setVettingText(guild.id, welcome)
                    break
                }
            }
        }

        val logger = loggerService.getLogger(guild)
        if (logger == null) {
            channel.sendEmbed {
                title("Server Logging")
                description("Please mention a channel or type out its id to send logging messages to.")
            }
            var loggingChannel: TextChannel?
            while (true) {
                loggingChannel = findAndParseSnowflake(nextMessage.awaitFirst())?.let {
                    guild.getChannelById(it).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull() as? TextChannel
                }
                if (loggingChannel == null) {
                    channel.sendEmbed {
                        description("A valid channel was not specified. Please mention a channel or type out its id to send logging messages to.")
                    }
                } else {
                    break
                }
            }
            loggerService.markLogger(loggingChannel!!)
            channel.sendEmbed {
                description("Log messages will now be sent to ${loggingChannel.mention}")
            }
        }

        // 8. enable
        val enabled = guildConfigService.isEnabled(guild.id)
        val enable = channel.sendEmbed {
            title("Enable Vetting")
            if (enabled) {
                description("Currently vetting is enabled. Do you wish to keep it enabled?")
            } else {
                description("Currently vetting is disabled. Do you wish to enable it?")
            }
        }.promptBoolean(userId)
        if (enable != enabled) {
            guildConfigService.setEnabled(guild.id, enable)
            if (enable) {
                channel.sendEmbed {
                    description("Enabled vetting.")
                }
            } else {
                channel.sendEmbed {
                    description("Disabled vetting.")
                }
            }
        }

        val pruneDays = pruneService.findSchedule(guild.id)
        val enablePruning = channel.sendEmbed {
            title("Pruning")
            if (pruneDays == null) {
                description("Currently, members who do not complete the vetting process might stay in the server indefinitely. Do you wish to automatically kick users who haven't vetted and have been offline for a while?")
            } else {
                description("Currently, members who do not complete the vetting process and aren't online are kicked after $pruneDays days. Is this fine?")
            }
        }.promptBoolean(userId)
        var promptPruningDays = enablePruning && pruneDays == null
        if (!enablePruning && pruneDays != null) {
            val prompt = channel.sendEmbed {
                description(
                    """
                    Do you wish to
                    1. stop pruning?
                    2. change the number of days that a member must be inactive before being pruned?
                """.trimIndent()
                )
            }
            val emojis = listOf(ReactionEmoji.unicode("1️⃣"), ReactionEmoji.unicode("2️⃣"))
            for (emoji in emojis) {
                prompt.addReaction(emoji).awaitCompletion()
            }
            val reaction = guild.client.on(ReactionAddEvent::class.java)
                .filter { it.messageId == prompt.id && it.userId == userId && it.emoji in emojis }
                .timeout(Duration.ofMinutes(5))
                .map { it.emoji }
                .awaitFirst()
            when (emojis.indexOf(reaction)) {
                0 -> {
                    pruneService.removeSchedule(guild.id)
                    channel.sendEmbed {
                        description("Disabled pruning.")
                    }
                }
                1 -> promptPruningDays = true
                else -> error("Should never happen")
            }
        }
        if (promptPruningDays) {
            channel.sendEmbed {
                title("Pruning")
                description("How many days must a member be offline while not being vetted before they are kicked? Please type a number between 1 and 30 inclusive.")
            }
            var days = nextMessage.awaitFirst().toIntOrNull()
            while (days == null || days !in 1..30) {
                channel.sendEmbed {
                    description("Invalid number passed. Make sure the number is between 1 and 30.")
                }
                days = nextMessage.awaitFirst().toIntOrNull()
            }
            pruneService.schedule(guild.id, days)
            channel.sendEmbed {
                description("Members who are offline for $days days and are not vetted are now automatically kicked.")
            }
        }

        //9. ban watch
        val banWatchEnabled = banWatchService.isEnabled(guild.id)
        val enableBanWatch = channel.sendEmbed {
            title("Ban Watch")
            if (banWatchEnabled) {
                description("Do you wish to keep monitoring of moderators who ban or kick too frequently enabled?")
            } else {
                description("Do you wish to enable monitoring of moderators who ban or kick too frequently? This entails removing ban, kick, and administrator privileges from anyone who bans or kicks too many people in a designated length of time.\n**WARNING: This bot cannot remove privileges if they are from roles higher than the bot.**")
            }
        }.promptBoolean(userId)
        if (enableBanWatch && !banWatchEnabled) {
            var time: Duration? = null
            while (time == null) {
                channel.sendEmbed {
                    description("Type out the length of time that kicks and bans are kept track of.")
                    displayDurationHelp()
                }
                time = parseDuration(nextMessage.awaitFirst())
                if (time == null) {
                    channel.sendEmbed { description("An invalid amount of time was specified. Try again.") }
                }
            }
            var banLimit: Int? = null
            while (banLimit == null) {
                channel.sendEmbed {
                    description("Type out the maximum number of bans anyone can perform in the previously mentioned amount of time.")
                }
                banLimit = nextMessage.awaitFirst().toIntOrNull()
                if (banLimit == null) {
                    channel.sendEmbed { description("An invalid number was specified. Try again.") }
                }
            }
            banWatchService.enableBanWatch(guild.id, time, banLimit)
            channel.sendEmbed {
                description("Enabled ban watch.")
            }
        } else if (!enableBanWatch && banWatchEnabled) {
            banWatchService.disable(guild.id)

            channel.sendEmbed {
                description("Disabled ban watch.")
            }
        }

        channel.sendEmbed {
            title("Setup")
            description("Completed setup.")
        }
    }

    private suspend fun promptWelcomeMessageChannel(
        guild: Guild,
        channel: TextChannel,
        userId: Snowflake,
        self: Member,
        nextMessage: Mono<String>,
        modRoles: Set<Snowflake>
    ): TextChannel {
        while (true) {
            channel.sendEmbed {
                title("Vetting Message")
                description("Please mention a channel. Otherwise, a new channel with the desired name will be created.")
            }
            val nameOrId = nextMessage.awaitFirst()
            val id = findAndParseSnowflake(nameOrId)
            val vettedRole = guildConfigService.getVettedRole(guild.id)
            val vettingRole = guildConfigService.getVettingRole(guild.id)
            val messageChannel = id?.let {
                guild.getChannelById(id).onDiscordNotFound { Mono.empty() }.cast<TextChannel>().awaitFirstOrNull()
            }
            if (messageChannel != null) {
                return messageChannel
            }
            val createChannel = channel.sendEmbed {
                description("Do you wish to create a channel named `$nameOrId` for the welcome message?")
            }.promptBoolean(userId)
            if (createChannel) {
                return guild.createTextChannel {
                    it.setName(nameOrId)
                    it.setPermissionOverwrites((modRoles.map { roleId ->
                        PermissionOverwrite.forRole(
                            roleId, PermissionSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.READ_MESSAGE_HISTORY
                            ), PermissionSet.of(Permission.SEND_MESSAGES)
                        )
                    } + setOfNotNull(
                        PermissionOverwrite.forMember(
                            self.id,
                            PermissionSet.all(),
                            PermissionSet.none()
                        ),
                        vettedRole?.let { id ->
                            PermissionOverwrite.forRole(
                                id,
                                PermissionSet.none(),
                                PermissionSet.all()
                            )
                        },
                        vettingRole?.let { id ->
                            PermissionOverwrite.forRole(
                                id,
                                PermissionSet.none(),
                                PermissionSet.all()
                            )
                        },
                        PermissionOverwrite.forRole(
                            guild.id,
                            PermissionSet.of(
                                Permission.READ_MESSAGE_HISTORY,
                                Permission.VIEW_CHANNEL
                            ),
                            PermissionSet.of(Permission.SEND_MESSAGES)
                        )
                    )).toSet()
                    )
                }.awaitSingle()
            }
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
                    The role ${previousVettedRole.roleMention(guild.id)} is assigned to members after they are vetted.
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
                    Role ${role.id.roleMention(role.guildId)} can't see or send messages by default because of its permissions.
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
                    The role ${previousVettingRole.roleMention(guild.id)} is assigned to members while they are being vetted.
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
                    Role ${role.id.roleMention(role.guildId)} can see or send messages by default.
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
        val response = nextMessage.awaitFirst()
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
                description("Using role ${existingRole.id.roleMention(existingRole.guildId)}")
            }
            existingRole
        }
    }
}