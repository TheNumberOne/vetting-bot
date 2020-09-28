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

package vettingbot.archival

import discord4j.common.util.Snowflake
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.core.publisher.Mono
import vettingbot.util.*

@Component
class ArchiveChannelService(
    private val trans: TransactionalOperator,
    private val channelArchiveRepository: ChannelArchiveRepository,
    private val messageArchiveRepository: MessageArchiveRepository
) {
    suspend fun startArchiving(userId: Snowflake, channel: GuildMessageChannel) {
        val timeToStart = channel.lastMessage.awaitFirstOrNull()?.id?.timestamp ?: DISCORD_EPOCH_INSTANT
        trans.executeAndAwait {
            val archive = channelArchiveRepository.findByGuildIdAndUserId(channel.guildId, userId)
                ?: ChannelArchive(channel.guildId, userId, DISCORD_EPOCH_INSTANT, timeToStart)
            val newArchive = archive.copy(recordMessagesAfter = timeToStart)
            channelArchiveRepository.save(newArchive).awaitCompletion()
        }
    }

    private suspend fun getOrCreateChannelArchive(guildId: Snowflake, userId: Snowflake): ChannelArchive {
        return trans.executeAndAwait {
            channelArchiveRepository.findByGuildIdAndUserId(guildId, userId)
                ?: channelArchiveRepository.save(
                    ChannelArchive(
                        guildId,
                        userId,
                        DISCORD_EPOCH_INSTANT,
                        DISCORD_EPOCH_INSTANT
                    )
                )
                    .awaitSingle()
        }!!
    }

    suspend fun archive(userId: Snowflake, channel: GuildMessageChannel) {
        val startTime = getOrCreateChannelArchive(channel.guildId, userId).recordMessagesAfter
        val messages = channel.getMessagesAfter(Snowflake.of(startTime)).asFlow().toList()
            .filter {
                it.type == Message.Type.DEFAULT && it.webhookId.isEmpty && it.author.nullable?.isBot == false && it.content.isNotBlank()
            }.map {
                MessageArchive(it.editedTimestamp.orElse(it.timestamp), it.author.get().id, it.content)
            }

        trans.executeAndAwait {
            val channelArchive = getOrCreateChannelArchive(channel.guildId, userId)
            val inserted = messageArchiveRepository.saveAll(messages).asFlow().toList()
            val lastMessage = messages.map { it.time }.maxOrNull() ?: channelArchive.lastMessageArchived
            val newArchive = channelArchive.copy(
                lastMessageArchived = lastMessage,
                recordMessagesAfter = maxOf(lastMessage, channelArchive.recordMessagesAfter),
                messages = channelArchive.messages + inserted
            )
            channelArchiveRepository.save(newArchive).awaitCompletion()
        }
    }

    private suspend fun retrieveArchiveFor(guildId: Snowflake, userId: Snowflake): ChannelArchive? {
        return trans.executeAndAwait {
            channelArchiveRepository.findByGuildIdAndUserId(guildId, userId)
        }
    }

    suspend fun restoreArchiveFor(userId: Snowflake, channel: TextChannel) = coroutineScope {
        val archive = retrieveArchiveFor(channel.guildId, userId) ?: return@coroutineScope
        val self = async {
            channel.client.self.awaitSingle().asMember(channel.guildId).awaitSingle()
        }
        val webhookDeferred = async {
            val name = self.await().displayName
            channel.createWebhook {
                it.setName("$name | Archival")
            }.awaitSingle()
        }
        val guild = channel.guild.awaitSingle()
        val userIds = archive.messages.map { it.author }.toSet()

        val usersDeferred = userIds.map { id ->
            id to async {
                channel.client.getUserById(id).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull()
            }
        }.toMap()
        val membersDeferred = userIds.map { id ->
            id to async {
                guild.getMemberById(id).onDiscordNotFound { Mono.empty() }.awaitFirstOrNull()
            }
        }.toMap()

        suspend fun username(userId: Snowflake) =
            membersDeferred[userId]?.await()?.displayName ?: usersDeferred[userId]?.await()?.tag ?: userId.asString()

        suspend fun avatar(userId: Snowflake) =
            usersDeferred[userId]?.await()?.avatarUrl ?: "https://cdn.discordapp.com/embed/avatars/${
                userId.asLong().toULong() % 5u
            }.png"


        val previousOverwrites = channel.permissionOverwrites
        launch {
            val newOverwrites = mutableSetOf<PermissionOverwrite>()
            previousOverwrites.mapTo(newOverwrites) { permissions ->
                when {
                    permissions.roleId.isPresent -> PermissionOverwrite.forRole(
                        permissions.roleId.get(),
                        permissions.allowed.andNot(PermissionSet.of(Permission.SEND_MESSAGES)),
                        permissions.allowed.or(PermissionSet.of(Permission.SEND_MESSAGES))
                    )
                    permissions.memberId.isPresent ->
                        PermissionOverwrite.forMember(
                            permissions.memberId.get(),
                            permissions.allowed.andNot(PermissionSet.of(Permission.SEND_MESSAGES)),
                            permissions.allowed.or(PermissionSet.of(Permission.SEND_MESSAGES))
                        )
                    else -> error("Either role id or member id should be present.")
                }
            }

            channel.edit {
                it.setPermissionOverwrites(newOverwrites)
            }
        }
        val webhook = webhookDeferred.await()

        val startMessage = channel.sendEmbed {
            description("RESTORING CHAT HISTORY.")
            color(Color.RED)
        }

        val sortedMessages = archive.messages.sortedBy { it.time }
        val squishedMessages = mutableListOf<MessageArchive>()
        if (sortedMessages.isNotEmpty()) {
            var current = sortedMessages.first()
            for (message in sortedMessages.drop(1)) {
                if (current.author == message.author && current.content.length + message.content.length + 12 <= 2048) {
                    current = current.copy(content = current.content + "\n----------\n" + message.content)
                } else {
                    squishedMessages += current
                    current = message
                }
            }
            squishedMessages += current
        }

        for (messages in squishedMessages.windowed(10, 10, true)) {
            val usernames = messages.map { username(it.author) }
            val avatars = messages.map { avatar(it.author) }
            val selfAvatar = self.await().avatarUrl
            webhook.executeAndWait {
                it.setAvatarUrl(selfAvatar)
                for ((i, message) in messages.withIndex()) {
                    it.addEmbed { embed ->
                        embed.setAuthor(usernames[i], null, avatars[i])
                        embed.setDescription(message.content)
                        embed.setTimestamp(message.time)
                    }
                }
            }.awaitCompletion()
        }

        webhook.executeAndWait {
            it.addEmbed { embed ->
                embed.setDescription("RESTORED PREVIOUS VETTING HISTORY")
            }
        }.awaitCompletion()

        launch { webhook.deleteWithToken().awaitCompletion() }
        launch { startMessage.delete().awaitCompletion() }
        launch { channel.edit { it.setPermissionOverwrites(previousOverwrites) } }
    }

    suspend fun findArchives(guildId: Snowflake, page: Pageable): Page<Snowflake> {
        return trans.executeAndAwait {
            val total = channelArchiveRepository.countByGuildId(guildId)
            val items = channelArchiveRepository.findByGuildId(guildId, page).map { it.userId }.toList()
            PageImpl(items, page, total)
        }!!
    }
}