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

package vettingbot.vetting

import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.cast
import reactor.kotlin.core.publisher.whenComplete
import vettingbot.util.awaitCompletion
import vettingbot.util.nullable
import vettingbot.util.onDiscordNotFound
import vettingbot.util.toSnowflake

@Component
class MessageService(
    private val messageConfigRepository: MessageConfigRepository,
    private val transaction: TransactionalOperator,
    private val vettingService: VettingService
) {
    /**
     * Creates a message that will be used to jump start the vetting process.
     */
    suspend fun createVettingMessage(
        guildId: Snowflake,
        channelId: Snowflake,
        messageId: Snowflake,
        emoji: ReactionEmoji
    ) {
        transaction.executeAndAwait {
            val id = emoji.asCustomEmoji().nullable?.id
            val name = emoji.asUnicodeEmoji().nullable?.raw
            messageConfigRepository.save(MessageConfig(messageId.asLong(), guildId, channelId, id, name))
                .awaitCompletion()
        }
    }

    suspend fun getVettingMessage(messageId: Snowflake): MessageConfig? {
        return transaction.executeAndAwait { messageConfigRepository.findById(messageId.asLong()).awaitFirstOrNull() }
    }

    suspend fun scanVettingMessagesIn(guild: Guild) = coroutineScope {
        val messages = transaction.executeAndAwait { messageConfigRepository.findAllByGuildId(guild.id).toList() }!!
        for (message in messages) {
            launch { scanVettingMessage(guild, message) }
        }
    }

    suspend fun getVettingMessagesInGuild(guild: Guild): List<Message> {
        val messages = transaction.executeAndAwait { messageConfigRepository.findAllByGuildId(guild.id).toList() }!!
            .map { message ->
                guild.client.getMessageById(message.channelId, message.id.toSnowflake()).onDiscordNotFound {
                    transaction.transactional(messageConfigRepository.delete(message)).cast()
                }
            }
        return Flux.merge(messages).asFlow().toList()
    }

    suspend fun scanVettingMessage(guild: Guild, message: MessageConfig) {
        val discordMessage =
            guild.client.getMessageById(message.channelId, message.id.toSnowflake()).onDiscordNotFound {
                transaction.transactional(messageConfigRepository.delete(message)).cast()
            }.awaitFirstOrNull() ?: return

        discordMessage.reactions.filter { !message.isVettingEmoji(it.emoji) }.map {
            discordMessage.removeReactions(it.emoji)
        }.whenComplete().subscribe()

        val vettingReaction = discordMessage.reactions.firstOrNull {
            message.isVettingEmoji(it.emoji)
        } ?: return

        if (!vettingReaction.selfReacted() || vettingReaction.count > 1) {
            discordMessage.getReactors(vettingReaction.emoji).asFlow().collect { user ->
                if (user.id != guild.client.selfId) {
                    discordMessage.removeReaction(vettingReaction.emoji, user.id).subscribe()
                    coroutineScope {
                        launch {
                            vettingService.beginVetting(user.asMember(guild.id).awaitSingle())
                        }
                    }
                }
            }
        }
    }

    fun getVettingMessagesInGuild(guildId: Snowflake): Flow<MessageConfig> {
        return messageConfigRepository.findAllByGuildId(guildId)
    }
}