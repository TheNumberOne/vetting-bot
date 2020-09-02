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

import discord4j.core.event.domain.message.ReactionAddEvent
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.stereotype.Component
import vettingbot.discord.DiscordEventListener
import vettingbot.util.awaitCompletion
import vettingbot.util.nullable

@Component
class DeleteChannelReactionListener(private val repo: DeleteMessageRepository) :
    DiscordEventListener<ReactionAddEvent> {
    override suspend fun on(event: ReactionAddEvent) {
        val deleteMessage = repo.findById(event.messageId.asLong()).awaitFirstOrNull() ?: return
        if (event.emoji.asUnicodeEmoji().nullable?.raw != deleteMessage.emoji) return
        repo.deleteById(event.messageId.asLong()).awaitFirstOrNull()
        val channel = event.channel.awaitSingle()
        channel.delete().awaitCompletion()
    }
}