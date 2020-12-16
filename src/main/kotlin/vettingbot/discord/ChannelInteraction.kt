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

package vettingbot.discord

import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withTimeoutOrNull
import vettingbot.util.awaitCompletion
import vettingbot.util.nullable
import vettingbot.util.sendEmbed
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

interface ChannelInteraction : CoroutineScope {
    suspend fun send(message: String, title: String? = null)
    suspend fun read(): String
    suspend fun <T> promptReactions(message: String, reactions: Map<ReactionEmoji, T>, title: String? = null): T
}

sealed class ChannelInteractionCancellationException : CancellationException()
class ExitMessageCancellationException : ChannelInteractionCancellationException()
class MessageTimedOutCancellationException : ChannelInteractionCancellationException()
class ReactionTimedOutCancellationException : ChannelInteractionCancellationException()

@OptIn(ExperimentalTime::class)
fun interactionFor(
    channel: MessageChannel,
    user: User,
    messageTimeout: Duration = 5.minutes,
    reactionTimeout: Duration = messageTimeout,
    exitMessage: String = "exit"
): ChannelInteraction {
    val messages = channel.client.on(MessageCreateEvent::class.java).asFlow()
        .filter {
            it.message.channelId == channel.id && it.member.nullable?.id == user.id
        }

    val channelReactions = channel.client.on(ReactionAddEvent::class.java).asFlow()
        .filter {
            it.channelId == channel.id && it.userId == user.id
        }

    return object : ChannelInteraction, CoroutineScope {
        override suspend fun send(message: String, title: String?) {
            channel.sendEmbed {
                description(message)
                title?.let { title(it) }
            }
        }

        override suspend fun read(): String {
            return withTimeoutOrNull(messageTimeout) {
                messages.firstOrNull()?.message?.content?.takeUnless { it == exitMessage }
                    ?: stopInteraction(ExitMessageCancellationException())
            } ?: stopInteraction(MessageTimedOutCancellationException())
        }

        private fun stopInteraction(e: CancellationException = CancellationException()): Nothing {
            job.cancel(e)
            throw e
        }

        override suspend fun <T> promptReactions(
            message: String,
            reactions: Map<ReactionEmoji, T>,
            title: String?
        ): T {
            val discordMessage = channel.sendEmbed {
                description(message)
                title?.let { title(it) }
            }
            for (reaction in reactions.keys) {
                discordMessage.addReaction(reaction).awaitCompletion()
            }
            val messageReactions = channelReactions.filter {
                it.messageId == discordMessage.id && it.emoji in reactions
            }
            return withTimeoutOrNull(reactionTimeout) {
                reactions.getValue(messageReactions.first().emoji)
            } ?: stopInteraction(ReactionTimedOutCancellationException())
        }

        private val job = SupervisorJob()

        override val coroutineContext: CoroutineContext get() = job
    }
}

suspend fun <T> ChannelInteraction.promptUnicodeReactions(
    message: String,
    reactions: Map<String, T>,
    title: String? = null
): T {
    return promptReactions(message, reactions.mapKeys { ReactionEmoji.unicode(it.key) }, title)
}

suspend fun ChannelInteraction.promptBoolean(message: String, title: String? = null): Boolean {
    return promptUnicodeReactions(message, mapOf("✅" to true, "\uD83D\uDEAB" to false), title)
}