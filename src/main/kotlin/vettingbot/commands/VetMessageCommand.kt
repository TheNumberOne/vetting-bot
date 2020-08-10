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
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.http.client.ClientException
import discord4j.rest.util.Permission
import io.netty.handler.codec.http.HttpResponseStatus
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import vettingbot.command.AbstractCommand
import vettingbot.util.awaitCompletion
import vettingbot.util.nullable
import vettingbot.util.respondEmbed
import vettingbot.vetting.MessageService

private val EMOJI_REGEX = Regex("(?<unicode>.)|<?(?:(?<animated>a?):)?(?<name>[^:]*):(?<id>\\d+>?)")

private fun parseEmoji(emoji: String): ReactionEmoji? {
    val match = EMOJI_REGEX.matchEntire(emoji) ?: return null
    val unicode = match.groups["unicode"]
    if (unicode != null) {
        return ReactionEmoji.unicode(unicode.value)
    }
    val animated = match.groups["animated"] != null
    return ReactionEmoji.custom(Snowflake.of(match.groups["id"]!!.value), match.groups["name"]!!.value, animated)
}

@Component
class VetMessageCommand(
    private val messageService: MessageService
) : AbstractCommand(
    "vetmessage",
    "Create a message that users can start the vetting process by reacting to.",
    Permission.ADMINISTRATOR
) {
    @ExperimentalUnsignedTypes
    override suspend fun run(message: MessageCreateEvent, args: String) {
        val guildId = message.guildId.nullable ?: return
        if (args.isEmpty()) {
            val messages = messageService.getVettingMessagesInGuild(message.guild.awaitSingle())
            val links = messages.map {
                "https://discordapp.com/channels/${guildId.asString()}/${it.channelId.asString()}/${it.id.asString()}"
            }.toList().joinToString("\n")
            if (links.isNotEmpty()) {
                message.respondEmbed {
                    title("Vet Messages")
                    description("These messages are currently used to start the vetting process.")
                    field("Messages", links)
                }
            } else {
                message.respondEmbed {
                    title("Vet Messages")
                    description("There are not currently any messages being used to start the vetting process.")
                }
            }
            return
        }
        val parts = args.split(" ", limit = 2)
        if (parts.size < 2) {
            message.respondEmbed {
                title("Vet Message")
                description("Please specify a reaction followed by the message.")
            }
            return
        }
        val emoji = parseEmoji(parts[0]) ?: run {
            message.respondEmbed {
                title("Vet Message")
                description("${parts[0]} is not a valid emoji.")
            }
            return
        }
        val messageContent = parts[1]
        val createdMessage = message.respondEmbed {
            description(messageContent)
        }
        try {
            createdMessage.addReaction(emoji).awaitCompletion()
        } catch (e: Exception) {
            createdMessage.delete("Invalid message").awaitCompletion()
            if (e is ClientException && e.status == HttpResponseStatus.BAD_REQUEST
                && e.errorResponse.nullable?.fields?.get("code") == 10014
            ) {
                message.respondEmbed {
                    title("Vet Message")
                    description("${parts[0]} is either not a valid emoji, or this bot isn't in the guild the emoji is from.")
                }
                return
            } else {
                throw e
            }
        }
        messageService.createVettingMessage(guildId, message.message.channelId, createdMessage.id, emoji)
    }
}